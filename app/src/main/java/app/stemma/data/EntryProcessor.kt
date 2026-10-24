package app.stemma.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Turns the "mobile-html" page that Wiktionary's REST API returns into compact HTML for the
 * entry screen. The Wikipedia app renders the same format with Wikimedia's own scripts; this app
 * instead rewrites it once, here, so that the page needs only a small local script and stylesheet:
 *
 * - every language section becomes a collapsible `<details class="lang">` block,
 * - translation boxes, inflection tables and long term lists become collapsible blocks,
 * - quotations under each definition are collapsed behind a "Quotations" toggle,
 * - audio players become a single play button,
 * - editing controls, scripts and large metadata attributes are removed,
 * - anything that could run script in the WebView (event-handler attributes, `javascript:` and
 *   similar URLs, embedded frames, plugins and forms) is removed, since the page has a JS bridge.
 */
object EntryProcessor {

    private val strippedAttributes = setOf(
        "about", "typeof", "onclick", "resource", "data-ety-tree-json", "data-etytree-height",
        "data-etytree-width", "data-file-width", "data-file-height", "data-file-type",
        "data-durationhint", "data-mw-section-id", "data-mw-wikitext", "data-id", "data-action",
    )

    /** Attributes that hold a URL the WebView might load or navigate to. */
    private val urlAttributes = setOf(
        "href", "src", "data-src", "srcset", "action", "formaction", "poster", "background",
        "xlink:href", "cite", "longdesc",
    )

    /** URL schemes that run script or embed arbitrary content when loaded or followed. */
    private val unsafeSchemes = listOf("javascript:", "data:", "vbscript:")

    fun process(html: String, requestedTitle: String, preferredLanguage: String, now: Long = System.currentTimeMillis()): EntryContent {
        val doc = Jsoup.parse(html, Titles.SITE + "/wiki/")
        doc.outputSettings().prettyPrint(false)

        val canonicalTitle = canonicalTitle(doc) ?: Titles.normalize(requestedTitle)
        val displayTitle = doc.selectFirst("header h1")?.text()?.takeIf { it.isNotBlank() }
            ?: doc.title().takeIf { it.isNotBlank() }
            ?: canonicalTitle
        val revision = Regex("/revision/(\\d+)").find(doc.selectFirst("html")?.attr("about").orEmpty())
            ?.groupValues?.get(1)?.toLongOrNull() ?: 0L

        val root = doc.getElementById("pcs") ?: doc.body()
        removeChrome(root)
        restoreLazyImages(root)
        fixReferenceBackLinks(root)
        stripAttributes(root)
        unwrapPcsTables(root)
        convertNavFrames(root)
        convertListSwitchers(root)
        collapseQuotations(root)
        replaceAudio(root)
        tidyMedia(root)
        wrapTables(root)
        removeActiveContent(root)

        val languages = buildLanguageSections(root)
        val summary = summarize(root, languages, preferredLanguage)

        return EntryContent(
            title = canonicalTitle,
            displayTitle = displayTitle,
            revision = revision,
            bodyHtml = root.html(),
            languages = languages,
            summary = summary,
            fetchedAt = now,
        )
    }

    private fun canonicalTitle(doc: Document): String? {
        val href = doc.selectFirst("link[rel=dc:isVersionOf]")?.attr("href") ?: return null
        return Titles.titleFromLocation(href)
    }

    private fun removeChrome(root: Element) {
        root.select(
            "script, iframe, frame, frameset, object, embed, applet, form, base, animate, set, animateMotion, animateTransform, discard, " +
                "header, link, meta, hr.pcs-fold-hr, .pcs-edit-section-link-container, " +
                ".pcs-section-control, .mw-empty-elt, .sister-wikidata, .audiometa, " +
                ".pcs-collapse-table-collapsed-container, .pcs-collapse-table-collapsed-bottom",
        ).remove()
        // PCS wraps each heading in a header div and each section body in a hidden div.
        root.select("div.pcs-edit-section-header").forEach { it.unwrap() }
        root.select("div.pcs-section-hidden").forEach { it.unwrap() }
        root.select("section").forEach { it.removeAttr("style") }
        root.select("h1, h2, h3, h4, h5, h6").forEach { it.removeClass("pcs-edit-section-title") }
    }

