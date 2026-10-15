package app.stemma.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.stemma.AppContainer
import app.stemma.R
import app.stemma.data.RateLimitedException
import app.stemma.data.Titles
import app.stemma.data.WordSummary
import app.stemma.ui.theme.WordRowStyle
import java.io.IOException
import java.net.UnknownHostException

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** A word with its part of speech and first definition, as used in Saved, History and Search. */
@Composable
fun WordListItem(
    title: String,
    summary: WordSummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = if (trailing != null) 4.dp else 20.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = WordRowStyle, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val line = supporting ?: summary?.oneLine().orEmpty()
            if (line.isNotBlank()) {
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.widthIn(max = 360.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (action != null) {
                Spacer(Modifier.height(20.dp))
                action()
            }
        }
    }
}

@Composable
fun ErrorState(error: Throwable, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    EmptyState(
        icon = Icons.Outlined.CloudOff,
        title = errorTitle(error),
        message = errorMessage(error),
        modifier = modifier,
        action = { Button(onClick = onRetry) { Text(stringResource(R.string.retry)) } },
    )
}

fun errorTitle(error: Throwable): String = when (error) {
    is UnknownHostException -> "You're offline"
    is RateLimitedException -> "Wiktionary is busy"
    is IOException -> "Couldn't reach Wiktionary"
    else -> "Something went wrong"
}

fun errorMessage(error: Throwable): String = when (error) {
    is UnknownHostException -> "Connect to the internet and try again. Saved words are available offline."
    is RateLimitedException -> "Wiktionary is receiving too many requests from your network. Wait a minute and try again."
    is IOException -> "Check your connection and try again."
    else -> error.message ?: "Please try again."
}

/**
 * Opens a web page in the user's browser (in a Custom Tab when the browser supports them).
 * The browser is chosen explicitly: if the user has set Stemma to open Wiktionary links,
 * an unaddressed link would otherwise come straight back into this app.
 */
fun openInBrowser(context: Context, url: String) {
    val uri = url.toUri()
    val customTabsBrowser = CustomTabsClient.getPackageName(context, null)
    if (customTabsBrowser != null && customTabsBrowser != context.packageName) {
        try {
            val tab = CustomTabsIntent.Builder().setShowTitle(true).build()
            tab.intent.setPackage(customTabsBrowser)
            tab.launchUrl(context, uri)
            return
        } catch (_: ActivityNotFoundException) {
        }
    }
    val view = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    defaultBrowser(context)?.let { view.setPackage(it) }
    try {
        context.startActivity(view)
    } catch (_: ActivityNotFoundException) {
    }
}

/** The package of the app that opens ordinary web links, or null if there is none other than this app. */
private fun defaultBrowser(context: Context): String? {
    val probe = Intent(Intent.ACTION_VIEW, "https://www.example.com/".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    val pm = context.packageManager
    val preferred = pm.resolveActivity(probe, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
    if (preferred != null && preferred != "android" && preferred != context.packageName) return preferred
    // No default browser has been chosen ("android" is the chooser), so take the first one installed.
    return pm.queryIntentActivities(probe, 0).map { it.activityInfo.packageName }.firstOrNull { it != context.packageName }
}

fun shareWord(context: Context, title: String) {
    val text = "$title — ${Titles.pageUrl(title)}"
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

