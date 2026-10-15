package app.stemma.ui.entry

import android.content.ClipData
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.Toc
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FindInPage
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.stemma.data.EntryContent
import app.stemma.data.SearchHit
import app.stemma.data.Settings
import app.stemma.data.Titles
import app.stemma.ui.components.AppearanceControls
import app.stemma.ui.components.EmptyState
import app.stemma.ui.components.ErrorState
import app.stemma.ui.components.LocalAppContainer
import app.stemma.ui.components.WordListItem
import app.stemma.ui.components.openInBrowser
import app.stemma.ui.components.shareWord
import app.stemma.ui.theme.LocalReadingTheme
import app.stemma.ui.theme.Serif
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

private enum class Sheet { NONE, CONTENTS, APPEARANCE }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryScreen(
    title: String,
    fragment: String?,
    onBack: () -> Unit,
    onOpenEntry: (title: String, fragment: String?) -> Unit,
    onSearch: (query: String) -> Unit,
) {
    val container = LocalAppContainer.current
    val vm: EntryViewModel = viewModel { EntryViewModel(container, title, fragment) }
    val state by vm.state.collectAsStateWithLifecycle()
    val saved by vm.isSaved.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val holder = remember { EntryWebViewHolder() }
    var sheet by remember { mutableStateOf(Sheet.NONE) }
    var findMode by remember { mutableStateOf(false) }

    LaunchedEffect(vm) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val loaded = state as? EntryUiState.Loaded
    val content = loaded?.content

    fun handleLink(url: String) {
        when (val target = Titles.classify(url)) {
            is Titles.LinkTarget.Entry -> {
                if (content != null && target.title == content.title && target.fragment != null) {
                    holder.webView?.jump(target.fragment)
                } else {
                    onOpenEntry(target.title, target.fragment)
                }
            }
            is Titles.LinkTarget.External -> openInBrowser(context, url)
        }
    }

    BackHandler(enabled = findMode) {
        findMode = false
        holder.webView?.clearMatches()
    }

    Scaffold(
        topBar = {
            EntryTopBar(
                onBack = onBack,
                onSearch = { onSearch("") },
                pageTitle = content?.title ?: title,
                onRefresh = { vm.load(forceRefresh = true) },
            )
        },
        bottomBar = {
            if (findMode) {
                FindInPageBar(holder = holder, onClose = { findMode = false })
            } else {
                EntryBottomBar(
                    enabled = content != null,
                    saved = saved,
                    onSave = vm::toggleSaved,
                    onContents = { sheet = Sheet.CONTENTS },
                    onFind = { findMode = true },
                    onAppearance = { sheet = Sheet.APPEARANCE },
                    onShare = { content?.let { shareWord(context, it.title) } },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                EntryUiState.Loading -> LoadingEntry(title)
                is EntryUiState.Failed -> ErrorState(s.error, onRetry = { vm.load(forceRefresh = true) })
                is EntryUiState.NotFound -> NotFoundContent(
                    title = s.title,
                    suggestions = s.suggestions,
                    onOpen = { onOpenEntry(it, null) },
                    onSearch = { onSearch(s.title) },
                )
                is EntryUiState.Loaded -> {
                    val prefs = settings ?: return@Box
                    Column(Modifier.fillMaxSize()) {
                        if (s.offline) OfflineBanner(onRetry = { vm.load(forceRefresh = true) })
                        EntryPage(
                            content = s.content,
                            settings = prefs,
                            fragment = vm.fragment,
                            vm = vm,
                            holder = holder,
                            onLink = ::handleLink,
                            onExternal = { openInBrowser(context, it) },
                            onShowContents = { sheet = Sheet.CONTENTS },
                        )
                    }
                }
            }
        }
    }

    when (sheet) {
        Sheet.CONTENTS -> if (content != null) {
            ContentsSheet(
                content = content,
                onDismiss = { sheet = Sheet.NONE },
                onJump = { anchor ->
                    sheet = Sheet.NONE
                    holder.webView?.jump(anchor)
                },
            )
        }
        Sheet.APPEARANCE -> settings?.let { prefs ->
            ModalBottomSheet(onDismissRequest = { sheet = Sheet.NONE }) {
                AppearanceControls(prefs, container.settings, Modifier.padding(bottom = 24.dp))
            }
        }
        Sheet.NONE -> Unit
    }
}

@Composable
private fun EntryPage(
    content: EntryContent,
    settings: Settings,
    fragment: String?,
    vm: EntryViewModel,
    holder: EntryWebViewHolder,
    onLink: (String) -> Unit,
    onExternal: (String) -> Unit,
    onShowContents: () -> Unit,
) {
    val theme = LocalReadingTheme.current
    var rendererGeneration by remember { mutableIntStateOf(0) }
    // Rebuilt when the content, theme or language preferences change, on a background thread
    // (long pages are several megabytes). The reading position is carried over, so changing the
    // theme does not jump back to the top. The previous page stays visible until the new one is ready.
    val html by produceState<String?>(
        initialValue = null,
        content, theme, settings.preferredLanguage, settings.expandAllLanguages, rendererGeneration,
    ) {
        val scrollY = holder.webView?.scrollY ?: vm.scrollY
        value = withContext(Dispatchers.Default) {
            EntryHtml.render(
                content = content,
                themeClass = theme.cssClass,
                darkTheme = theme.isDark,
                preferredLanguage = settings.preferredLanguage,
                expandAll = settings.expandAllLanguages,
                fragment = fragment,
                restoreScrollY = scrollY,
            )
        }
    }
    val currentOnLink by rememberUpdatedState(onLink)
    val currentOnExternal by rememberUpdatedState(onExternal)
    val currentOnShowContents by rememberUpdatedState(onShowContents)
    val bridge = remember {
        EntryJsBridge(
            onLink = { currentOnLink(it) },
            onExternal = { currentOnExternal(it) },
            onShowContents = { currentOnShowContents() },
        )
    }
    DisposableEffect(Unit) {
        onDispose { holder.webView?.let { vm.scrollY = it.scrollY } }
    }
    if (rendererGeneration > MAX_RENDERER_RESTARTS) {
        // The page keeps crashing the WebView (usually out of memory); stop reloading it.
        EmptyState(
            icon = Icons.Outlined.ErrorOutline,
            title = "This entry is too large to show",
            message = "Your phone ran out of memory while displaying this page. You can open it on Wiktionary instead.",
        )
        return
    }
    val page = html ?: return
    key(rendererGeneration) {
        EntryWebView(
            html = page,
            baseUrl = Titles.pageUrl(content.title),
            textZoom = settings.textZoom,
            background = MaterialTheme.colorScheme.background,
            textColor = MaterialTheme.colorScheme.onBackground,
            holder = holder,
            bridge = bridge,
            onLinkNavigation = onLink,
            onRendererGone = { rendererGeneration++ },
            onDispose = { vm.scrollY = it },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

private const val MAX_RENDERER_RESTARTS = 2

private fun WebView.jump(anchor: String) {
    val quoted = Json.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(anchor))
    evaluateJavascript("window.wh && wh.jump($quoted)", null)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryTopBar(onBack: () -> Unit, onSearch: () -> Unit, pageTitle: String, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        title = {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onSearch),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
                    Icon(Icons.Outlined.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(10.dp))
                    Text(
                        "Search Wiktionary",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        },
        actions = {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "More options") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Refresh") },
                    leadingIcon = { Icon(Icons.Outlined.Refresh, null) },
                    onClick = { menu = false; onRefresh() },
                )
                DropdownMenuItem(
                    text = { Text("Open on Wiktionary") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) },
                    onClick = { menu = false; openInBrowser(context, Titles.pageUrl(pageTitle)) },
                )
                DropdownMenuItem(
                    text = { Text("Copy link") },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                    onClick = {
                        menu = false
                        scope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(pageTitle, Titles.pageUrl(pageTitle))))
                        }
                    },
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun EntryBottomBar(
    enabled: Boolean,
    saved: Boolean,
    onSave: () -> Unit,
    onContents: () -> Unit,
    onFind: () -> Unit,
    onAppearance: () -> Unit,
    onShare: () -> Unit,
) {
    BottomAppBar(containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            IconButton(onClick = onSave, enabled = enabled) {
                Icon(
                    if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                    contentDescription = if (saved) "Remove from Saved" else "Save",
                    tint = if (saved) MaterialTheme.colorScheme.primary else iconTint(enabled),
                )
            }
            IconButton(onClick = onContents, enabled = enabled) {
                Icon(Icons.AutoMirrored.Outlined.Toc, contentDescription = "Contents")
            }
            IconButton(onClick = onFind, enabled = enabled) {
                Icon(Icons.Outlined.FindInPage, contentDescription = "Find in page")
            }
            IconButton(onClick = onAppearance) {
                Icon(Icons.Outlined.FormatSize, contentDescription = "Text size and theme")
            }
            IconButton(onClick = onShare, enabled = enabled) {
                Icon(Icons.Outlined.Share, contentDescription = "Share")
            }
        }
    }
}