    private fun stripAttributes(root: Element) {
        for (el in root.getAllElements()) {
            val names = el.attributes().asList().map { it.key }
            for (name in names) {
                if (name in strippedAttributes || name.startsWith("data-mw")) el.removeAttr(name)
            }
            // Link tooltips repeat the page name and make up a large share of the page.
            if (el.tagName() == "a") el.removeAttr("title")
        }
    }

    /**
     * Last pass over the finished tree: drops every event-handler attribute (`on…`, in any case)
     * and every URL attribute whose scheme could run script (`javascript:`, `data:`, `vbscript:`).
     * Elements that embed other content are already gone (see [removeChrome]); they are removed
     * again here in case a later step produced one.
     */
    private fun removeActiveContent(root: Element) {
        root.select("script, iframe, frame, frameset, object, embed, applet, form, base, animate, set, animateMotion, animateTransform, discard").remove()
        for (el in root.getAllElements()) {
            val names = el.attributes().asList().map { it.key }
            for (name in names) {
                val lower = name.lowercase()
                if (lower.startsWith("on")) {
                    el.removeAttr(name)
                } else if (lower == "srcset") {
                    // A comma-separated list of candidates; drop it if any candidate is unsafe.
                    if (el.attr(name).split(',').any { isUnsafeUrl(it) }) el.removeAttr(name)
                } else if (lower in urlAttributes && isUnsafeUrl(el.attr(name))) {
                    el.removeAttr(name)
                }
            }
        }
    }

    /** True if the URL uses a script-capable scheme; browsers ignore whitespace and control characters in it. */
    internal fun isUnsafeUrl(value: String): Boolean {
        val compact = value.filterNot { it <= ' ' }.lowercase()
        return unsafeSchemes.any { compact.startsWith(it) }
    }

    /**
     * PCS replaces images with placeholder spans that its own script swaps back on scroll.
     * Turn them back into ordinary lazily-loaded images.
     */
    private fun restoreLazyImages(root: Element) {
        for (placeholder in root.select("span.pcs-lazy-load-placeholder")) {
            val src = placeholder.absUrl("data-src").ifEmpty { placeholder.attr("data-src") }
            if (src.isEmpty()) {
                placeholder.remove()
                continue
            }
            val img = Element("img").attr("src", src).attr("loading", "lazy").attr("decoding", "async")
            placeholder.attr("data-srcset").takeIf { it.isNotBlank() }?.let { img.attr("srcset", it) }
            placeholder.attr("data-width").takeIf { it.isNotBlank() }?.let { img.attr("width", it) }
            placeholder.attr("data-height").takeIf { it.isNotBlank() }?.let { img.attr("height", it) }
            img.attr("alt", placeholder.attr("data-alt"))
            img.addClass("mw-file-element")
            placeholder.replaceWith(img)
        }
        root.select(".pcs-widen-image-override, .pcs-widen-image-ancestor").forEach {
            it.removeClass("pcs-widen-image-override").removeClass("pcs-widen-image-ancestor")
            if (it.attr("style").contains("width")) it.removeAttr("style")
        }
    }

    /** Footnote numbers in the reference list should jump back to where the footnote is cited. */
    private fun fixReferenceBackLinks(root: Element) {
        for (link in root.select("a.pcs-ref-back-link")) {
            val target = Regex("#([^\"&]+)").find(link.attr("pcs-back-links"))?.groupValues?.get(1)
            if (target != null) link.attr("href", "#$target") else link.removeAttr("href")
            link.removeAttr("pcs-back-links")
        }
    }

    private fun unwrapPcsTables(root: Element) {
        root.select("div.pcs-collapse-table-content").forEach {
            it.removeAttr("style")
            it.unwrap()
        }
        root.select("div.pcs-collapse-table-container").forEach { it.unwrap() }
        root.select("table.pcs-collapse-table").forEach { it.removeClass("pcs-collapse-table") }
    }

