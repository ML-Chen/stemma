package app.stemma.data

import kotlinx.serialization.Serializable

/** A dictionary entry after [EntryProcessor] has cleaned up the HTML that Wiktionary returns. */
@Serializable
data class EntryContent(
    /** Canonical page title, with spaces rather than underscores (for example "ice cream"). */
    val title: String,
    /** Title as Wiktionary displays it (usually identical to [title]). */
    val displayTitle: String,
    val revision: Long,
    /** Processed HTML for the page body; [EntryHtml] wraps it in a full document. */
    val bodyHtml: String,
    /** One entry per language heading on the page, in page order. */
    val languages: List<LanguageSection>,
    /** First definition of the preferred (or first) language, used for list previews. */
    val summary: WordSummary,
    val fetchedAt: Long,
)

@Serializable
data class LanguageSection(
    /** Language name as shown in the page heading, for example "English". */
    val name: String,
    /** HTML id of the language heading, used for in-page jumps. */
    val anchor: String,
    /** HTML id of the collapsible block that wraps the language section. */
    val blockId: String,
    val headings: List<SubHeading>,
)

@Serializable
data class SubHeading(val title: String, val anchor: String, val level: Int)

/** A part of speech plus one short definition, for example ("Noun", "A mammal of the family Canidae"). */
@Serializable
data class WordSummary(
    val language: String = "",
    val partOfSpeech: String = "",
    val definition: String = "",
) {
    val isEmpty: Boolean get() = definition.isBlank()

    /** One line such as "noun · A mammal of the family Canidae". */
    fun oneLine(): String = listOf(partOfSpeech.lowercase(), definition)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
}

/** A word shown on an Explore card, such as the Word of the Day or a random word. */
@Serializable
data class WordCard(
    val title: String,
    val fragment: String? = null,
    val summary: WordSummary = WordSummary(),
    /** Label such as "October 12" for the Word of the Day; empty for random words. */
    val dateLabel: String = "",
)

@Serializable
data class SavedWord(
    val title: String,
    val summary: WordSummary = WordSummary(),
    val savedAt: Long,
)

@Serializable
data class HistoryItem(
    val title: String,
    val summary: WordSummary = WordSummary(),
    val viewedAt: Long,
)

/** A full-text search hit, used when the exact word has no entry. */
@Serializable
data class SearchHit(val title: String, val excerpt: String = "")
