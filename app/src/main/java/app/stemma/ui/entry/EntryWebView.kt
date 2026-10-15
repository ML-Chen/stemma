package app.stemma.ui.entry

import android.annotation.SuppressLint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.webkit.WebViewAssetLoader
import app.stemma.R

/** Holds the WebView and the HTML it currently shows, so recompositions do not reload the page. */
class EntryWebViewHolder {
    var webView: WebView? = null
    var loadedHtml: String? = null
}

// Lint's MissingOnRenderProcessGone check does not see the override in the anonymous client below.
@SuppressLint("SetJavaScriptEnabled", "MissingOnRenderProcessGone")
@Composable
fun EntryWebView(
    html: String,
    baseUrl: String,
    textZoom: Int,
    background: Color,
    textColor: Color,
    holder: EntryWebViewHolder,
    bridge: EntryJsBridge,
    onLinkNavigation: (String) -> Unit,
    onRendererGone: () -> Unit,
    onDispose: (scrollY: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    // Stop pronunciation audio and pause the page while the app is in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val webView = holder.webView ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    webView.evaluateJavascript("window.wh && wh.stopAudio()", null)
                    webView.onPause()
                }
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val webView = try {
                WebView(ctx)
            } catch (e: Exception) {
                // Thrown while the Android System WebView app is being updated or is disabled.
                null
            }
            webView?.apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = false
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.setSupportZoom(false)
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.mediaPlaybackRequiresUserGesture = true
                isVerticalScrollBarEnabled = true
                addJavascriptInterface(bridge, EntryJsBridge.NAME)
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? =
                        assetLoader.shouldInterceptRequest(request.url)

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        // The page never navigates itself; links are routed through the app.
                        onLinkNavigation(request.url.toString())
                        return true
                    }

                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        holder.loadedHtml = null
                        onRendererGone()
                        return true
                    }
                }
                holder.webView = this
            } ?: TextView(ctx).apply {
                setText(R.string.webview_unavailable)
                gravity = Gravity.CENTER
                val pad = (32 * resources.displayMetrics.density).toInt()
                setPadding(pad, pad, pad, pad)
                setTextColor(textColor.toArgb())
            }
        },
        update = { view: View ->
            view.setBackgroundColor(background.toArgb())
            if (view is WebView) {
                if (view.settings.textZoom != textZoom) view.settings.textZoom = textZoom
                if (holder.loadedHtml !== html) {
                    holder.loadedHtml = html
                    view.loadDataWithBaseURL(baseUrl, html, "text/html", "utf-8", null)
                }
            }
        },
        onRelease = { view: View ->
            if (view is WebView) {
                onDispose(view.scrollY)
                if (holder.webView === view) {
                    holder.webView = null
                    holder.loadedHtml = null
                }
                view.stopLoading()
                view.removeJavascriptInterface(EntryJsBridge.NAME)
                view.destroy()
            }
        },
    )
}
