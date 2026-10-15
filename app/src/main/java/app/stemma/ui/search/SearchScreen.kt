package app.stemma.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stemma.AppContainer
import app.stemma.data.LibraryData
import app.stemma.ui.components.LocalAppContainer
import app.stemma.ui.components.errorTitle
import app.stemma.ui.theme.WordRowStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Search results for [query] (the text they were requested for). */
data class SearchUiState(
    val query: String = "",
    val suggestions: List<String> = emptyList(),
    val loading: Boolean = false,
    val error: Throwable? = null,
)

@OptIn(FlowPreview::class)
class SearchViewModel(private val container: AppContainer, initialQuery: String) : ViewModel() {
    /**
     * The search box's text. It is a TextFieldState, which the text field edits directly and
     * synchronously; routing each keystroke through a StateFlow delays it by a frame and makes typing lag.
     */
    val input = TextFieldState(initialQuery, TextRange(initialQuery.length))

    private val _state = MutableStateFlow(SearchUiState(query = initialQuery))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            snapshotFlow { input.text.toString().trim() }
                .distinctUntilChanged()
                .debounce { if (it.isEmpty()) 0L else 200L }
                .collectLatest { q ->
                    if (q.isEmpty()) {
                        _state.value = SearchUiState()
                        return@collectLatest
                    }
                    _state.value = _state.value.copy(loading = true, error = null)
                    _state.value = try {
                        val titles = container.api.searchTitles(q)
                        // The user has probably typed a whole word: start downloading it now.
                        titles.firstOrNull()?.takeIf { it.equals(q, ignoreCase = true) }?.let { container.entries.prefetch(it) }
                        SearchUiState(query = q, suggestions = titles)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _state.value.copy(loading = false, error = e)
                    }
                }
        }
    }

    /**
     * The title to open when the keyboard's search key is pressed. Prefers a suggestion that
     * matches exactly, then one that matches ignoring case, then the text as typed.
     */
    fun resolveSubmission(): String? {
        val q = input.text.toString().trim()
        if (q.isEmpty()) return null
        val suggestions = _state.value.suggestions.takeIf { _state.value.query == q }.orEmpty()
        return suggestions.firstOrNull { it == q }
            ?: suggestions.firstOrNull { it.equals(q, ignoreCase = true) && it.first().isLowerCase() }
            ?: suggestions.firstOrNull { it.equals(q, ignoreCase = true) }
            ?: q
    }

    fun clear() = input.clearText()

    fun rememberSearch(query: String) {
        viewModelScope.launch { container.library.addRecentSearch(query) }
    }

    fun forget(query: String) {
        viewModelScope.launch { container.library.removeRecentSearch(query) }
    }

    fun clearRecent() {
        viewModelScope.launch { container.library.clearRecentSearches() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(initialQuery: String, onBack: () -> Unit, onOpenEntry: (String) -> Unit) {
    val container = LocalAppContainer.current
    val vm: SearchViewModel = viewModel { SearchViewModel(container, initialQuery) }
    val state by vm.state.collectAsStateWithLifecycle()
    val library by container.library.data.collectAsStateWithLifecycle(LibraryData())
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Read through derivedStateOf so the result list doesn't recompose on every keystroke.
    val inputEmpty by remember { derivedStateOf { vm.input.text.isBlank() } }

    LaunchedEffect(Unit) { focus.requestFocus() }

    fun open(title: String) {
        keyboard?.hide()
        vm.rememberSearch(title)
        onOpenEntry(title)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    TextField(
                        state = vm.input,
                        placeholder = { Text("Search Wiktionary") },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Search,
                        ),
                        onKeyboardAction = { vm.resolveSubmission()?.let(::open) },
                        trailingIcon = {
                            if (!inputEmpty) {
                                IconButton(onClick = vm::clear) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            // Fixed height, so the list doesn't jump when loading starts and stops.
            Box(Modifier.fillMaxWidth().height(3.dp), contentAlignment = Alignment.BottomCenter) {
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().height(3.dp)) else HorizontalDivider()
            }
            when {
                inputEmpty -> RecentSearches(
                    recent = library.recentSearches,
                    onOpen = ::open,
                    onForget = vm::forget,
                    onClear = vm::clearRecent,
                )
                state.error != null && state.suggestions.isEmpty() -> Text(
                    errorTitle(state.error!!) + ". Press search to look up the word anyway.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.suggestions, key = { it }) { title ->
                        ListItem(
                            headlineContent = { Text(title, style = WordRowStyle) },
                            leadingContent = { Icon(Icons.Outlined.Search, contentDescription = null) },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                            modifier = Modifier.clickableRow { open(title) },
                        )
                    }
                    if (!state.loading && state.suggestions.isEmpty() && state.query.isNotEmpty()) {
                        item {
                            Text(
                                "No titles start with “${state.query}”. Press search to look it up anyway.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSearches(recent: List<String>, onOpen: (String) -> Unit, onForget: (String) -> Unit, onClear: () -> Unit) {
    if (recent.isEmpty()) {
        Text(
            "Type a word in any language. Wiktionary has entries for millions of words in thousands of languages.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(20.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
            ) {
                Text(
                    "Recent searches",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
        items(recent, key = { it }) { query ->
            ListItem(
                headlineContent = { Text(query, style = WordRowStyle) },
                leadingContent = { Icon(Icons.Outlined.History, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { onForget(query) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove “$query” from recent searches")
                    }
                },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                modifier = Modifier.clickableRow { onOpen(query) },
            )
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
