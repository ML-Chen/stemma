package app.stemma.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParsersTest {
    @Test
    fun parsesWordOfTheDay() {
        val html = Json.parseToJsonElement(Samples.read("wotd.json")).jsonObject["parse"]!!.jsonObject["text"]!!.jsonPrimitive.content
        val card = WordOfTheDayParser.parse(html)
        assertEquals("fabada", card.title)
        assertEquals("English", card.fragment)
        assertEquals("Noun", card.summary.partOfSpeech)
        assertEquals("October 12", card.dateLabel)
        assertTrue(card.summary.definition, card.summary.definition.contains("stew from Asturias"))
    }

    @Test
    fun parsesFirstDefinition() {
        val root = Json.parseToJsonElement(Samples.read("def_serendipity.json")).jsonObject
        val summary = DefinitionParser.firstDefinition(root, "English")
        assertEquals("Noun", summary.partOfSpeech)
        assertTrue(summary.definition.startsWith("The phenomenon of making an unplanned, fortunate discovery"))
    }

    @Test
    fun definitionTextLeavesOutTemplateStyles() {
        val root = Json.parseToJsonElement(Samples.read("def_defenestration.json")).jsonObject
        val summary = DefinitionParser.firstDefinition(root, "English")
        assertTrue(summary.definition, summary.definition.startsWith("The act of throwing something"))
        assertTrue(summary.definition, !summary.definition.contains("mw-parser-output"))
    }

    @Test
    fun definitionPrefersTheRequestedLanguage() {
        val root = Json.parseToJsonElement(Samples.read("def_set.json")).jsonObject
        val english = DefinitionParser.firstDefinition(root, "English")
        assertEquals("English", english.language)
        val french = DefinitionParser.firstDefinition(root, "French")
        assertEquals("French", french.language)
        assertTrue(french.definition.isNotBlank())
    }
}

class TitlesTest {
    @Test
    fun classifiesEntryLinks() {
        assertEquals(
            Titles.LinkTarget.Entry("ice cream", "English"),
            Titles.classify("https://en.wiktionary.org/wiki/ice_cream#English"),
        )
        assertEquals(
            Titles.LinkTarget.Entry("Appendix:Glossary", "uncountable"),
            Titles.classify("https://en.wiktionary.org/wiki/Appendix:Glossary#uncountable"),
        )
        assertEquals(
            Titles.LinkTarget.Entry("Reconstruction:Old English/dogga", null),
            Titles.classify("https://en.wiktionary.org/wiki/Reconstruction:Old_English/dogga"),
        )
        assertEquals(Titles.LinkTarget.Entry("naïve", null), Titles.classify("https://en.m.wiktionary.org/wiki/na%C3%AFve"))
        assertEquals(Titles.LinkTarget.Entry("C++", null), Titles.classify("https://en.wiktionary.org/wiki/C%2B%2B"))
        assertEquals(Titles.LinkTarget.Entry("re:", null), Titles.classify("https://en.wiktionary.org/wiki/re:"))
    }

    @Test
    fun sendsOtherLinksToTheBrowser() {
        assertTrue(Titles.classify("https://en.wiktionary.org/wiki/Category:English_nouns") is Titles.LinkTarget.External)
        assertTrue(Titles.classify("https://en.wiktionary.org/wiki/Wiktionary:About") is Titles.LinkTarget.External)
        assertTrue(Titles.classify("https://en.wiktionary.org/w/index.php?title=dog&action=history") is Titles.LinkTarget.External)
        assertTrue(Titles.classify("https://en.wikipedia.org/wiki/Dog") is Titles.LinkTarget.External)
        assertTrue(Titles.classify("https://search.worldcat.org/title/1456001") is Titles.LinkTarget.External)
    }

    @Test
    fun buildsPageUrls() {
        assertEquals("https://en.wiktionary.org/wiki/ice_cream", Titles.pageUrl("ice cream"))
        assertEquals("https://en.wiktionary.org/wiki/na%C3%AFve#French", Titles.pageUrl("naïve", "French"))
        assertEquals("https://en.wiktionary.org/wiki/and%2For", Titles.pageUrl("and/or"))
    }

    @Test
    fun readsRedirectLocations() {
        assertEquals("godma", Titles.titleFromLocation("//en.wiktionary.org/wiki/godma"))
        assertEquals("ice cream", Titles.titleFromLocation("https://en.wiktionary.org/wiki/ice_cream"))
        assertNull(Titles.titleFromLocation("https://en.wiktionary.org/w/index.php"))
    }
}
