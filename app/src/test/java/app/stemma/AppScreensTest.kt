package app.stemma

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Looper
import android.text.SpannedString
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import app.stemma.data.EntryContent
import app.stemma.data.Samples
import app.stemma.data.ThemeChoice
import app.stemma.ui.components.openInBrowser
import app.stemma.ui.entry.EntryHtml
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import okhttp3.HttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** Serves saved Wiktionary responses so the whole app can run without the network. */
object FakeWiktionary {
    val requests = mutableListOf<String>()

    private val server = MockWebServer().apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.url.encodedPath
                synchronized(requests) { requests += path }
                fun ok(name: String, type: String) =
                    MockResponse.Builder().code(200).addHeader("Content-Type", type).body(Samples.read(name)).build()
                return when {
                    path == "/w/api.php" && request.url.queryParameter("action") == "parse" -> ok("wotd.json", "application/json")
                    path.startsWith("/wiki/Special:RandomInCategory/") ->
                        MockResponse.Builder().code(302).addHeader("Location", "//en.wiktionary.org/wiki/serendipity").build()
                    path.startsWith("/api/rest_v1/page/definition/") -> {
                        val word = path.substringAfterLast('/')
                        runCatching { ok("def_$word.json", "application/json") }
                            .getOrElse { MockResponse.Builder().code(404).build() }
                    }
                    path.startsWith("/api/rest_v1/page/mobile-html/") -> {
                        val word = path.substringAfterLast('/')
                        runCatching { ok("mobilehtml_$word.html", "text/html") }
                            .getOrElse { MockResponse.Builder().code(404).build() }
                    }
                    path == "/w/rest.php/v1/search/title" -> ok("search_seren.json", "application/json")
                    path == "/w/rest.php/v1/search/page" -> MockResponse.Builder().code(200).body("""{"pages":[]}""").build()
                    else -> MockResponse.Builder().code(404).build()
                }
            }
        }
        start()
    }

    val url: HttpUrl get() = server.url("/")
}

class TestApp : StemmaApp() {
    override fun createContainer() = AppContainer(this, FakeWiktionary.url)
}

