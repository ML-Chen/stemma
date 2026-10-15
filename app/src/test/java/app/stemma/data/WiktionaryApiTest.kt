package app.stemma.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class WiktionaryApiTest {
    private val server = MockWebServer()
    private lateinit var api: WiktionaryApi

    @Before
    fun setUp() {
        server.start()
        api = WiktionaryApi(OkHttpClient(), server.url("/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueue(code: Int, body: String = "", vararg headers: Pair<String, String>) {
        val builder = MockResponse.Builder().code(code).body(body)
        headers.forEach { (k, v) -> builder.addHeader(k, v) }
        server.enqueue(builder.build())
    }

    @Test
    fun searchReturnsTitles() = runBlocking {
        enqueue(200, Samples.read("search_seren.json"))
        val titles = api.searchTitles("seren")
        assertEquals(listOf("seren", "serene", "sereno", "serendipity"), titles.take(4))
        val request = server.takeRequest()
        assertEquals("/w/rest.php/v1/search/title", request.url.encodedPath)
        assertEquals("seren", request.url.queryParameter("q"))
    }

    @Test
    fun mobileHtmlEncodesTitles() = runBlocking {
        enqueue(200, "<html></html>")
        api.mobileHtml("and/or")
        assertEquals("/api/rest_v1/page/mobile-html/and%2For", server.takeRequest().url.encodedPath)
    }

    @Test
    fun missingPageThrowsNotFound() = runBlocking {
        enqueue(404, "{}")
        try {
            api.mobileHtml("qwxzv")
            fail("expected NotFoundException")
        } catch (e: NotFoundException) {
            assertEquals("qwxzv", e.title)
        }
    }

    @Test
    fun rateLimitIsReported() = runBlocking {
        enqueue(429, "You are making too many requests")
        try {
            api.searchTitles("a")
            fail("expected RateLimitedException")
        } catch (_: RateLimitedException) {
        }
    }

    @Test
    fun randomWordFollowsTheCategoryRedirect() = runBlocking {
        enqueue(302, "", "Location" to "//en.wiktionary.org/wiki/godma")
        assertEquals("godma", api.randomTitle("English"))
        assertEquals("/wiki/Special:RandomInCategory/English_lemmas", server.takeRequest().url.encodedPath)
    }

    @Test
    fun randomWordFallsBackToAnyPage() = runBlocking {
        enqueue(500, "")
        enqueue(200, Samples.read("random.json"))
        assertEquals("fabrefactu", api.randomTitle("English"))
    }
}

class EntryRepositoryTest {
    private val server = MockWebServer()
    private lateinit var dir: File
    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Before
    fun setUp() {
        server.start()
        dir = Files.createTempDirectory("stemma").toFile()
    }

    @After
    fun tearDown() {
        background.cancel()
        server.close()
        dir.deleteRecursively()
    }

    private fun ok(name: String) = MockResponse.Builder().code(200).body(Samples.read(name)).build()

    private fun CoroutineScope.repository(library: Library = Library(File(dir, "library.json"), File(dir, "offline"), this)) =
        EntryRepository(WiktionaryApi(OkHttpClient(), server.url("/")), library, background, RecentEntries(File(dir, "recent")))

    @Test
    fun capitalisedTitleFallsBackToLowercase() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(ok("mobilehtml_dog.html"))
        val library = Library(File(dir, "library.json"), File(dir, "offline"), this)
        val result = repository(library).load("Dog", "English")
        assertTrue(result.toString(), result is EntryLoad.Loaded)
        assertEquals("dog", (result as EntryLoad.Loaded).content.title)
        assertEquals("/api/rest_v1/page/mobile-html/Dog", server.takeRequest().url.encodedPath)
        assertEquals("/api/rest_v1/page/mobile-html/dog", server.takeRequest().url.encodedPath)
        // History is written in the background.
        withTimeout(5_000) { library.data.first { it.history.isNotEmpty() } }
        assertEquals("dog", library.data.value.history.first().title)
    }

    @Test
    fun savedWordIsServedOfflineWhenTheNetworkFails() = runBlocking {
        server.enqueue(ok("mobilehtml_dog.html"))
        val library = Library(File(dir, "library.json"), File(dir, "offline"), this)
        val online = repository(library).load("dog", "English") as EntryLoad.Loaded
        library.save(online.content)
        server.close()

        // Refresh forces a network request, which fails, so the saved copy is shown as offline.
        val offline = repository(library).load("dog", "English", forceRefresh = true)
        assertTrue(offline.toString(), offline is EntryLoad.Loaded && offline.offline)
    }

    @Test
    fun recentlyViewedWordOpensWithoutTheNetwork() = runBlocking {
        server.enqueue(ok("mobilehtml_serendipity.html"))
        repository().load("serendipity", "English")
        // The disk copy is written in the background.
        withTimeout(5_000) { while (File(dir, "recent").listFiles().isNullOrEmpty()) delay(20) }
        server.close()

        // A new repository (as after restarting the app) has an empty memory cache.
        val again = repository().load("serendipity", "English")
        assertTrue(again.toString(), again is EntryLoad.Loaded && !again.offline)
        assertEquals("serendipity", (again as EntryLoad.Loaded).content.title)
    }

    @Test
    fun prefetchAndTapShareOneDownload() = runBlocking {
        server.enqueue(ok("mobilehtml_fabada.html").newBuilder().headersDelay(300, TimeUnit.MILLISECONDS).build())
        val repo = repository()
        repo.prefetch("fabada")
        delay(50)
        val result = repo.load("fabada", "English")
        assertTrue(result.toString(), result is EntryLoad.Loaded)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun missingWordReturnsSuggestions() = runBlocking {
        server.enqueue(MockResponse.Builder().code(404).build())
        server.enqueue(
            MockResponse.Builder().code(200)
                .body("""{"pages":[{"title":"dogged","excerpt":"<span class=\"searchmatch\">dog</span>ged"}]}""")
                .build(),
        )
        val result = repository().load("doggx", "English")
        assertEquals(EntryLoad.NotFound("doggx", listOf(SearchHit("dogged", "dogged"))), result)
    }
}

class LibraryTest {
    @Test
    fun keepsSavedWordsHistoryAndSearchesAcrossRestarts() = runBlocking {
        val dir = Files.createTempDirectory("stemma").toFile()
        try {
            val entry = EntryProcessor.process(Samples.read("mobilehtml_dog.html"), "dog", "English", 0L)
            val first = Library(File(dir, "library.json"), File(dir, "offline"), this)
            first.save(entry)
            first.recordView(entry)
            first.addRecentSearch("dog")
            first.addRecentSearch("cat")
            first.addRecentSearch("DOG")

            val second = Library(File(dir, "library.json"), File(dir, "offline"), this)
            second.addRecentSearch("bird") // waits for the file to load
            val data = second.data.value
            assertEquals(listOf("dog"), data.saved.map { it.title })
            assertEquals("Noun", data.saved.first().summary.partOfSpeech)
            assertEquals(listOf("dog"), data.history.map { it.title })
            assertEquals(listOf("bird", "DOG", "cat"), data.recentSearches)
            assertEquals(entry, second.offlineCopy("dog"))

            second.unsave("dog")
            assertTrue(second.data.value.saved.isEmpty())
            assertEquals(null, second.offlineCopy("dog"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun concurrentSaveAndUnsaveKeepOfflineCopiesConsistent() = runBlocking {
        val dir = Files.createTempDirectory("stemma").toFile()
        try {
            val entry = EntryProcessor.process(Samples.read("mobilehtml_fabada.html"), "fabada", "English", 0L)
            val library = Library(File(dir, "library.json"), File(dir, "offline"), this)
            repeat(20) { round ->
                val jobs = List(6) { i ->
                    launch(Dispatchers.Default) {
                        when ((i + round) % 3) {
                            0 -> library.save(entry)
                            1 -> library.unsave("fabada")
                            else -> library.refreshSaved(entry)
                        }
                    }
                }
                jobs.joinAll()
                val saved = library.isSaved("fabada")
                val copy = library.offlineCopy("fabada")
                assertEquals("round $round: saved=$saved but offline copy present=${copy != null}", saved, copy != null)
            }
            assertTrue(File(dir, "offline").listFiles()!!.none { it.name.endsWith(".tmp") })
        } finally {
            dir.deleteRecursively()
        }
    }
}
