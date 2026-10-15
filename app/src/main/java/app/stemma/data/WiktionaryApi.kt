package app.stemma.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** The page does not exist on Wiktionary. */
class NotFoundException(val title: String) : IOException("No entry for $title")

/** Wiktionary answered 429 (too many requests from this network). */
class RateLimitedException : IOException("Wiktionary is receiving too many requests from this network")

/** Any other unsuccessful HTTP status. */
class HttpStatusException(val code: Int) : IOException("Wiktionary returned HTTP $code")

/**
 * Calls the public English Wiktionary APIs:
 * - REST `page/mobile-html` for full entries (the same endpoint the Wikipedia app reads articles from),
 * - REST `page/definition` for short definitions,
 * - `rest.php/v1/search` for search,
 * - the Action API to read the Word of the Day page.
 */
class WiktionaryApi(
    private val client: OkHttpClient,
    private val baseUrl: HttpUrl = "https://en.wiktionary.org/".toHttpUrl(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val noRedirectClient = client.newBuilder().followRedirects(false).build()

    /** Raw mobile-html for a page. Redirects are followed. */
    suspend fun mobileHtml(title: String): String {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/rest_v1/page/mobile-html")
            .addPathSegment(Titles.toDbKey(title))
            .build()
        return fetchString(url, title)
    }

    /** Page titles that start with [query], best matches first. */
    suspend fun searchTitles(query: String, limit: Int = 12): List<String> {
        if (query.isBlank()) return emptyList()
        val url = baseUrl.newBuilder()
            .addPathSegments("w/rest.php/v1/search/title")
            .addQueryParameter("q", query.trim())
            .addQueryParameter("limit", limit.toString())
            .build()
        val body = fetchString(url)
        return json.parseToJsonElement(body).jsonObject["pages"]?.jsonArray.orEmpty()
            .mapNotNull { it.jsonObject["title"]?.jsonPrimitive?.contentOrNull }
            .filter { Titles.isReaderTitle(it) }
    }

    /** Full-text search, used to suggest alternatives when a word has no entry. */
    suspend fun searchPages(query: String, limit: Int = 10): List<SearchHit> {
        if (query.isBlank()) return emptyList()
        val url = baseUrl.newBuilder()
            .addPathSegments("w/rest.php/v1/search/page")
            .addQueryParameter("q", query.trim())
            .addQueryParameter("limit", limit.toString())
            .build()
        val body = fetchString(url)
        return json.parseToJsonElement(body).jsonObject["pages"]?.jsonArray.orEmpty().mapNotNull { page ->
            val obj = page.jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val excerpt = obj["excerpt"]?.jsonPrimitive?.contentOrNull?.let { Jsoup.parse(it).text() }.orEmpty()
            SearchHit(title, excerpt)
        }
    }

    /** Today's Word of the Day, read from the page that Wiktionary's main page shows. */
    suspend fun wordOfTheDay(): WordCard {
        val url = baseUrl.newBuilder()
            .addPathSegments("w/api.php")
            .addQueryParameter("action", "parse")
            .addQueryParameter("page", "Wiktionary:Word of the day")
            .addQueryParameter("prop", "text")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("disableeditsection", "1")
            .build()
        val body = fetchString(url)
        val html = json.parseToJsonElement(body).jsonObject["parse"]?.jsonObject?.get("text")
            ?.jsonPrimitive?.contentOrNull ?: throw IOException("Unexpected Word of the Day response")
        return withContext(Dispatchers.Default) { WordOfTheDayParser.parse(html) }
    }

    /**
     * A random lemma in [language], using the Special:RandomInCategory redirect
     * (for example "English lemmas"). Falls back to any random page.
     */
    suspend fun randomTitle(language: String): String {
        val url = baseUrl.newBuilder()
            .addPathSegments("wiki/Special:RandomInCategory")
            .addPathSegment("${Titles.toDbKey(language)}_lemmas")
            .build()
        val request = Request.Builder().url(url).build()
        val location = withContext(Dispatchers.IO) {
            noRedirectClient.newCall(request).await().use { response ->
                if (response.code == 429) throw RateLimitedException()
                if (response.isRedirect) response.header("Location") else null
            }
        }
        return location?.let { Titles.titleFromLocation(it) } ?: randomAnyTitle()
    }

    private suspend fun randomAnyTitle(): String {
        val url = baseUrl.newBuilder()
            .addPathSegments("w/api.php")
            .addQueryParameter("action", "query")
            .addQueryParameter("list", "random")
            .addQueryParameter("rnnamespace", "0")
            .addQueryParameter("rnlimit", "1")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .build()
        val body = fetchString(url)
        return json.parseToJsonElement(body).jsonObject["query"]?.jsonObject?.get("random")?.jsonArray
            ?.firstOrNull()?.jsonObject?.get("title")?.jsonPrimitive?.contentOrNull
            ?: throw IOException("Unexpected random page response")
    }

    /** First definition of [title] in [language], from the REST definition endpoint. */
    suspend fun definitionSummary(title: String, language: String): WordSummary {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/rest_v1/page/definition")
            .addPathSegment(Titles.toDbKey(title))
            .build()
        val body = fetchString(url, title)
        return withContext(Dispatchers.Default) {
            DefinitionParser.firstDefinition(json.parseToJsonElement(body).jsonObject, language)
        }
    }

    /** GETs [url] and returns the body. Runs on the IO dispatcher: Android forbids network reads on the main thread. */
    private suspend fun fetchString(url: HttpUrl, title: String? = null): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).await().use { response ->
            if (!response.isSuccessful) {
                throw when (response.code) {
                    404 -> NotFoundException(title ?: url.toString())
                    429 -> RateLimitedException()
                    else -> HttpStatusException(response.code)
                }
            }
            response.body.string()
        }
    }
}