/**
 * Runs the real app (Compose UI, navigation, view models, storage) on the JVM against
 * [FakeWiktionary]. With `-Pscreenshots`, it also saves the Play Store screenshots.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = TestApp::class, sdk = [35], qualifiers = "w411dp-h731dp-420dpi")
@OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class)
class AppScreensTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<TestApp>()
    private val container get() = app.container

    private val screenshotDir = System.getProperty("stemma.screenshots.dir")

    private fun capture(name: String) {
        compose.waitForIdle()
        if (screenshotDir != null) captureScreenRoboImage(File(screenshotDir, "$name.png"))
    }

    /**
     * Waits for [condition], running queued main-thread work on each check. Compose's own
     * waitUntil does not drain Robolectric's main looper while an indeterminate progress
     * indicator is animating, so coroutine results posted to the main thread would never arrive.
     */
    private fun waitFor(condition: () -> Boolean) {
        compose.waitUntil(timeoutMillis = 15_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun waitForText(text: String) = waitFor {
        compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
    }

    /** Fills Saved and History with real Wiktionary definitions from the test fixtures. */
    private fun seedLibrary() = runBlocking {
        val words = listOf(
            "serendipity", "petrichor", "saudade", "sonder", "susurrus",
            "mellifluous", "quixotic", "ephemeral", "wanderlust", "halcyon",
        )
        val now = System.currentTimeMillis()
        val hour = 3_600_000L
        // Oldest first, so the first word ends up at the top of both lists.
        words.reversed().forEachIndexed { i, word ->
            val summary = container.api.definitionSummary(word, "English")
            val entry = EntryContent(word, word, 0, "<p>$word</p>", emptyList(), summary, now)
            container.library.save(entry)
            val age = if (i < 5) 30 * hour - i * hour else (words.size - i) * 10 * 60_000L
            container.library.recordView(entry, at = now - age)
        }
        listOf("defenestration", "halcyon", "seren").forEach { container.library.addRecentSearch(it) }
    }

    private fun launch(theme: ThemeChoice = ThemeChoice.LIGHT): ActivityScenario<MainActivity> {
        runBlocking { container.settings.setTheme(theme) }
        return ActivityScenario.launch(MainActivity::class.java)
    }

    @Test
    fun exploreShowsWordOfTheDayAndRandomWord() {
        seedLibrary()
        launch().use {
            waitForText("fabada")
            waitForText("serendipity")
            compose.onNodeWithText("A stew from Asturias", substring = true).assertExists()
            capture("1-explore")
        }
    }

    @Test
    fun exploreInDarkTheme() {
        seedLibrary()
        launch(ThemeChoice.DARK).use {
            waitForText("fabada")
            waitForText("unplanned")
            capture("6-explore-dark")
        }
    }

    @Test
    fun searchSuggestsTitlesAndOpensAnEntry() {
        launch().use {
            waitForText("Search Wiktionary")
            compose.onNodeWithText("Search Wiktionary").performClick()
            compose.onNode(hasText("Search Wiktionary")).performTextInput("seren")
            waitForText("serendipity")
            capture("3-search")
            compose.onNodeWithText("serendipity").performClick()
            waitFor { compose.onAllNodes(hasText("Search Wiktionary")).fetchSemanticsNodes().isNotEmpty() }
            waitFor { synchronized(FakeWiktionary.requests) { "/api/rest_v1/page/mobile-html/serendipity" in FakeWiktionary.requests } }
            waitFor { container.library.data.value.history.any { it.title == "serendipity" } }
            assertEquals("serendipity", container.library.data.value.recentSearches.first())
        }
    }

    @Test
    fun savedAndHistoryTabsListWords() {
        seedLibrary()
        launch().use {
            waitForText("Saved")
            compose.onNodeWithText("Saved").performClick()
            waitForText("petrichor")
            compose.onNodeWithText("distinctive scent", substring = true).assertExists()
            capture("4-saved")
            compose.onNodeWithText("History").performClick()
            waitForText("Today")
            capture("5-history")
        }
    }

    @Test
    fun entryScreenLoadsSavesAndShares() {
        launch().use {
            waitForText("serendipity")
            // The second "Read entry" button belongs to the random-word card (serendipity).
            compose.onAllNodesWithText("Read entry")[1].performScrollTo().performClick()
            waitFor { container.library.data.value.history.any { it.title == "serendipity" } }
            compose.onNodeWithContentDescription("Save").performClick()
            waitFor { container.library.data.value.saved.any { it.title == "serendipity" } }
            waitForText("available offline")
            val entry = runBlocking { container.library.offlineCopy("serendipity") }!!
            assertEquals("Noun", entry.summary.partOfSpeech)
            assertTrue(entry.languages.isNotEmpty())

            // Robolectric does not draw WebView content, so the store screenshot is completed by
            // rendering the same HTML in headless Chromium (see store/compose_entry_screenshot.py).
            if (screenshotDir != null) {
                waitFor {
                    compose.onAllNodes(hasText("available offline", substring = true)).fetchSemanticsNodes().isEmpty()
                }
                capture("2-entry")
                // The page the WebView was given, for rendering in a real browser engine.
                File(screenshotDir, "2-entry.html").writeText(
                    EntryHtml.render(entry, "wh-light", false, "English", false, null, 0),
                )
            }
        }
    }

    @Test
    fun entrySheetsAndFindInPageOpen() {
        launch().use {
            waitForText("serendipity")
            compose.onAllNodesWithText("Read entry")[1].performScrollTo().performClick()
            waitFor { container.library.data.value.history.any { it.title == "serendipity" } }
            compose.onNodeWithContentDescription("Contents").performClick()
            waitForText("Etymology")
            compose.onNodeWithText("Pronunciation").performClick()
            compose.onNodeWithContentDescription("Text size and theme").performClick()
            waitForText("Expand all languages")
            compose.onNodeWithContentDescription("Sepia theme").performClick()
            waitFor { runBlocking { container.settings.settings.first().theme } == ThemeChoice.SEPIA }
            compose.onNodeWithContentDescription("Larger text").performClick()
            waitFor { runBlocking { container.settings.settings.first().textZoom } == 110 }
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithContentDescription("Find in page").performClick()
            waitForText("Find in page")
            compose.onNodeWithContentDescription("Close find in page").performClick()
            compose.onNodeWithContentDescription("Save").assertExists()
        }
    }

    @Test
    fun settingsAndAboutOpen() {
        launch().use {
            waitForText("Search Wiktionary")
            compose.onNodeWithContentDescription("Settings").performClick()
            waitForText("Preferred language")
            compose.onNodeWithText("Preferred language").performClick()
            compose.onNodeWithText("French").performClick()
            waitFor { runBlocking { container.settings.settings.first().preferredLanguage } == "French" }
            compose.onNodeWithText("About Stemma").performScrollTo().performClick()
            waitForText("not made, endorsed or sponsored by the Wikimedia Foundation")
        }
    }

    @Test
    fun defineFromAnotherAppOpensTheEntryAndBackLeaves() {
        val define = Intent(Intent.ACTION_PROCESS_TEXT)
            .setClass(app, MainActivity::class.java)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_PROCESS_TEXT, "serendipity.")
        ActivityScenario.launch<MainActivity>(define).use { scenario ->
            waitFor { container.library.data.value.history.any { it.title == "serendipity" } }
            compose.onNodeWithContentDescription("Save").assertExists()
            // Explore was replaced, so Back returns to the app that asked.
            compose.onNodeWithContentDescription("Back").performClick()
            compose.waitForIdle()
            var finishing = false
            scenario.onActivity { finishing = it.isFinishing }
            assertTrue("Back should close the activity and return to the calling app", finishing)
        }
    }

    @Test
    fun sharedStyledTextIsLookedUp() {
        val share = Intent(Intent.ACTION_SEND)
            .setClass(app, MainActivity::class.java)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, SpannedString("fabada"))
        ActivityScenario.launch<MainActivity>(share).use {
            waitFor { container.library.data.value.history.any { it.title == "fabada" } }
        }
    }

    @Test
    fun relaunchFromRecentsShowsExploreInsteadOfReplayingTheLink() {
        val view = Intent(Intent.ACTION_VIEW, Uri.parse("https://en.wiktionary.org/wiki/fabada"))
            .setClass(app, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY)
        ActivityScenario.launch<MainActivity>(view).use {
            waitForText("Word of the day".uppercase())
            assertTrue(container.library.data.value.history.none { it.title == "fabada" })
        }
    }

    @Test
    fun webLinksOpenInTheBrowserRatherThanThisApp() {
        val pm = shadowOf(app.packageManager)
        val browser = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.example.browser"
                name = "com.example.browser.Main"
                applicationInfo = ApplicationInfo().apply { packageName = "com.example.browser" }
            }
        }
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com/")).addCategory(Intent.CATEGORY_BROWSABLE)
        pm.addResolveInfoForIntent(probe, browser)
        openInBrowser(app, "https://en.wiktionary.org/wiki/Category:English_nouns")
        val started = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, started.action)
        assertEquals("https://en.wiktionary.org/wiki/Category:English_nouns", started.dataString)
        assertEquals("com.example.browser", started.`package`)
    }

    @Test
    fun entryForMissingWordShowsNotFound() {
        launch().use {
            waitForText("Search Wiktionary")
            compose.onNodeWithText("Search Wiktionary").performClick()
            compose.onNode(hasText("Search Wiktionary")).performTextInput("Qwxzv")
            compose.waitForIdle()
            // Press the keyboard's search action.
            compose.onNode(hasText("Qwxzv")).performImeAction()
            waitForText("No entry for")
        }
    }

}