    /** Wiktionary's `NavFrame` boxes (translations, inflection tables) become `<details>` blocks. */
    private fun convertNavFrames(root: Element) {
        // Innermost first, so nested frames are converted before their parents move them.
        for (frame in root.select("div.NavFrame").reversed()) {
            val head = frame.children().firstOrNull { it.hasClass("NavHead") }
            val content = frame.children().firstOrNull { it.hasClass("NavContent") }
            if (head == null || content == null) {
                frame.addClass("wh-box")
                continue
            }
            val details = Element("details").addClass("wh-nav")
            frame.classNames().filter { it != "NavFrame" }.forEach { details.addClass(it) }
            val summary = Element("summary").addClass("wh-nav-head")
            summary.appendChildren(head.takeChildren())
            val body = Element("div").addClass("wh-nav-body")
            body.appendChildren(content.takeChildren())
            details.appendChild(summary).appendChild(body)
            frame.replaceWith(details)
        }
    }

    /** Long term lists ("Derived terms", "Hyponyms", …) become collapsed `<details>` blocks. */
    private fun convertListSwitchers(root: Element) {
        for (wrapper in root.select("div.list-switcher-wrapper").reversed()) {
            val header = wrapper.selectFirst(".list-switcher-header")
            val list = wrapper.selectFirst(".list-switcher") ?: continue
            val count = list.select("li").size
            val details = Element("details").addClass("wh-list")
            val label = header?.text()?.takeIf { it.isNotBlank() } ?: "Terms"
            details.appendElement("summary").addClass("wh-list-head")
                .appendText(label)
                .appendElement("span").addClass("wh-count").text(count.toString())
            val body = details.appendElement("div").addClass("wh-list-body")
            body.appendChildren(list.takeChildren())
            wrapper.replaceWith(details)
        }
    }

    /** Quotation lists under a definition are hidden behind a "Quotations" toggle. */
    private fun collapseQuotations(root: Element) {
        for (ul in root.select("ol > li > ul")) {
            val items = ul.children().filter { it.tagName() == "li" }
            if (items.isEmpty()) continue
            val quoteItems = items.count { it.selectFirst(".citation-whole, .cited-source, .h-quotation") != null }
            if (quoteItems == 0) continue
            val details = Element("details").addClass("wh-quotes")
            details.appendElement("summary").text(if (quoteItems == 1) "Quotation" else "Quotations ($quoteItems)")
            ul.replaceWith(details)
            details.appendChild(ul)
        }
    }

    /** Replaces each `<audio>` player with a compact play button handled by entry.js. */
    private fun replaceAudio(root: Element) {
        for (audio in root.select("audio")) {
            val sources = audio.select("source")
            val chosen = sources.firstOrNull { it.attr("type").contains("mpeg") }
                ?: sources.firstOrNull { it.attr("type").contains("ogg") }
                ?: sources.firstOrNull()
            val src = chosen?.absUrl("src").orEmpty()
            if (src.isEmpty()) {
                audio.remove()
                continue
            }
            val button = Element("button")
                .attr("type", "button")
                .addClass("wh-audio")
                .attr("data-src", src)
                .attr("aria-label", "Play pronunciation")
            button.appendElement("span").addClass("wh-audio-icon").attr("aria-hidden", "true")
            button.appendElement("span").addClass("wh-audio-label").text("Play")
            // Climb out of TimedMediaHandler's sizing spans so the button is not squeezed.
            var target: Element = audio
            while (target.parent()?.tagName() == "span" && target.parent()!!.childrenSize() == 1) {
                target = target.parent()!!
            }
            target.replaceWith(button)
        }
    }

