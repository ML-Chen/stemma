package app.stemma.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset

/** How an entry was obtained. */
sealed interface EntryLoad {
    data class Loaded(val content: EntryContent, val offline: Boolean) : EntryLoad
    data class NotFound(val title: String, val suggestions: List<SearchHit>) : EntryLoad
    data class Failed(val error: Throwable) : EntryLoad
}

/**
 * Loads entries. To make pages appear quickly it keeps the last few entries in memory, keeps
 * recently viewed entries on disk for [RecentEntries.FRESH_FOR_MS], shares one download between
 * everyone waiting for the same word, and can start a download before the user taps ([prefetch]).
 */
class EntryRepository(
    private val api: WiktionaryApi,
    private val library: Library,
    /** Runs downloads and bookkeeping that should outlive the screen that started them. */
    private val scope: CoroutineScope,
    private val recent: RecentEntries? = null,
    /** The user's preferred language, used for the one-line summary of prefetched entries. */
    private val preferredLanguage: suspend () -> String = { "English" },
) {
    // Entries for common words can be several megabytes, so only a few are kept in memory.
    private val memory = object : LinkedHashMap<String, EntryContent>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EntryContent>?) = size > 12
    }
    private val inFlight = HashMap<String, Deferred<EntryContent>>()

    suspend fun load(title: String, preferredLanguage: String, forceRefresh: Boolean = false): EntryLoad {
        val key = Titles.normalize(title)
        return try {
            val stored = if (forceRefresh) null else storedCopy(key)
            finish(stored ?: download(key, preferredLanguage, forceRefresh).await())
        } catch (e: CancellationException) {
            throw e
        } catch (e: NotFoundException) {
            notFound(key, preferredLanguage)
        } catch (e: Exception) {
            // No connection, a server error, or a captive portal answering with a page that isn't Wiktionary's.
            offlineOrFailure(key, e)
        }
    }

    /** Starts downloading [title] in the background so that opening it later is instant. Errors are ignored. */
    fun prefetch(title: String) {
        val key = Titles.normalize(title)
        if (synchronized(memory) { memory[key] } != null) return
        scope.launch {
            if (storedCopy(key) == null) {
                runCatching { download(key, preferredLanguage(), forceRefresh = false).await() }
            }
        }
    }

    /** A copy that is recent enough to show without asking Wiktionary again, if there is one. */
    private suspend fun storedCopy(key: String): EntryContent? {
        synchronized(memory) { memory[key] }?.let { return it }
        val fromDisk = recent?.read(key, maxAgeMs = RecentEntries.FRESH_FOR_MS)
            ?: library.offlineCopy(key)?.takeIf { System.currentTimeMillis() - it.fetchedAt < RecentEntries.FRESH_FOR_MS }
        fromDisk?.let { remember(key, it) }
        return fromDisk
    }

    /** One shared download per title: a tap on a word that is already being prefetched waits for that download. */
    private fun download(title: String, language: String, forceRefresh: Boolean): Deferred<EntryContent> = synchronized(inFlight) {
        inFlight[title]?.takeIf { it.isActive && !forceRefresh }?.let { return it }
        val job = scope.async(Dispatchers.IO) {
            val html = api.mobileHtml(title)
            val content = withContext(Dispatchers.Default) { EntryProcessor.process(html, title, language) }
            remember(title, content)
            recent?.write(title, content)
            content
        }
        inFlight[title] = job
        job.invokeOnCompletion { synchronized(inFlight) { if (inFlight[title] === job) inFlight.remove(title) } }
        job
    }

    private fun remember(key: String, content: EntryContent) = synchronized(memory) {
        memory[key] = content
        memory[content.title] = content
    }

    private suspend fun notFound(key: String, preferredLanguage: String): EntryLoad {
        // Phone keyboards capitalise the first letter; Wiktionary titles are case-sensitive.
        val lower = key.replaceFirstChar { it.lowercase() }
        if (lower != key) {
            try {
                return finish(storedCopy(lower) ?: download(lower, preferredLanguage, forceRefresh = false).await())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
        val suggestions = try {
            api.searchPages(key).filter { Titles.isReaderTitle(it.title) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }
        return EntryLoad.NotFound(key, suggestions)
    }

    /** When Wiktionary can't be reached, show any stored copy, however old. */
    private suspend fun offlineOrFailure(key: String, error: Exception): EntryLoad {
        for (candidate in listOf(key, key.replaceFirstChar { it.lowercase() }).distinct()) {
            val copy = (if (library.isSaved(candidate)) library.offlineCopy(candidate) else null)
                ?: recent?.read(candidate, maxAgeMs = Long.MAX_VALUE)
            if (copy != null) return EntryLoad.Loaded(copy, offline = true)
        }
        return EntryLoad.Failed(error)
    }

    private fun finish(content: EntryContent): EntryLoad {
        // History and the saved copy are updated in the background, so the page isn't held up by disk writes.
        scope.launch {
            library.recordView(content)
            library.refreshSaved(content)
        }
        return EntryLoad.Loaded(content, offline = false)
    }
}

/**
 * Processed copies of recently viewed entries, kept in the app's cache folder (which Android may
 * clear when storage runs low). Revisiting a word within [FRESH_FOR_MS] opens it without the network.
 */
class RecentEntries(private val dir: File, private val maxFiles: Int = 80, private val maxBytes: Long = 60L * 1024 * 1024) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun read(title: String, maxAgeMs: Long): EntryContent? = withContext(Dispatchers.IO) {
        val file = fileFor(title)
        if (!file.exists()) return@withContext null
        val content = try {
            json.decodeFromString(EntryContent.serializer(), file.readText())
        } catch (_: Exception) {
            file.delete()
            return@withContext null
        }
        if (System.currentTimeMillis() - content.fetchedAt > maxAgeMs) return@withContext null
        file.setLastModified(System.currentTimeMillis())
        content
    }

    suspend fun write(title: String, content: EntryContent) = withContext(Dispatchers.IO) {
        try {
            dir.mkdirs()
            val text = json.encodeToString(EntryContent.serializer(), content)
            for (key in setOf(Titles.normalize(title), content.title)) {
                val tmp = File.createTempFile("entry", ".tmp", dir)
                tmp.writeText(text)
                if (!tmp.renameTo(fileFor(key))) tmp.delete()
            }
            trim()
        } catch (_: IOException) {
        }
    }

    /** Deletes the least recently used copies beyond the size limits. */
    private fun trim() {
        val files = dir.listFiles { f -> f.name.endsWith(".json") }?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        files.forEachIndexed { i, f ->
            total += f.length()
            if (i >= maxFiles || total > maxBytes) f.delete()
        }
    }

    private fun fileFor(title: String) = File(dir, sha256(Titles.normalize(title)) + ".json")

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        /** Wiktionary entries change slowly; a copy younger than this is shown without checking for updates. */
        const val FRESH_FOR_MS = 3L * 24 * 60 * 60 * 1000
    }
}