@Composable
private fun iconTint(enabled: Boolean): Color =
    if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

@Composable
private fun FindInPageBar(holder: EntryWebViewHolder, onClose: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var ordinal by remember { mutableIntStateOf(0) }
    var count by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }

    DisposableEffect(holder.webView) {
        val webView = holder.webView
        webView?.setFindListener { active, matches, _ ->
            ordinal = active
            count = matches
        }
        onDispose {
            webView?.setFindListener(null)
            webView?.clearMatches()
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            TextField(
                value = query,
                onValueChange = {
                    query = it
                    if (it.isEmpty()) {
                        holder.webView?.clearMatches()
                        count = 0
                    } else {
                        holder.webView?.findAllAsync(it)
                    }
                },
                placeholder = { Text("Find in page") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { holder.webView?.findNext(true) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            if (query.isNotEmpty()) {
                Text(
                    if (count == 0) "0/0" else "${ordinal + 1}/$count",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { holder.webView?.findNext(false) }, enabled = count > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = { holder.webView?.findNext(true) }, enabled = count > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close find in page") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContentsSheet(content: EntryContent, onDismiss: () -> Unit, onJump: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "Contents",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, bottom = 8.dp),
        )
        if (content.languages.isEmpty()) {
            Text(
                "This page has no sections.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp),
            )
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            for (language in content.languages) {
                item(key = language.blockId) {
                    Text(
                        language.name,
                        fontFamily = Serif,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(language.anchor) }
                            .padding(horizontal = 24.dp, vertical = 10.dp),
                    )
                }
                items(language.headings, key = { language.blockId + "/" + it.anchor }) { heading ->
                    Text(
                        heading.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (heading.level <= 3) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onJump(heading.anchor) }
                            .padding(start = (24 + (heading.level - 3).coerceAtLeast(0) * 16 + 12).dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
                    )
                }
                item(key = language.blockId + "/divider") {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp, horizontal = 24.dp))
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Shown while an entry loads: the word itself straight away, and placeholder lines for the rest. */
@Composable
private fun LoadingEntry(title: String) {
    val pulse = rememberInfiniteTransition(label = "loading")
    val alpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "alpha",
    )
    val bar = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = alpha)
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
        Text(
            title,
            fontFamily = Serif,
            fontSize = 34.sp,
            lineHeight = 40.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 18.dp),
        )
        for (width in listOf(0.45f, 1f, 0.92f, 0.97f, 0.6f, 0f, 0.35f, 1f, 0.85f, 0.7f)) {
            if (width == 0f) {
                Spacer(Modifier.height(18.dp))
            } else {
                Box(
                    Modifier
                        .padding(vertical = 6.dp)
                        .fillMaxWidth(width)
                        .height(14.dp)
                        .background(bar, RoundedCornerShape(7.dp)),
                )
            }
        }
    }
}

@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
        ) {
            Text(
                "You're offline. Showing your saved copy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun NotFoundContent(title: String, suggestions: List<SearchHit>, onOpen: (String) -> Unit, onSearch: () -> Unit) {
    if (suggestions.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.SearchOff,
            title = "No entry for “$title”",
            message = "Wiktionary doesn't have a page with this exact title. Check the spelling or try another search.",
            action = { OutlinedButton(onClick = onSearch) { Text("Search Wiktionary") } },
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 8.dp)) {
                Text("No entry for “$title”", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Pages that mention it:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        items(suggestions, key = { it.title }) { hit ->
            WordListItem(title = hit.title, summary = null, supporting = hit.excerpt, onClick = { onOpen(hit.title) })
        }
    }
}
