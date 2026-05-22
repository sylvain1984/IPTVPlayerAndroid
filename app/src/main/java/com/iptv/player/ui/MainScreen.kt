package com.iptv.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.iptv.player.data.Channel
import com.iptv.player.data.StreamSource
import com.iptv.player.ui.theme.IPTVTheme
import com.iptv.player.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val channels            by vm.filteredChannels.collectAsStateWithLifecycle()
    val allChannels         by vm.allChannels.collectAsStateWithLifecycle()
    val allGroups           by vm.groups.collectAsStateWithLifecycle()
    val selectedId          by vm.selectedChannelId.collectAsStateWithLifecycle()
    val selectedGroup       by vm.selectedGroup.collectAsStateWithLifecycle()
    val searchText          by vm.searchText.collectAsStateWithLifecycle()
    val favOnly             by vm.showOnlyFavorites.collectAsStateWithLifecycle()
    val isRefreshing        by vm.isRefreshing.collectAsStateWithLifecycle()
    val progress            by vm.progress.collectAsStateWithLifecycle()
    val activeSource        by vm.activeSource.collectAsStateWithLifecycle()
    val selectedChannel     by vm.selectedChannel.collectAsStateWithLifecycle()
    val showAddSource       by vm.showAddSourceDialog.collectAsStateWithLifecycle()
    val recentChannels      by vm.recentChannels.collectAsStateWithLifecycle()
    val recommendedChannels by vm.recommendedChannels.collectAsStateWithLifecycle()
    val showOnlyRecent      by vm.showOnlyRecent.collectAsStateWithLifecycle()
    val showRecommended     by vm.showRecommended.collectAsStateWithLifecycle()

    // userExitedFullscreen: set when user explicitly presses Back from fullscreen.
    // Resets automatically when a new channel is selected.
    var userExitedFullscreen by remember { mutableStateOf(false) }
    LaunchedEffect(selectedId) { userExitedFullscreen = false }

    // Derive isFullscreen directly — no coroutine delay, fires on the same frame as selection.
    val isFullscreen = selectedId != null && !userExitedFullscreen

    var showOverlay by remember { mutableStateOf(false) }

    // Animate the sidebar width: 320dp when visible, 0dp when fullscreen
    val sidebarWidth by animateDpAsState(
        targetValue   = if (isFullscreen) 0.dp else 320.dp,
        animationSpec = tween(durationMillis = 280),
        label         = "sidebar"
    )

    // Show overlay briefly when entering fullscreen (new channel selected)
    LaunchedEffect(selectedId) {
        if (selectedId != null) {
            showOverlay = true
        }
    }

    // Auto-hide fullscreen overlay after 3 s
    LaunchedEffect(showOverlay, selectedId) {
        if (showOverlay) {
            delay(3_000)
            showOverlay = false
        }
    }

    val hasActiveFilter = searchText.isNotEmpty() || selectedGroup != null || favOnly ||
        showOnlyRecent || showRecommended

    // Back: exit fullscreen first, then clear filters
    BackHandler(enabled = isFullscreen || hasActiveFilter) {
        when {
            isFullscreen            -> userExitedFullscreen = true
            searchText.isNotEmpty() -> vm.setSearch("")
            showOnlyRecent          -> vm.showRecent()
            showRecommended         -> vm.toggleRecommended()
            favOnly                 -> vm.toggleFavoritesFilter()
            selectedGroup != null   -> vm.setGroup(null)
        }
    }

    IPTVTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Row(modifier = Modifier.fillMaxSize()) {

                // ── Left sidebar — collapses to 0 dp when fullscreen ─────────
                Box(
                    modifier = Modifier
                        .width(sidebarWidth)
                        .fillMaxHeight()
                        .clipToBounds()
                ) {
                    if (sidebarWidth > 0.dp) {
                        Row(modifier = Modifier.width(321.dp).fillMaxHeight()) {
                            ChannelListPanel(
                                channels              = channels,
                                totalChannelCount     = allChannels.size,
                                groups                = allGroups,
                                selectedChannelId     = selectedId,
                                selectedGroup         = selectedGroup,
                                searchText            = searchText,
                                showOnlyFavorites     = favOnly,
                                isRefreshing          = isRefreshing,
                                progress              = progress,
                                hasRecent             = recentChannels.isNotEmpty(),
                                hasRecommended        = recommendedChannels.isNotEmpty(),
                                showOnlyRecent        = showOnlyRecent,
                                showRecommended       = showRecommended,
                                onSelectChannel       = { vm.selectChannel(it) },
                                onToggleFavorite      = { vm.toggleFavorite(it) },
                                onRefresh             = { vm.refresh() },
                                onGroupSelected       = { vm.setGroup(it) },
                                onSearchChanged       = { vm.setSearch(it) },
                                onToggleFavoritesFilter = { vm.toggleFavoritesFilter() },
                                onShowRecent          = { vm.showRecent() },
                                onToggleRecommended   = { vm.toggleRecommended() },
                                modifier              = Modifier.fillMaxHeight().width(320.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight().width(1.dp)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                            )
                        }
                    }
                }

                // ── Right: player + overlays ──────────────────────────────────
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {

                    // Info bar: inline when split, overlay when fullscreen
                    if (!isFullscreen && selectedChannel != null) {
                        ChannelInfoBar(
                            channel          = selectedChannel!!,
                            activeSource     = activeSource,
                            onToggleFavorite = { vm.toggleFavorite(selectedChannel!!) },
                            onShowAddSource  = { vm.showAddSource() },
                            onClearRefresh   = { vm.clearAndRefresh() }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        PlayerPanel(
                            source              = activeSource,
                            onTryFallbackSource  = { vm.trySwitchToNextSource() },
                            onRevalidateSources  = { vm.revalidateCurrentChannel() },
                            onErrorNoFallback    = { userExitedFullscreen = true },
                            modifier            = Modifier.fillMaxSize()
                        )

                        // Fullscreen tap target — click toggles overlay visibility
                        if (isFullscreen) {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { showOverlay = !showOverlay }
                            )
                        }

                        // Fullscreen info overlay — fades via alpha animation
                        if (isFullscreen && selectedChannel != null) {
                            val overlayAlpha by animateFloatAsState(
                                targetValue = if (showOverlay) 1f else 0f,
                                label = "overlay"
                            )
                            FullscreenInfoOverlay(
                                channel          = selectedChannel!!,
                                activeSource     = activeSource,
                                onToggleFavorite = { vm.toggleFavorite(selectedChannel!!) },
                                onShowAddSource  = { vm.showAddSource() },
                                onClearRefresh   = { vm.clearAndRefresh() },
                                onExitFullscreen = { userExitedFullscreen = true },
                                modifier         = Modifier
                                    .align(Alignment.TopStart)
                                    .fillMaxWidth()
                                    .alpha(overlayAlpha)
                            )
                        }
                    }

                    // Source picker: only in split-view mode
                    if (!isFullscreen) {
                        val ch = selectedChannel
                        if (ch != null && ch.sources.size > 1) {
                            SourcePickerStrip(
                                sources   = ch.sources,
                                activeUrl = activeSource?.url,
                                onSelect  = { vm.selectSource(it) }
                            )
                        }
                    }
                }
            }

            if (showAddSource) {
                AddSourceDialog(
                    onDismiss   = { vm.hideAddSource() },
                    onAddUrl    = { vm.addRemoteSource(it) },
                    onAddPreset = { vm.addOptionalSourceGroup(it) }
                )
            }
        }
    }
}

