package app.stemma.data

import java.net.URI
import java.net.URLDecoder

/** Helpers for turning Wiktionary URLs into page titles and back. */
object Titles {
    const val HOST = "en.wiktionary.org"
    private const val MOBILE_HOST = "en.m.wiktionary.org"
    const val SITE = "https://$HOST"

    /**
     * Namespaces whose pages are dictionary content and can be shown inside the app.
     * Pages in every other namespace (Category, Template, Wiktionary, …) open in the browser.
     */
    private val readerNamespaces = setOf("appendix", "reconstruction", "rhymes", "thesaurus", "citations")

    /** All English Wiktionary namespace prefixes, so a title like "re:" is not mistaken for a namespace. */
    private val knownNamespaces = readerNamespaces + setOf(
        "talk", "user", "user talk", "wiktionary", "wiktionary talk", "wt", "file", "image", "mediawiki",
        "template", "help", "category", "concordance", "index", "transwiki", "sign gloss", "module",
        "special", "media", "timedtext", "gadget", "gadget definition", "topic", "appendix talk",
        "reconstruction talk", "rhymes talk", "thesaurus talk", "citations talk", "template talk",
        "category talk", "help talk", "module talk", "file talk", "mediawiki talk", "w", "wikipedia",
        "commons", "d", "wikidata", "s", "wikisource",
    )

    /** What tapping a link inside an entry should do. */
    sealed interface LinkTarget {
        data class Entry(val title: String, val fragment: String?) : LinkTarget
        data class External(val url: String) : LinkTarget
    }

    /** "ice_cream" → "ice cream"; trims and collapses whitespace. */
    fun normalize(raw: String): String =
        raw.replace('_', ' ').trim().replace(Regex("\\s+"), " ")

    /** "ice cream" → "ice_cream", the form used in URLs and REST API paths. */
    fun toDbKey(title: String): String = normalize(title).replace(' ', '_')

    fun pageUrl(title: String, fragment: String? = null): String {
        val path = encodePath(toDbKey(title))
        return "$SITE/wiki/$path" + (fragment?.let { "#" + encodePath(it) } ?: "")
    }

    fun historyUrl(title: String): String =
        "$SITE/w/index.php?title=${encodePath(toDbKey(title))}&action=history"

    /** Percent-encodes everything except characters that MediaWiki leaves readable in URLs. */
    fun encodePath(s: String): String {
        val sb = StringBuilder()
        for (b in s.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            val ch = c.toChar()
            if (ch.isLetterOrDigit() && c < 0x80 || ch in "-._~:!*'(),;@$") sb.append(ch)
            else sb.append('%').append("%02X".format(c))
        }
        return sb.toString()
    }

    /** Decides whether a tapped link opens another entry in the app or goes to the browser. */
    fun classify(url: String): LinkTarget {
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return LinkTarget.External(url)
        }
        val host = uri.host?.lowercase()
        if (host != HOST && host != MOBILE_HOST) return LinkTarget.External(url)
        val rawPath = uri.rawPath ?: return LinkTarget.External(url)
        if (!rawPath.startsWith("/wiki/")) return LinkTarget.External(url)
        val title = normalize(decode(rawPath.removePrefix("/wiki/")))
        if (title.isEmpty()) return LinkTarget.External(url)
        val fragment = uri.rawFragment?.let { decode(it) }?.takeIf { it.isNotBlank() }
        return if (isReaderTitle(title)) LinkTarget.Entry(title, fragment) else LinkTarget.External(url)
    }

    /** True when the title is a dictionary page (main namespace or one of [readerNamespaces]). */
    fun isReaderTitle(title: String): Boolean {
        val colon = title.indexOf(':')
        if (colon <= 0) return true
        val prefix = title.substring(0, colon).replace('_', ' ').trim().lowercase()
        return prefix !in knownNamespaces || prefix in readerNamespaces
    }

    /** Parses a redirect target such as "//en.wiktionary.org/wiki/godma" into "godma". */
    fun titleFromLocation(location: String): String? {
        val path = location.substringAfter("/wiki/", missingDelimiterValue = "")
        if (path.isEmpty()) return null
        return normalize(decode(path.substringBefore('#').substringBefore('?'))).ifEmpty { null }
    }

    private fun decode(s: String): String = try {
        URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    } catch (e: IllegalArgumentException) {
        s
    }
}