@Serializable
private data class ExploreCache(val wordOfTheDayDate: String = "", val wordOfTheDay: WordCard? = null)

/** Word of the Day (cached for the UTC day it belongs to) and random words. */
class ExploreRepository(
    private val api: WiktionaryApi,
    private val cacheFile: File,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** The Wiktionary main page switches its Word of the Day at midnight UTC. */
    private fun today(): String = LocalDate.now(ZoneOffset.UTC).toString()

    suspend fun cachedWordOfTheDay(): WordCard? = withContext(Dispatchers.IO) {
        runCatching { json.decodeFromString<ExploreCache>(cacheFile.readText()) }.getOrNull()?.wordOfTheDay
    }

    suspend fun wordOfTheDay(forceRefresh: Boolean = false): WordCard {
        val cached = withContext(Dispatchers.IO) {
            runCatching { json.decodeFromString<ExploreCache>(cacheFile.readText()) }.getOrNull()
        }
        val cachedCard = cached?.wordOfTheDay
        if (!forceRefresh && cachedCard != null && cached.wordOfTheDayDate == today()) return cachedCard
        val fresh = api.wordOfTheDay()
        withContext(Dispatchers.IO) {
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(json.encodeToString(ExploreCache.serializer(), ExploreCache(today(), fresh)))
            }
        }
        return fresh
    }

    suspend fun randomWord(language: String): WordCard {
        val title = api.randomTitle(language)
        val summary = try {
            api.definitionSummary(title, language)
        } catch (e: IOException) {
            WordSummary(language)
        }
        return WordCard(title = title, summary = summary)
    }
}