// ── Fullscreen overlay bar ────────────────────────────────────────────────────

@Composable
private fun FullscreenInfoOverlay(
    channel: Channel,
    activeSource: StreamSource?,
    onToggleFavorite: () -> Unit,
    onShowAddSource: () -> Unit,
    onClearRefresh: () -> Unit,
    onExitFullscreen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                )
            )
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Back-to-list button
            IconButton(onClick = onExitFullscreen) {
                Icon(Icons.Default.ArrowBack, contentDescription = "返回列表", tint = Color.White)
            }

            Text(
                channel.name,
                style     = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color     = Color.White,
                maxLines  = 1,
                overflow  = TextOverflow.Ellipsis,
                modifier  = Modifier.weight(1f)
            )

            channel.groupTitle?.let { g ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Text(
                        g,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style    = MaterialTheme.typography.labelSmall,
                        color    = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            activeSource?.latencyMs?.let {
                Text(
                    "${it}ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (channel.isFavorite) "取消收藏" else "收藏",
                    tint = if (channel.isFavorite) Color(0xFFFFCC00) else Color.White.copy(alpha = 0.7f)
                )
            }

            IconButton(onClick = onShowAddSource) {
                Icon(Icons.Default.Add, contentDescription = "添加源", tint = Color.White.copy(alpha = 0.8f))
            }

            IconButton(onClick = onClearRefresh) {
                Icon(Icons.Default.DeleteForever, contentDescription = "清除缓存",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ── Split-view info bar ───────────────────────────────────────────────────────

@Composable
private fun ChannelInfoBar(
    channel: Channel,
    activeSource: StreamSource?,
    onToggleFavorite: () -> Unit,
    onShowAddSource: () -> Unit,
    onClearRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            channel.name,
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines   = 1,
            overflow   = TextOverflow.Ellipsis
        )

        IconButton(onClick = onToggleFavorite) {
            Icon(
                if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                tint = if (channel.isFavorite) Color(0xFFFFCC00)
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        channel.groupTitle?.let { g ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(g, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall)
            }
        }

        Spacer(Modifier.weight(1f))

        activeSource?.latencyMs?.let {
            Text("${it}ms", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }

        IconButton(onClick = onShowAddSource) {
            Icon(Icons.Default.Add, contentDescription = "添加源")
        }

        IconButton(onClick = onClearRefresh) {
            Icon(Icons.Default.DeleteForever, contentDescription = "清除缓存",
                tint = MaterialTheme.colorScheme.error)
        }
    }
}

// ── Source picker strip ───────────────────────────────────────────────────────

@Composable
private fun SourcePickerStrip(sources: List<StreamSource>, activeUrl: String?, onSelect: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp)
    ) {
        Text("可用源 (${sources.size})", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp))
        Spacer(Modifier.height(4.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(sources, key = { it.url }) { src ->
                SourceChip(source = src, isActive = src.url == activeUrl, onClick = { onSelect(src.url) })
            }
        }
    }
}

@Composable
private fun SourceChip(source: StreamSource, isActive: Boolean, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    val dotColor = when {
        source.score >= 1.0 -> Color(0xFF4CAF50)
        source.score >= 0.5 -> Color(0xFFFFEB3B)
        source.score > 0.0  -> Color(0xFFFF6B35)
        else                -> Color(0xFF555555)
    }

    val host = remember(source.url) {
        runCatching { java.net.URL(source.url).host }.getOrDefault(source.url)
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    isActive  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    isFocused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    else      -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .then(if (isActive) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(dotColor))
        Text(host, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        source.latencyMs?.let {
            Text("${it}ms", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}
