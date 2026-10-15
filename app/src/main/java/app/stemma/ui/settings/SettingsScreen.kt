package app.stemma.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stemma.BuildConfig
import app.stemma.data.SettingsStore
import app.stemma.ui.components.AppearanceControls
import app.stemma.ui.components.LocalAppContainer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onAbout: () -> Unit) {
    val container = LocalAppContainer.current
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var pickLanguage by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, suspend () -> Unit>?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val prefs = settings ?: return@Scaffold
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SectionTitle("Reading")
            AppearanceControls(prefs, container.settings)
            SettingRow(
                title = "Preferred language",
                subtitle = "${prefs.preferredLanguage}. This section opens first on every entry, and random words come from this language.",
                onClick = { pickLanguage = true },
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("Your data")
            SettingRow("Clear reading history", "Words you have looked up") {
                confirm = "Clear reading history?" to { container.library.clearHistory() }
            }
            SettingRow("Clear recent searches", "Shown when you open search") {
                confirm = "Clear recent searches?" to { container.library.clearRecentSearches() }
            }
            Text(
                "Saved words, history and settings are kept on this device (and in Android backup, if it is on). " +
                    "The app has no account and sends no analytics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionTitle("About")
            SettingRow("About Stemma", "Version ${BuildConfig.VERSION_NAME}, licences, privacy", onClick = onAbout)
        }
    }

    if (pickLanguage) {
        val current = settings?.preferredLanguage
        AlertDialog(
            onDismissRequest = { pickLanguage = false },
            title = { Text("Preferred language") },
            text = {
                LazyColumn {
                    items(SettingsStore.LANGUAGES) { language ->
                        ListItem(
                            headlineContent = { Text(language) },
                            leadingContent = { RadioButton(selected = language == current, onClick = null) },
                            colors = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent),
                            modifier = Modifier.clickable {
                                pickLanguage = false
                                scope.launch { container.settings.setPreferredLanguage(language) }
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickLanguage = false }) { Text("Close") } },
        )
    }

    confirm?.let { (question, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(question) },
            text = { Text("This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        action()
                        snackbar.showSnackbar("Done")
                    }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 4.dp),
    )
}