/** Reads the Word of the Day HTML, which marks its parts with WOTD-rss-* ids for Wiktionary's feed. */
object WordOfTheDayParser {
    fun parse(html: String): WordCard {
        val doc = Jsoup.parse(html, Titles.SITE + "/wiki/")
        val titleSpan = doc.getElementById("WOTD-rss-title") ?: throw IOException("Word of the Day not found")
        val link = titleSpan.closest("a")
        val target = link?.absUrl("href")?.let { Titles.classify(it) } as? Titles.LinkTarget.Entry
        val title = target?.title ?: Titles.normalize(titleSpan.text())
        val partOfSpeech = titleSpan.closest("td")?.selectFirst("> i")?.text().orEmpty()
        val description = doc.getElementById("WOTD-rss-description")
        val firstSense = description?.selectFirst("ol > li")?.let { li ->
            li.clone().apply { select("ol, ul, dl").remove() }.text()
        } ?: description?.text().orEmpty()
        return WordCard(
            title = title,
            fragment = target?.fragment,
            summary = WordSummary("English", expandPartOfSpeech(partOfSpeech), firstSense.trim()),
            dateLabel = doc.getElementById("WOTD-rss-date")?.text().orEmpty(),
        )
    }

    /** The Word of the Day template abbreviates parts of speech ("n", "v", "adj"). */
    private fun expandPartOfSpeech(abbreviation: String): String = when (abbreviation.trim().lowercase().removeSuffix(".")) {
        "n" -> "Noun"
        "v" -> "Verb"
        "adj" -> "Adjective"
        "adv" -> "Adverb"
        "prep" -> "Preposition"
        "conj" -> "Conjunction"
        "interj" -> "Interjection"
        "pron" -> "Pronoun"
        "prop n", "proper n" -> "Proper noun"
        "phrase" -> "Phrase"
        else -> abbreviation.trim()
    }
}

/** Reads the REST `page/definition` response: language code → list of part-of-speech groups. */
object DefinitionParser {
    fun firstDefinition(root: JsonObject, language: String): WordSummary {
        val groups = root.values.filterIsInstance<JsonArray>().flatten().map { it.jsonObject }
        val ordered = groups.filter { it.string("language").equals(language, ignoreCase = true) } + groups
        for (group in ordered) {
            val definitions = group["definitions"] as? JsonArray ?: continue
            for (definition in definitions) {
                val html = definition.jsonObject.string("definition")
                val text = Jsoup.parse(html).text().replace(Regex("\\s+"), " ").trim()
                if (text.isNotEmpty()) {
                    return WordSummary(group.string("language"), group.string("partOfSpeech"), text)
                }
            }
        }
        return WordSummary(language)
    }

    private fun JsonObject.string(key: String): String = this[key]?.jsonPrimitive?.contentOrNull.orEmpty()
}

/** Suspends until the call completes; cancelling the coroutine cancels the HTTP call. */
suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) = cont.resume(response) { _, value, _ -> value.close() }
        override fun onFailure(call: Call, e: IOException) {
            if (!cont.isCancelled) cont.resumeWithException(e)
        }
    })
    cont.invokeOnCancellation { runCatching { cancel() } }
}
