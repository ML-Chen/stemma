package app.stemma.ui.entry

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.stemma.AppContainer
import app.stemma.data.EntryContent
import app.stemma.data.EntryLoad
import app.stemma.data.SearchHit
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface EntryUiState {
    data object Loading : EntryUiState
    data class Loaded(val content: EntryContent, val offline: Boolean) : EntryUiState
    data class NotFound(val title: String, val suggestions: List<SearchHit>) : EntryUiState
    data class Failed(val error: Throwable) : EntryUiState
}

class EntryViewModel(
    private val container: AppContainer,
    val requestedTitle: String,
    val fragment: String?,
) : ViewModel() {
    private val _state = MutableStateFlow<EntryUiState>(EntryUiState.Loading)
    val state: StateFlow<EntryUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    val isSaved: StateFlow<Boolean> = combine(_state, container.library.data) { state, data ->
        val title = (state as? EntryUiState.Loaded)?.content?.title ?: requestedTitle
        data.saved.any { it.title == title }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Scroll position kept while the WebView is recreated (rotation, returning from another entry). */
    var scrollY: Int = 0

    private var loadJob: Job? = null

    init {
        load(forceRefresh = false)
    }

    fun load(forceRefresh: Boolean = true) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (_state.value !is EntryUiState.Loaded) _state.value = EntryUiState.Loading
            val language = container.settings.settings.first().preferredLanguage
            _state.value = when (val result = container.entries.load(requestedTitle, language, forceRefresh)) {
                is EntryLoad.Loaded -> EntryUiState.Loaded(result.content, result.offline)
                is EntryLoad.NotFound -> EntryUiState.NotFound(result.title, result.suggestions)
                is EntryLoad.Failed -> {
                    val current = _state.value
                    if (current is EntryUiState.Loaded) {
                        _messages.tryEmit("Couldn't refresh. Showing the copy you already have.")
                        current
                    } else {
                        EntryUiState.Failed(result.error)
                    }
                }
            }
        }
    }

    private var toggleJob: Job? = null

    fun toggleSaved() {
        val content = (_state.value as? EntryUiState.Loaded)?.content ?: return
        if (toggleJob?.isActive == true) return
        toggleJob = viewModelScope.launch {
            if (isSaved.value) {
                container.library.unsave(content.title)
                _messages.emit("Removed from Saved")
            } else {
                container.library.save(content)
                _messages.emit("Saved. “${content.displayTitle}” is now available offline.")
            }
        }
    }
}
