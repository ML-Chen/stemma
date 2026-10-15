package app.stemma.ui.entry

import app.stemma.data.EntryProcessor
import app.stemma.data.Samples
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntryHtmlTest {
    private val entry = EntryProcessor.process(Samples.read("mobilehtml_dog.html"), "dog", "English", 0L)

    private fun render(preferred: String = "English", expandAll: Boolean = false, fragment: String? = null) =
        Jsoup.parse(EntryHtml.render(entry, "wh-dark", true, preferred, expandAll, fragment, 0))

    @Test
    fun opensOnlyThePreferredLanguage() {
        val doc = render("Polish")
        val open = doc.select("details.lang[open]")
        assertEquals(1, open.size)
        assertEquals("Polish", open.first()!!.selectFirst("h2")!!.text())
    }

    @Test
    fun opensTheFirstLanguageWhenThePreferredOneIsMissing() {
        val open = render("Klingon").select("details.lang[open]")
        assertEquals(listOf("English"), open.map { it.selectFirst("h2")!!.text() })
    }

    @Test
    fun opensTheLanguageNamedInTheFragment() {
        val doc = render("English", fragment = "Afrikaans")
        assertEquals(listOf("Afrikaans"), doc.select("details.lang[open]").map { it.selectFirst("h2")!!.text() })
        assertEquals("Afrikaans", doc.body().attr("data-fragment"))
    }

    @Test
    fun expandAllOpensEverything() {
        val doc = render(expandAll = true)
        assertEquals(entry.languages.size, doc.select("details.lang[open]").size)
    }

    @Test
    fun includesThemeChipsAndAttribution() {
        val doc = render()
        assertTrue(doc.selectFirst("html")!!.hasClass("wh-dark"))
        val chips = doc.select(".wh-langs a.wh-chip")
        assertEquals(6, chips.size)
        assertTrue(chips.last()!!.hasClass("wh-more"))
        val footer = doc.selectFirst("footer.wh-footer")!!
        assertTrue(footer.text().contains("Creative Commons Attribution-ShareAlike 4.0"))
        assertTrue(footer.select("a").any { it.attr("href") == "https://en.wiktionary.org/wiki/dog" })
        assertEquals(1, doc.select("script[src='${EntryHtml.ASSET_BASE}entry.js']").size)
    }
}
