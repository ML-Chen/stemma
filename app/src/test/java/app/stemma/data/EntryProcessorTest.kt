package app.stemma.data

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryProcessorTest {
    private val html = Samples.read("mobilehtml_dog.html")
    private val entry = EntryProcessor.process(html, "dog", preferredLanguage = "English", now = 0L)
    private val body = Jsoup.parseBodyFragment(entry.bodyHtml)

    @Test
    fun readsTitleAndRevision() {
        assertEquals("dog", entry.title)
        assertEquals("dog", entry.displayTitle)
        assertEquals(93504505L, entry.revision)
    }

    @Test
    fun buildsOneCollapsibleBlockPerLanguage() {
        assertTrue("expected many languages, got ${entry.languages.size}", entry.languages.size > 15)
        assertEquals("English", entry.languages.first().name)
        assertEquals("English", entry.languages.first().anchor)
        val blocks = body.select("details.lang")
        assertEquals(entry.languages.size, blocks.size)
        entry.languages.forEachIndexed { i, lang ->
            val block = body.getElementById(lang.blockId)!!
            assertEquals("details", block.tagName())
            assertEquals(lang.name, block.selectFirst("> summary > h2")!!.text())
            assertEquals("lang-$i", lang.blockId)
        }
        // No language block is open until EntryHtml decides which one to open.
        assertTrue(blocks.none { it.hasAttr("open") })
    }

    @Test
    fun collectsSubheadingsForTheContentsSheet() {
        val english = entry.languages.first()
        val titles = english.headings.map { it.title }
        assertTrue(titles.toString(), "Pronunciation" in titles)
        assertTrue(titles.toString(), "Etymology 1" in titles)
        assertTrue(titles.toString(), "Noun" in titles)
        assertTrue(english.headings.all { body.getElementById(it.anchor) != null })
    }

    @Test
    fun summarisesTheFirstEnglishDefinition() {
        assertEquals("English", entry.summary.language)
        assertEquals("Noun", entry.summary.partOfSpeech)
        assertTrue(entry.summary.definition, entry.summary.definition.startsWith("A mammal of the family Canidae"))
        assertFalse(entry.summary.definition, entry.summary.definition.contains("barked"))
    }

    @Test
    fun summaryFollowsThePreferredLanguage() {
        val french = EntryProcessor.process(html, "dog", preferredLanguage = "Polish", now = 0L)
        assertEquals("Polish", french.summary.language)
        assertTrue(french.summary.definition.isNotBlank())
    }

    @Test
    fun removesScriptsEditLinksAndMetadata() {
        val out = entry.bodyHtml
        assertFalse(out.contains("<script"))
        assertFalse(out.contains("data-mw="))
        assertFalse(out.contains("pcs-edit-section"))
        assertFalse(out.contains("onclick"))
        assertFalse(out.contains("display: none"))
        assertFalse(out.contains("pcs-lazy-load-placeholder"))
        assertFalse(out.contains("pcs-back-links"))
        assertTrue("output should be smaller than input", out.length < html.length * 0.8)
    }

    @Test
    fun replacesAudioPlayersWithPlayButtons() {
        assertTrue(body.select("audio").isEmpty())
        val buttons = body.select("button.wh-audio")
        assertTrue(buttons.isNotEmpty())
        for (button in buttons) {
            val src = button.attr("data-src")
            assertTrue(src, src.startsWith("https://upload.wikimedia.org/"))
        }
        assertTrue(buttons.first()!!.attr("data-src").endsWith(".mp3"))
    }

    @Test
    fun convertsNavFramesAndQuotations() {
        assertTrue(body.select("div.NavFrame").none { it.selectFirst(".NavContent") != null })
        assertTrue(body.select("details.wh-nav").isNotEmpty())
        val quotes = body.select("details.wh-quotes")
        assertTrue(quotes.isNotEmpty())
        assertTrue(quotes.first()!!.selectFirst("summary")!!.text().startsWith("Quotation"))
        assertTrue(body.select("details.wh-list").isNotEmpty())
    }

    @Test
    fun wrapsTablesForHorizontalScrolling() {
        val tables = body.select("table:not(.audiotable)").filter { t -> t.parents().none { it.tagName() == "table" } }
        assertTrue(tables.isNotEmpty())
        assertTrue(tables.all { it.parent()!!.hasClass("wh-scroll") })
    }

    @Test
    fun restoresLazyLoadedImages() {
        val images = body.select("img[src*=YellowLabradorLooking]")
        assertEquals(1, images.size)
        assertEquals("500", images.first()!!.attr("width"))
    }

    @Test
    fun referenceBackLinksPointAtTheCitation() {
        val back = body.select("a.pcs-ref-back-link")
        assertTrue(back.isNotEmpty())
        for (link in back) {
            val id = link.attr("href").removePrefix("#")
            assertTrue(link.outerHtml(), body.getElementById(id) != null)
        }
    }

    @Test
    fun makesImageUrlsAbsolute() {
        for (img in body.select("img")) {
            assertTrue(img.attr("src"), img.attr("src").startsWith("https://"))
            assertEquals("lazy", img.attr("loading"))
        }
    }

    @Test
    fun processesOtherEntries() {
        for (word in listOf("serendipity", "fabada")) {
            val e = EntryProcessor.process(Samples.read("mobilehtml_$word.html"), word, "English", 0L)
            assertEquals(word, e.title)
            assertEquals("English", e.languages.first().name)
            assertTrue(e.summary.definition, e.summary.definition.isNotBlank())
        }
    }

    @Test
    fun removesScriptCapableContent() {
        val page = """
            <html><body><div id="pcs"><section data-mw-section-id="1">
            <h2 id="English">English</h2>
            <h3 id="Noun">Noun</h3>
            <ol><li>A test word.
              <img src="https://upload.wikimedia.org/a.png" onerror="alert(1)" OnLoad="alert(2)">
              <a href="javascript:alert(3)" onmouseover="alert(4)">bad link</a>
              <a href=" JaVaScRiPt:alert(5)">spaced link</a>
              <a href="vbscript:msgbox(6)">vb link</a>
              <a href="data:text/html,hi">data link</a>
              <a href="./other">good link</a>
              <img src="https://upload.wikimedia.org/b.png" srcset="https://upload.wikimedia.org/b2.png 2x, data:image/png;base64,AA 3x">
              <iframe src="https://example.com/"></iframe>
              <object data="x.swf"></object><embed src="x.swf">
              <form action="https://example.com/"><input name="q"></form>
            </li></ol>
            </section></div></body></html>
        """.trimIndent()
        val processed = Jsoup.parseBodyFragment(EntryProcessor.process(page, "test", "English", 0L).bodyHtml)

        val attributes = processed.getAllElements().flatMap { el -> el.attributes().asList().map { it.key to it.value } }
        assertTrue(attributes.toString(), attributes.none { it.first.lowercase().startsWith("on") })
        assertTrue(processed.select("iframe, object, embed, form, input").isEmpty())
        for (link in processed.select("a")) {
            val href = link.attr("href").trim().lowercase()
            assertFalse(link.outerHtml(), href.startsWith("javascript:") || href.startsWith("vbscript:") || href.startsWith("data:"))
        }
        assertEquals("good link", processed.select("a[href]").single().text())
        // The images and their ordinary attributes are kept; only the handlers and the unsafe srcset go.
        assertEquals(2, processed.select("img[src^=https://upload.wikimedia.org/]").size)
        assertTrue(processed.select("img[srcset]").isEmpty())
        assertTrue(EntryProcessor.isUnsafeUrl("\tjava\nscript:alert(1)"))
        assertFalse(EntryProcessor.isUnsafeUrl("https://en.wiktionary.org/wiki/dog"))
    }
}
