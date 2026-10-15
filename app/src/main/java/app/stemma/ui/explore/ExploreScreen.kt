package app.stemma.ui.explore

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stemma.AppContainer
import app.stemma.R
import app.stemma.data.HistoryItem
import app.stemma.data.LibraryData
import app.stemma.data.WordCard
import app.stemma.ui.components.LocalAppContainer
import app.stemma.ui.components.WordListItem
import app.stemma.ui.components.errorTitle
import app.stemma.ui.theme.Serif
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** A card that is loading, loaded, or failed. */
sealed interface CardState {
    data object Loading : CardState
    data class Ready(val card: WordCard) : CardState
    data class Failed(val error: Throwable) : CardState
}

data class ExploreUiState(
    val wordOfTheDay: CardState = CardState.Loading,
    val random: CardState = CardState.Loading,
    val refreshing: Boolean = false,
)

class ExploreViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ExploreUiState())
    val state: StateFlow<ExploreUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.explore.cachedWordOfTheDay()?.let { cached ->
                if (_state.value.wordOfTheDay is CardState.Loading) {
                    _state.value = _state.value.copy(wordOfTheDay = CardState.Ready(cached))
                }
            }
        }
        refresh(force = false)
    }

    fun refresh(force: Boolean = true) {
        viewModelScope.launch {
            _state.value = _state.value.copy(refreshing = force)
            val wotd = async { loadWordOfTheDay(force) }
            val random = async { loadRandom() }
            _state.value = _state.value.copy(wordOfTheDay = wotd.await(), random = random.await(), refreshing = false)
            prefetchCards()
        }
    }

    /** These two words are the likeliest next taps, so their entries are downloaded in advance. */
    private fun prefetchCards() {
        listOf(_state.value.wordOfTheDay, _state.value.random)
            .filterIsInstance<CardState.Ready>()
            .forEach { container.entries.prefetch(it.card.title) }
    }

    fun shuffle() {
        viewModelScope.launch {
            _state.value = _state.value.copy(random = CardState.Loading)
            _state.value = _state.value.copy(random = loadRandom())
            (_state.value.random as? CardState.Ready)?.let { container.entries.prefetch(it.card.title) }
        }
    }

    private suspend fun loadWordOfTheDay(force: Boolean): CardState = try {
        CardState.Ready(container.explore.wordOfTheDay(force))
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        (_state.value.wordOfTheDay as? CardState.Ready) ?: CardState.Failed(e)
    }

    private suspend fun loadRandom(): CardState = try {
        val language = container.settings.settings.first().preferredLanguage
        CardState.Ready(container.explore.randomWord(language))
    } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        CardState.Failed(e)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onSearch: () -> Unit,
    onOpenEntry: (title: String, fragment: String?) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val vm: ExploreViewModel = viewModel { ExploreViewModel(container) }
    val state by vm.state.collectAsStateWithLifecycle()
    val library by container.library.data.collectAsStateWithLifecycle(LibraryData())

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.stemma_mark),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                        )
                        Spacer(Modifier.size(10.dp))
                        Text("Stemma", fontFamily = Serif, fontSize = 26.sp)
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { vm.refresh(force = true) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            ) {
                item { SearchPill(onSearch) }
                item {
                    WordCardView(
                        label = "Word of the day",
                        state = state.wordOfTheDay,
                        dateLabel = (state.wordOfTheDay as? CardState.Ready)?.card?.dateLabel.orEmpty(),
                        onOpen = { onOpenEntry(it.title, it.fragment) },
                        onRetry = { vm.refresh(force = true) },
                        featured = true,
                    )
                }
                item {
                    WordCardView(
                        label = "Random word",
                        state = state.random,
                        dateLabel = "",
                        onOpen = { onOpenEntry(it.title, null) },
                        onRetry = vm::shuffle,
                        featured = false,
                        extraAction = {
                            TextButton(onClick = vm::shuffle) {
                                Icon(Icons.Outlined.Casino, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(6.dp))
                                Text("Another")
                            }
                        },
                    )
                }
                if (library.history.isNotEmpty()) {
                    item { ContinueReading(library.history.take(3), onOpen = { onOpenEntry(it, null) }, onMore = onOpenHistory) }
                }
                item {
                    Text(
                        "Definitions come from Wiktionary, the free dictionary written by volunteers. " +
                            "Text is available under the Creative Commons Attribution-ShareAlike 4.0 License.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().height(52.dp).clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp)) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(12.dp))
            Text("Search Wiktionary", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun WordCardView(
    label: String,
    state: CardState,
    dateLabel: String,
    onOpen: (WordCard) -> Unit,
    onRetry: () -> Unit,
    featured: Boolean,
    extraAction: (@Composable () -> Unit)? = null,
) {
    val ready = state as? CardState.Ready
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (featured) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth().then(if (ready != null) Modifier.clickable { onOpen(ready.card) } else Modifier),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (featured) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (dateLabel.isNotEmpty()) {
                    Text(dateLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(8.dp))
                }
            }
            when (state) {
                CardState.Loading -> Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(28.dp))
                }
                is CardState.Failed -> Column(Modifier.padding(vertical = 12.dp)) {
                    Text(errorTitle(state.error), style = MaterialTheme.typography.bodyLarge)
                    TextButton(onClick = onRetry, modifier = Modifier.padding(top = 4.dp)) { Text("Try again") }
                }
                is CardState.Ready -> {
                    val card = state.card
                    Text(
                        card.title,
                        fontFamily = Serif,
                        fontSize = if (featured) 38.sp else 30.sp,
                        lineHeight = if (featured) 44.sp else 36.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    val pos = card.summary.partOfSpeech
                    if (pos.isNotBlank()) {
                        Text(
                            pos.lowercase(),
                            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    if (card.summary.definition.isNotBlank()) {
                        Text(
                            card.summary.definition,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp).widthIn(max = 560.dp),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        extraAction?.invoke()
                        Spacer(Modifier.weight(1f))
                        FilledTonalButton(onClick = { onOpen(card) }) {
                            Text("Read entry")
                            Spacer(Modifier.size(6.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueReading(items: List<HistoryItem>, onOpen: (String) -> Unit, onMore: () -> Unit) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp)) {
            Text(
                "RECENTLY VIEWED",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onMore) { Text("History") }
        }
        for (item in items) {
            WordListItem(title = item.title, summary = item.summary, onClick = { onOpen(item.title) })
        }
        Spacer(Modifier.height(8.dp))
    }
}