    private fun tidyMedia(root: Element) {
        for (img in root.select("img")) {
            img.attr("loading", "lazy")
            img.attr("src", img.absUrl("src"))
            if (img.hasAttr("srcset")) {
                img.attr(
                    "srcset",
                    img.attr("srcset").split(",").joinToString(", ") { part ->
                        val p = part.trim()
                        if (p.startsWith("//")) "https:$p" else p
                    },
                )
            }
        }
        // The Wikidata and Wikipedia sister-project boxes float right on desktop; keep them inline.
        root.select(".floatright").forEach { it.removeClass("floatright").addClass("wh-sister") }
    }

    private fun wrapTables(root: Element) {
        for (table in root.select("table")) {
            if (table.hasClass("audiotable")) continue
            if (table.parents().any { it.tagName() == "table" || it.hasClass("wh-scroll") }) continue
            table.wrap("<div class=\"wh-scroll\"></div>")
        }
    }

    /** Wraps each top-level language section in a collapsible block and returns the outline. */
    private fun buildLanguageSections(root: Element): List<LanguageSection> {
        val result = mutableListOf<LanguageSection>()
        val sections = root.children().filter { it.tagName() == "section" }
        for (section in sections) {
            val h2 = section.children().firstOrNull { it.tagName() == "h2" }
            if (h2 == null) {
                // Section 0: hatnotes such as "See also: Dog, DOG". Keep it as a plain block.
                section.tagName("div").addClass("wh-preamble")
                if (section.text().isBlank() && section.select("img").isEmpty()) section.remove()
                continue
            }
            val index = result.size
            val blockId = "lang-$index"
            val name = h2.text().trim()
            val anchor = h2.id().ifEmpty { Titles.toDbKey(name) }.also { h2.id(it) }
            val headings = section.select("h3, h4, h5, h6")
                .filter { it.id().isNotEmpty() && it.parents().none { p -> p.tagName() == "summary" } }
                .map { SubHeading(it.text().trim(), it.id(), it.tagName().substring(1).toInt()) }

            val details = Element("details").addClass("lang").id(blockId)
            val summary = details.appendElement("summary").addClass("lang-head")
            h2.remove()
            summary.appendChild(h2)
            val body = details.appendElement("div").addClass("lang-body")
            body.appendChildren(section.takeChildren())
            section.replaceWith(details)
            result += LanguageSection(name = name, anchor = anchor, blockId = blockId, headings = headings)
        }
        return result
    }

    /** Picks the first definition of the preferred language (or of the first language on the page). */
    private fun summarize(root: Element, languages: List<LanguageSection>, preferredLanguage: String): WordSummary {
        if (languages.isEmpty()) return WordSummary()
        val section = languages.firstOrNull { it.name.equals(preferredLanguage, ignoreCase = true) } ?: languages.first()
        val body = root.getElementById(section.blockId)?.selectFirst("div.lang-body") ?: return WordSummary(section.name)

        var partOfSpeech = ""
        for (el in body.getAllElements()) {
            if (el.tagName() in setOf("h3", "h4", "h5", "h6")) partOfSpeech = el.text().trim()
            if (el.tagName() == "ol" && el.parents().none { it.tagName() == "li" || it.tagName() == "details" && it != body.parent() }) {
                val first = el.children().firstOrNull { it.tagName() == "li" && ownText(it).isNotBlank() } ?: continue
                var text = ownText(first)
                if (text.endsWith(":")) {
                    val sub = first.select("ol > li").firstOrNull { ownText(it).isNotBlank() }
                    if (sub != null) text = text.removeSuffix(":").trim() + ": " + ownText(sub)
                }
                return WordSummary(section.name, partOfSpeech.replace(Regex("\\s+\\d+$"), ""), text.take(280))
            }
        }
        return WordSummary(section.name)
    }

    /** Text of a list item without its nested lists, examples and quotations. */
    private fun ownText(li: Element): String {
        val copy = li.clone()
        copy.select("ol, ul, dl, details, style, .wh-quotes, sup.reference").remove()
        return copy.text().replace(Regex("\\s+"), " ").trim()
    }

    /** Detaches and returns this element's child nodes, so they can be moved elsewhere. */
    private fun Element.takeChildren() = childNodes().toList().onEach { it.remove() }
}
