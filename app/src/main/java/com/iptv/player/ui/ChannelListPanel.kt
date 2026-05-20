package com.iptv.player.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iptv.player.data.Channel

@Composable
fun ChannelListPanel(
    channels: List<Channel>,
    groups: List<String>,
    selectedChannelId: String?,
    selectedGroup: String?,
    searchText: String,
    showOnlyFavorites: Boolean,
    isRefreshing: Boolean,
    progress: String,
    hasRecent: Boolean,
    hasRecommended: Boolean,
    showOnlyRecent: Boolean = false,
    showRecommended: Boolean = false,
    onSelectChannel: (String) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onRefresh: () -> Unit,
    onGroupSelected: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onShowRecent: () -> Unit,
    onToggleRecommended: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // Collapse search when text is cleared externally
    LaunchedEffect(searchText) {
        if (searchText.isEmpty()) searchExpanded = false
    }

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxHeight()
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TvIconButton(
                onClick = onToggleFavoritesFilter,
                tint = if (showOnlyFavorites) Color(0xFFFFCC00)
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ) {
                Icon(
                    if (showOnlyFavorites) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = "收藏筛选",
                    modifier = Modifier.size(20.dp)
                )
            }

            if (progress.isNotEmpty()) {
                Text(
                    progress,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Search toggle button — D-pad 可以直接选中它再按确认展开搜索框
            TvIconButton(
                onClick = {
                    if (searchExpanded) {
                        searchExpanded = false
                        onSearchChanged("")
                    } else {
                        searchExpanded = true
                    }
                },
                tint = if (searchExpanded) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            ) {
                Icon(
                    if (searchExpanded) Icons.Default.Close else Icons.Default.Search,
                    contentDescription = if (searchExpanded) "关闭搜索" else "搜索频道",
                    modifier = Modifier.size(20.dp)
                )
            }

            TvIconButton(onClick = onRefresh, enabled = !isRefreshing) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = "刷新",
                        modifier = Modifier.size(20.dp))
                }
            }
        }

        // 搜索框：仅在展开时出现，展开后自动获取焦点
        AnimatedVisibility(
            visible = searchExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            LaunchedEffect(searchExpanded) {
                if (searchExpanded) searchFocusRequester.requestFocus()
            }
            TvSearchBar(
                value = searchText,
                onValueChange = onSearchChanged,
                focusRequester = searchFocusRequester,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // Group filter chips
        LazyRow(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (hasRecent) {
                item {
                    GroupChip(
                        label   = "最近",
                        selected = showOnlyRecent,
                        icon    = Icons.Default.AccessTime,
                        onClick = onShowRecent
                    )
                }
            }
            if (hasRecommended) {
                item {
                    GroupChip(
                        label   = "推荐",
                        selected = showRecommended,
                        icon    = Icons.Default.AutoAwesome,
                        onClick = onToggleRecommended
                    )
                }
            }
            item {
                GroupChip(
                    label    = "全部",
                    selected = !showOnlyRecent && !showRecommended && selectedGroup == null,
                    onClick  = { onGroupSelected(null) }
                )
            }
            items(groups) { g ->
                GroupChip(
                    label    = g,
                    selected = !showOnlyRecent && !showRecommended && selectedGroup == g,
                    onClick  = { onGroupSelected(if (selectedGroup == g) null else g) }
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        // Channel list
        if (channels.isEmpty() && !isRefreshing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchText.isNotEmpty() || selectedGroup != null || showOnlyFavorites)
                        "无匹配结果" else "暂无频道，请点击刷新",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 4.dp)
            ) {
                items(channels, key = { it.id }) { ch ->
                    ChannelItem(
                        channel = ch,
                        isSelected = ch.id == selectedChannelId,
                        onSelect = { onSelectChannel(ch.id) },
                        onToggleFavorite = { onToggleFavorite(ch) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelItem(
    channel: Channel,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }

    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
        isFocused  -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
        else       -> Color.Transparent
    }

    val accentColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isFocused  -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        else       -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onSelect)
            .background(bgColor)
    ) {
        // 左侧焦点色条
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(3.dp)
                .fillMaxHeight()
                .background(accentColor)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 15.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
        ChannelLogo(channel.logoUrl, modifier = Modifier.size(32.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                channel.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isFocused  -> MaterialTheme.colorScheme.onSurface
                    else       -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                }
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreDot(channel.bestSource?.score ?: 0.0)
                Text(
                    "${channel.sources.size} 源",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                channel.groupTitle?.let { g ->
                    Text(
                        g,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        maxLines = 1
                    )
                }
            }
        }

        TvIconButton(onClick = onToggleFavorite) {
            Icon(
                if (channel.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (channel.isFavorite) Color(0xFFFFCC00)
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
        } // end Row
    } // end Box
}

@Composable
private fun ChannelLogo(logoUrl: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (logoUrl != null) {
            AsyncImage(model = logoUrl, contentDescription = null,
                modifier = Modifier.fillMaxSize())
        } else {
            Text("📺", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ScoreDot(score: Double) {
    val color = when {
        score >= 1.0 -> Color(0xFF4CAF50)
        score >= 0.5 -> Color(0xFFFFEB3B)
        score > 0   -> Color(0xFFFF9800)
        else        -> Color.Gray
    }
    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
}

@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    var isFocused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    selected  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    isFocused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    else      -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .then(
                if (selected) Modifier.border(1.dp, MaterialTheme.colorScheme.primary,
                    RoundedCornerShape(16.dp)) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            androidx.compose.material3.Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (selected) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun TvSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            color = MaterialTheme.colorScheme.onSurface
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .then(
                        if (isFocused) Modifier.border(1.dp, MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp)) else Modifier
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (value.isEmpty()) {
                    Text("搜索频道…", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
                inner()
            }
        },
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
    )
}

@Composable
private fun TvIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    content: @Composable () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isFocused) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                else Color.Transparent
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable(enabled)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides tint) {
            content()
        }
    }
}
