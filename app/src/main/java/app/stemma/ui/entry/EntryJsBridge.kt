package app.stemma.ui.entry

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface

/**
 * Exposed to entry.js as `window.StemmaBridge`. JavaScript calls arrive on a background
 * thread, so each call is forwarded to the main thread.
 */
class EntryJsBridge(
    private val onLink: (String) -> Unit,
    private val onExternal: (String) -> Unit,
    private val onShowContents: () -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun openLink(url: String) {
        main.post { onLink(url) }
    }

    @JavascriptInterface
    fun openExternal(url: String) {
        main.post { onExternal(url) }
    }

    @JavascriptInterface
    fun showContents() {
        main.post { onShowContents() }
    }

    companion object {
        const val NAME = "StemmaBridge"
    }
}
