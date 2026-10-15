package app.stemma.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import app.stemma.BuildConfig
import app.stemma.R
import app.stemma.ui.theme.Serif

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .widthIn(max = 640.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF21195A)),
            ) {
                Image(painter = painterResource(R.drawable.stemma_mark), contentDescription = null, modifier = Modifier.size(64.dp))
            }
            Text("Stemma", fontFamily = Serif, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 12.dp))
            Text(
                "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Heading("Content")
            Body(
                "Stemma is a reader for Wiktionary, the free multilingual dictionary written by volunteers. " +
                    "Every definition, pronunciation, etymology and translation shown in the app comes from ",
                link("English Wiktionary", "https://en.wiktionary.org/"),
                ". Wiktionary text is available under the ",
                link("Creative Commons Attribution-ShareAlike 4.0 License", "https://creativecommons.org/licenses/by-sa/4.0/"),
                "; each entry links to its page and its list of contributors. Images and audio come from Wikimedia Commons under their own licences.",
            )
            Body(
                "Stemma is an independent app. It is not made, endorsed or sponsored by the Wikimedia Foundation. " +
                    "Wiktionary is a trademark of the Wikimedia Foundation.",
            )

            Heading("Privacy")
            Body(
                "Stemma has no account, no ads and no analytics, and it does not collect personal information. " +
                    "To show a word, the app requests it from the Wikimedia Foundation's servers (en.wiktionary.org " +
                    "and upload.wikimedia.org). Those requests include the word and, like every web request, your IP " +
                    "address; the Wikimedia Foundation handles them under its ",
                link("privacy policy", "https://foundation.wikimedia.org/wiki/Policy:Privacy_policy"),
                ". Saved words, offline copies, history, recent searches and settings are stored on this device, " +
                    "and the developer never receives them. If Android backup is turned on, Android may include saved " +
                    "words, history and settings in your Google account's backup and restore them when you reinstall. " +
                    "You can clear history and searches in Settings.",
            )

            Heading("Open-source software")
            Body(
                "Android Jetpack, Jetpack Compose, Kotlin, kotlinx.coroutines, kotlinx.serialization and OkHttp " +
                    "are used under the ",
                link("Apache License 2.0", "https://www.apache.org/licenses/LICENSE-2.0"),
                ". jsoup is used under the ",
                link("MIT License", "https://jsoup.org/license"),
                ".",
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp, bottom = 6.dp))
}

private data class Link(val label: String, val url: String)

private fun link(label: String, url: String) = Link(label, url)

@Composable
private fun Body(vararg parts: Any) {
    val linkColor = MaterialTheme.colorScheme.primary
    val text = buildAnnotatedString {
        for (part in parts) {
            when (part) {
                is Link -> withLink(LinkAnnotation.Url(part.url, TextLinkStyles(SpanStyle(color = linkColor)))) { append(part.label) }
                else -> append(part.toString())
            }
        }
    }
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 10.dp))
}
