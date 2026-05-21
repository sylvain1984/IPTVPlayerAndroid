package com.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
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

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val channels             by vm.filteredChannels.collectAsStateWithLifecycle()
    val allGroups            by vm.groups.collectAsStateWithLifecycle()
    val selectedId           by vm.selectedChannelId.collectAsStateWithLifecycle()
    val selectedGroup        by vm.selectedGroup.collectAsStateWithLifecycle()
    val searchText           by vm.searchText.collectAsStateWithLifecycle()
    val favOnly              by vm.showOnlyFavorites.collectAsStateWithLifecycle()
    val isRefreshing         by vm.isRefreshing.collectAsStateWithLifecycle()
    val progress             by vm.progress.collectAsStateWithLifecycle()
    val activeSource         by vm.activeSource.collectAsStateWithLifecycle()
    val selectedChannel      by vm.selectedChannel.collectAsStateWithLifecycle()
    val showAddSource        by vm.showAddSourceDialog.collectAsStateWithLifecycle()
    val recentChannels       by vm.recentChannels.collectAsStateWithLifecycle()
    val recommendedChannels  by vm.recommendedChannels.collectAsStateWithLifecycle()
    val showOnlyRecent       by vm.showOnlyRecent.collectAsStateWithLifecycle()
    val showRecommended      by vm.showRecommended.collectAsStateWithLifecycle()

    IPTVTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Row(modifier = Modifier.fillMaxSize()) {

                // ── Left sidebar: channel list (fixed 320 dp) ──────────────────
                ChannelListPanel(
                    channels = channels,
                    groups = allGroups,
                    selectedChannelId = selectedId,
                    selectedGroup = selectedGroup,
                    searchText = searchText,
                    showOnlyFavorites = favOnly,
                    isRefreshing = isRefreshing,
                    progress = progress,
                    hasRecent        = recentChannels.isNotEmpty(),
                    hasRecommended   = recommendedChannels.isNotEmpty(),
                    showOnlyRecent   = showOnlyRecent,
                    showRecommended  = showRecommended,
                    onSelectChannel    = { vm.selectChannel(it) },
                    onToggleFavorite   = { vm.toggleFavorite(it) },
                    onRefresh          = { vm.refresh() },
                    onGroupSelected    = { vm.setGroup(it) },
                    onSearchChanged    = { vm.setSearch(it) },
                    onToggleFavoritesFilter = { vm.toggleFavoritesFilter() },
                    onShowRecent            = { vm.showRecent() },
                    onToggleRecommended     = { vm.toggleRecommended() },
                    modifier = Modifier.fillMaxHeight().width(320.dp)
                )

                // Thin vertical divider
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                )

                // ── Right area: header + player + source picker ────────────────
                Column(modifier = Modifier.fillMaxSize()) {

                    // Channel info bar (only when a channel is selected)
                    if (selectedChannel != null) {
                        ChannelInfoBar(
                            channel      = selectedChannel!!,
                            activeSource = activeSource,
                            onToggleFavorite = { vm.toggleFavorite(selectedChannel!!) },
                            onShowAddSource  = { vm.showAddSource() },
                            onClearRefresh   = { vm.clearAndRefresh() }
                        )
                    }

                    PlayerPanel(
                        source   = activeSource,
                        onTryFallbackSource = { vm.trySwitchToNextSource() },
                        modifier = Modifier.fillMaxWidth().weight(1f)
                    )

                    // Source picker strip (only when multiple sources available)
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

            // Add-source dialog
            if (showAddSource) {
                AddSourceDialog(
                    onDismiss    = { vm.hideAddSource() },
                    onAddUrl     = { vm.addRemoteSource(it) },
                    onAddPreset  = { vm.addOptionalSourceGroup(it) }
                )
            }
        }
    }
}

// ── Channel info bar ──────────────────────────────────────────────────────────

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
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Text(
                    g,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }

        Spacer(Modifier.weight(1f))

        activeSource?.latencyMs?.let {
            Text(
                "$it ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }

        IconButton(onClick = onShowAddSource) {
            Icon(Icons.Default.Add, contentDescription = "添加源")
        }

        IconButton(onClick = onClearRefresh) {
            Icon(
                Icons.Default.DeleteForever,
                contentDescription = "清除缓存并重新拉取",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

// ── Source picker strip ───────────────────────────────────────────────────────

@Composable
private fun SourcePickerStrip(
    sources: List<StreamSource>,
    activeUrl: String?,
    onSelect: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp)
    ) {
        Text(
            "可用源 (${sources.size})",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(4.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sources, key = { it.url }) { src ->
                SourceChip(
                    source   = src,
                    isActive = src.url == activeUrl,
                    onClick  = { onSelect(src.url) }
                )
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
        source.score > 0    -> Color(0xFFFF9800)
        else                -> Color.Gray
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
            .then(
                if (isActive) Modifier.border(
                    1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)
                ) else Modifier
            )
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
            Text(
                "${it}ms",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
    }
}
