package app.stemma

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Looper
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stemma.data.Settings
import app.stemma.data.Titles
import app.stemma.ui.ExternalRequest
import app.stemma.ui.StemmaNavigation
import app.stemma.ui.components.LocalAppContainer
import app.stemma.ui.components.openInBrowser
import app.stemma.ui.theme.StemmaTheme
import app.stemma.ui.theme.resolve
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

class MainActivity : ComponentActivity() {
    private val requests = Channel<ExternalRequest>(Channel.BUFFERED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as StemmaApp).container
        if (savedInstanceState == null) handle(intent, startedForIt = true)
        warmUpWebView()

        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = Settings())
            val theme = settings.theme.resolve()
            LaunchedEffect(theme.isDark) {
                val style = if (theme.isDark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            CompositionLocalProvider(LocalAppContainer provides container) {
                StemmaTheme(theme) {
                    StemmaNavigation(requests.receiveAsFlow())
                }
            }
        }
    }

    /**
     * The first WebView in a process has to load Android's web engine, which takes a noticeable
     * moment. Doing that once the first screen is idle makes the first entry open faster.
     */
    private fun warmUpWebView() {
        window.decorView.post {
            Looper.myQueue().addIdleHandler {
                try {
                    WebView(this).destroy()
                } catch (_: Exception) {
                    // Android System WebView is missing or updating; the entry screen reports that.
                }
                false
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent, startedForIt = false)
    }

    /**
     * Turns "Define" text selections, shared text and Wiktionary links into navigation requests.
     * [startedForIt] is true when this activity was created to handle [intent].
     */
    private fun handle(intent: Intent?, startedForIt: Boolean) {
        if (intent == null) return
        // Reopening the app from Recents redelivers the intent that first started it; ignore it then.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val request = when (intent.action) {
            Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.let(::requestForText)
            Intent.ACTION_SEND -> intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let(::requestForText)
            Intent.ACTION_VIEW -> intent.dataString?.let { url ->
                when (val target = Titles.classify(url)) {
                    is Titles.LinkTarget.Entry -> ExternalRequest.OpenEntry(target.title, target.fragment, replaceHome = true)
                    is Titles.LinkTarget.External -> {
                        // A Wiktionary page the app doesn't show (a category, a discussion): hand it to the browser.
                        openInBrowser(this, url)
                        if (startedForIt) finish()
                        null
                    }
                }
            }
            ACTION_SEARCH -> ExternalRequest.Search("", replaceHome = false)
            else -> null
        } ?: return
        requests.trySend(request)
    }

    private fun requestForText(raw: String): ExternalRequest? {
        val text = raw.trim().trim('"', '\'', '“', '”', '‘', '’', '.', ',', ';', ':', '!', '?', '(', ')')
        if (text.isEmpty()) return null
        if (text.startsWith("http://") || text.startsWith("https://")) {
            return (Titles.classify(text) as? Titles.LinkTarget.Entry)?.let {
                ExternalRequest.OpenEntry(it.title, it.fragment, replaceHome = true)
            }
        }
        // A short selection is looked up directly; anything longer goes to search to pick a word.
        return if (text.length <= 60 && text.count { it.isWhitespace() } <= 3) {
            ExternalRequest.OpenEntry(Titles.normalize(text), null, replaceHome = true)
        } else {
            ExternalRequest.Search(text.take(100), replaceHome = true)
        }
    }

    companion object {
        const val ACTION_SEARCH = "app.stemma.action.SEARCH"
    }
}
