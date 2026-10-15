package app.stemma.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.stemma.data.LibraryData
import app.stemma.data.SavedWord
import app.stemma.ui.components.EmptyState
import app.stemma.ui.components.LocalAppContainer
import app.stemma.ui.components.WordListItem
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private enum class SavedSort { RECENT, ALPHABETICAL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(onOpenEntry: (String) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val library by container.library.data.collectAsStateWithLifecycle(LibraryData())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var filter by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(SavedSort.RECENT) }
    var sortMenu by remember { mutableStateOf(false) }

    fun remove(item: SavedWord) {
        scope.launch {
            val offlineCopy = container.library.offlineCopy(item.title)
            container.library.unsave(item.title)
            val result = snackbar.showSnackbar("Removed “${item.title}”", actionLabel = "Undo", withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) container.library.restore(item, offlineCopy)
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Saved") },
                actions = {
                    if (library.saved.isNotEmpty()) {
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, contentDescription = "Sort") }
                            DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                                DropdownMenuItem(text = { Text("Recently saved") }, onClick = { sort = SavedSort.RECENT; sortMenu = false })
                                DropdownMenuItem(text = { Text("A to Z") }, onClick = { sort = SavedSort.ALPHABETICAL; sortMenu = false })
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (library.saved.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.BookmarkBorder,
                title = "No saved words yet",
                message = "Tap the bookmark on any entry to save it. Saved words can be read without an internet connection.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        val shown = library.saved
            .filter { filter.isBlank() || it.title.contains(filter.trim(), ignoreCase = true) || it.summary.definition.contains(filter.trim(), ignoreCase = true) }
            .let { list -> if (sort == SavedSort.ALPHABETICAL) list.sortedBy { it.title.lowercase() } else list }
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            FilterField(filter, onChange = { filter = it }, placeholder = "Filter ${library.saved.size} saved words")
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.title }) { item ->
                    SwipeToDelete(onDelete = { remove(item) }) {
                        WordListItem(
                            title = item.title,
                            summary = item.summary,
                            onClick = { onOpenEntry(item.title) },
                            trailing = {
                                IconButton(onClick = { remove(item) }) {
                                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Remove “${item.title}” from Saved")
                                }
                            },
                        )
                    }
                    HorizontalDivider(Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(onOpenEntry: (String) -> Unit, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val library by container.library.data.collectAsStateWithLifecycle(LibraryData())
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    if (library.history.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear history")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (library.history.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = "No history yet",
                message = "Words you look up appear here, newest first. History stays on this device.",
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        val shown = library.history.filter { filter.isBlank() || it.title.contains(filter.trim(), ignoreCase = true) }
        val grouped = shown.groupBy { dayLabel(it.viewedAt) }
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            FilterField(filter, onChange = { filter = it }, placeholder = "Filter history")
            LazyColumn(Modifier.fillMaxSize()) {
                grouped.forEach { (day, items) ->
                    item(key = "header-$day") {
                        Text(
                            day,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(items, key = { "h-" + it.title }) { item ->
                        SwipeToDelete(onDelete = { scope.launch { container.library.removeHistory(item.title) } }) {
                            WordListItem(title = item.title, summary = item.summary, onClick = { onOpenEntry(item.title) })
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("This removes every word from your reading history. Saved words are not affected.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    scope.launch { container.library.clearHistory() }
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun FilterField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (value.isNotEmpty()) {
                IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, contentDescription = "Clear filter") }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToDelete(onDelete: () -> Unit, content: @Composable () -> Unit) {
    val state = rememberSwipeToDismissBoxState()
    SwipeToDismissBox(
        state = state,
        enableDismissFromStartToEnd = false,
        onDismiss = { value -> if (value == SwipeToDismissBoxValue.EndToStart) onDelete() },
        backgroundContent = {
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 24.dp),
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        },
    ) {
        Box(Modifier.background(MaterialTheme.colorScheme.background)) { content() }
    }
}

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private fun dayLabel(millis: Long): String {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when (day) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> day.format(dateFormat)
    }
}
