package app.stemma.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest

@Serializable
data class LibraryData(
    val saved: List<SavedWord> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    val recentSearches: List<String> = emptyList(),
)

/**
 * Saved words, reading history and recent searches, kept in one small JSON file.
 * Saved words also keep a full offline copy of their entry in [offlineDir].
 */
class Library(
    private val file: File,
    private val offlineDir: File,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private val mutex = Mutex()
    private val _data = MutableStateFlow(LibraryData())
    val data: StateFlow<LibraryData> = _data.asStateFlow()

    private val loaded = scope.launch(Dispatchers.IO) {
        val initial = runCatching { json.decodeFromString<LibraryData>(file.readText()) }.getOrNull()
        if (initial != null) _data.value = initial
    }

    fun isSaved(title: String): Boolean = _data.value.saved.any { it.title == title }

    suspend fun save(entry: EntryContent) = locked {
        writeOffline(entry)
        mutate { data ->
            val item = SavedWord(entry.title, entry.summary, System.currentTimeMillis())
            data.copy(saved = listOf(item) + data.saved.filterNot { it.title == entry.title })
        }
    }

    suspend fun unsave(title: String) = locked {
        mutate { data -> data.copy(saved = data.saved.filterNot { it.title == title }) }
        withContext(Dispatchers.IO) { offlineFile(title).delete() }
    }

    /** Puts a removed item (and its offline copy, if there was one) back, for "Undo". */
    suspend fun restore(item: SavedWord, offlineCopy: EntryContent?) = locked {
        if (_data.value.saved.none { it.title == item.title }) {
            if (offlineCopy != null) writeOffline(offlineCopy)
            mutate { data -> data.copy(saved = (data.saved + item).sortedByDescending { it.savedAt }) }
        }
    }

    /** Refreshes the stored copy and preview of a saved word after it was viewed online. */
    suspend fun refreshSaved(entry: EntryContent) = locked {
        if (isSaved(entry.title)) {
            writeOffline(entry)
            mutate { data ->
                data.copy(saved = data.saved.map { if (it.title == entry.title) it.copy(summary = entry.summary) else it })
            }
        }
    }

    suspend fun recordView(entry: EntryContent, at: Long = System.currentTimeMillis()) = update { data ->
        val item = HistoryItem(entry.title, entry.summary, at)
        data.copy(history = (listOf(item) + data.history.filterNot { it.title == entry.title }).take(MAX_HISTORY))
    }

    suspend fun removeHistory(title: String) = update { data ->
        data.copy(history = data.history.filterNot { it.title == title })
    }

    suspend fun clearHistory() = update { it.copy(history = emptyList()) }

    suspend fun addRecentSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        update { data ->
            data.copy(recentSearches = (listOf(q) + data.recentSearches.filterNot { it.equals(q, ignoreCase = true) }).take(MAX_SEARCHES))
        }
    }

    suspend fun removeRecentSearch(query: String) = update { data ->
        data.copy(recentSearches = data.recentSearches.filterNot { it == query })
    }

    suspend fun clearRecentSearches() = update { it.copy(recentSearches = emptyList()) }

    /** The stored offline copy of a saved word, or null. */
    suspend fun offlineCopy(title: String): EntryContent? = withContext(Dispatchers.IO) {
        val f = offlineFile(title)
        if (!f.exists()) null else runCatching { json.decodeFromString<EntryContent>(f.readText()) }.getOrNull()
    }

    /** Writes the offline copy. If the write fails (for example, the disk is full), any older copy is kept. */
    private suspend fun writeOffline(entry: EntryContent) = withContext(Dispatchers.IO) {
        try {
            writeAtomically(offlineFile(entry.title), json.encodeToString(EntryContent.serializer(), entry))
        } catch (_: IOException) {
        }
    }

    private fun offlineFile(title: String): File = File(offlineDir, sha256(title) + ".json")

    /**
     * Runs [block] while holding the library lock, after the stored data has loaded. Every change to
     * library.json and to the offline copies goes through this lock, so changes can't interleave.
     */
    private suspend fun <T> locked(block: suspend () -> T): T {
        loaded.join()
        return mutex.withLock { block() }
    }

    private suspend fun update(transform: (LibraryData) -> LibraryData) = locked { mutate(transform) }

    /** Applies [transform] and saves the result. Call only while holding the lock. */
    private suspend fun mutate(transform: (LibraryData) -> LibraryData) {
        val next = transform(_data.value)
        if (next == _data.value) return
        _data.value = next
        withContext(Dispatchers.IO) {
            try {
                writeAtomically(file, json.encodeToString(LibraryData.serializer(), next))
            } catch (e: IOException) {
                // Keep working from memory; the next successful write saves everything.
            }
        }
    }

    /** Writes through a uniquely named temporary file, then renames it over [target]. */
    private fun writeAtomically(target: File, text: String) {
        val dir = target.parentFile!!.apply { mkdirs() }
        val tmp = File.createTempFile(target.name, ".tmp", dir)
        try {
            tmp.writeText(text)
            if (!tmp.renameTo(target)) throw IOException("Could not replace $target")
        } finally {
            tmp.delete()
        }
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val MAX_HISTORY = 500
        const val MAX_SEARCHES = 20
    }
}
