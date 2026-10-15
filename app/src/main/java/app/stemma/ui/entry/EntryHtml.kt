package app.stemma.ui.entry

import app.stemma.data.EntryContent
import app.stemma.data.Titles
import org.jsoup.nodes.Entities

/** Builds the HTML document that the entry WebView displays. */
object EntryHtml {
    /** Local assets are served from this origin by WebViewAssetLoader. */
    const val ASSET_BASE = "https://appassets.androidplatform.net/assets/entry/"

    private const val VISIBLE_CHIPS = 5
    private val languageBlock = Regex("<details class=\"lang\" id=\"(lang-\\d+)\">")

    fun render(
        content: EntryContent,
        themeClass: String,
        darkTheme: Boolean,
        preferredLanguage: String,
        expandAll: Boolean,
        fragment: String?,
        /** Scroll position to restore, in screen pixels as reported by WebView.getScrollY(). */
        restoreScrollY: Int,
    ): String {
        val languages = content.languages
        val openIds = when {
            languages.isEmpty() -> emptyList()
            expandAll -> languages.map { it.blockId }
            else -> {
                val fromFragment = fragment?.let { f -> languages.firstOrNull { it.anchor == f } }
                listOfNotNull(
                    fromFragment ?: languages.firstOrNull { it.name.equals(preferredLanguage, ignoreCase = true) }
                        ?: languages.first(),
                ).map { it.blockId }
            }
        }
        // One pass over the body, however many languages are opened (some pages have hundreds).
        val open = openIds.toHashSet()
        val body = languageBlock.replace(content.bodyHtml) { match ->
            if (match.groupValues[1] in open) match.value.dropLast(1) + " open>" else match.value
        }

        val chips = if (languages.size < 2) "" else buildString {
            append("<nav class=\"wh-langs\" aria-label=\"Languages\">")
            val shown = if (languages.size <= VISIBLE_CHIPS + 1) languages else languages.take(VISIBLE_CHIPS)
            for (lang in shown) {
                append("<a class=\"wh-chip\" href=\"#\" data-jump=\"").append(attr(lang.anchor)).append("\">")
                append(text(lang.name)).append("</a>")
            }
            if (shown.size < languages.size) {
                append("<a class=\"wh-chip wh-more\" href=\"#\">+").append(languages.size - shown.size).append(" more</a>")
            }
            append("</nav>")
        }

        val pageUrl = Titles.pageUrl(content.title)
        return buildString {
            append("<!DOCTYPE html><html lang=\"en\" class=\"").append(themeClass).append("\"><head>")
            append("<meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            append("<meta name=\"color-scheme\" content=\"").append(if (darkTheme) "dark" else "light").append("\">")
            append("<title>").append(text(content.displayTitle)).append("</title>")
            append("<link rel=\"stylesheet\" href=\"").append(ASSET_BASE).append("entry.css\">")
            append("</head><body")
            fragment?.let { append(" data-fragment=\"").append(attr(it)).append("\"") }
            if (restoreScrollY > 0) append(" data-scroll=\"").append(restoreScrollY).append("\"")
            append(">")
            append("<header class=\"wh-header\"><h1 class=\"wh-title\">").append(text(content.displayTitle)).append("</h1>")
            append(chips).append("</header>")
            append("<main id=\"content\" class=\"mw-parser-output\">").append(body).append("</main>")
            append("<footer class=\"wh-footer\">")
            append("Text from the Wiktionary entry <a class=\"wh-external\" href=\"").append(attr(pageUrl)).append("\">")
            append(text(content.displayTitle)).append("</a>, by its <a href=\"")
            append(attr(Titles.historyUrl(content.title))).append("\">contributors</a>, ")
            append("available under the <a href=\"https://creativecommons.org/licenses/by-sa/4.0/\">")
            append("Creative Commons Attribution-ShareAlike 4.0 License</a>. Additional terms may apply.")
            append("</footer>")
            append("<script src=\"").append(ASSET_BASE).append("entry.js\"></script>")
            append("</body></html>")
        }
    }

    private fun text(s: String): String = Entities.escape(s)
    private fun attr(s: String): String = Entities.escape(s).replace("\"", "&quot;")
}
