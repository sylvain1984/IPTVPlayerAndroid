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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iptv.player.data.Channel
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi

@Composable
fun ChannelListPanel(
    channels: List<Channel>,
    totalChannelCount: Int,
    groups: List<String>,
    subcategories: List<String>,
    selectedChannelId: String?,
    selectedGroup: String?,
    selectedSubcategory: String?,
    searchText: String,
    showOnlyFavorites: Boolean,
    isRefreshing: Boolean,
    progress: String,
    hasRecent: Boolean,
    hasRecommended: Boolean,
    showOnlyRecent: Boolean = false,
    showRecommended: Boolean = false,
    showExclusive: Boolean = false,
    onSelectChannel: (String) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onRefresh: () -> Unit,
    onGroupSelected: (String?) -> Unit,
    onSubcategorySelected: (String?) -> Unit,
    onSearchChanged: (String) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onShowRecent: () -> Unit,
    onToggleRecommended: () -> Unit,
    onToggleExclusive: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var searchExpanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }

    // Scroll to the selected channel only when selection changes (not on every list update)
    LaunchedEffect(selectedChannelId) {
        if (selectedChannelId == null) return@LaunchedEffect
        val idx = channels.indexOfFirst { it.id == selectedChannelId }
        if (idx >= 0) listState.animateScrollToItem(idx)
    }

    // Collapse search when text is cleared externally
    LaunchedEffect(searchText) {
        if (searchText.isEmpty()) searchExpanded = false
    }

    // Derive favorites / rest split for the "全部" pinned-section layout
    val isAllMode = !showOnlyFavorites && !showOnlyRecent && !showRecommended && !showExclusive &&
        selectedGroup == null && selectedSubcategory == null && searchText.isEmpty()
    val favChannels = if (isAllMode) channels.filter { it.isFavorite } else emptyList()
    val restChannels = if (isAllMode) channels.filter { !it.isFavorite } else channels

    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surface)
            .fillMaxHeight()
    ) {
        // ── Toolbar ─────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val isFiltered = searchText.isNotEmpty() || selectedGroup != null || selectedSubcategory != null ||
                showOnlyFavorites
            val countLabel = when {
                progress.isNotEmpty() -> progress
                isFiltered -> "${channels.size} / $totalChannelCount 个频道"
                else -> "$totalChannelCount 个频道"
            }
            Text(
                countLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            TvIconButton(
                onClick = {
                    if (searchExpanded) { searchExpanded = false; onSearchChanged("") }
                    else searchExpanded = true
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

        // ── Search bar ───────────────────────────────────────────────────────
        AnimatedVisibility(visible = searchExpanded, enter = expandVertically(), exit = shrinkVertically()) {
            LaunchedEffect(searchExpanded) { if (searchExpanded) searchFocusRequester.requestFocus() }
            TvSearchBar(
                value = searchText,
                onValueChange = onSearchChanged,
                focusRequester = searchFocusRequester,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }

        // ── Filter tabs + visible category sections ──────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    GroupChip(
                        label = "全部",
                        selected = !showOnlyFavorites && !showOnlyRecent && !showRecommended && !showExclusive && selectedGroup == null && selectedSubcategory == null,
                        onClick = { onGroupSelected(null); onSubcategorySelected(null) }
                    )
                }
                item {
                    GroupChip(
                        label = "专属",
                        selected = showExclusive,
                        onClick = onToggleExclusive
                    )
                }
                val favCount = channels.count { it.isFavorite }
                item {
                    GroupChip(
                        label         = if (favCount > 0) "收藏 ($favCount)" else "收藏",
                        selected      = showOnlyFavorites,
                        icon          = Icons.Default.Star,
                        selectedColor = Color(0xFFFFCC00),
                        onClick       = onToggleFavoritesFilter
                    )
                }
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
            }

            if (groups.isNotEmpty() || subcategories.isNotEmpty()) {
                Text(
                    "分类",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                if (groups.isNotEmpty()) {
                    GroupChipFlow(
                        chips = groups.map { g ->
                            g to {
                                onSubcategorySelected(null)
                                onGroupSelected(if (selectedGroup == g) null else g)
                            }
                        },
                        selected = { g -> !showOnlyFavorites && !showOnlyRecent && !showRecommended && !showExclusive && selectedGroup == g }
                    )
                }
                if (subcategories.isNotEmpty()) {
                    GroupChipFlow(
                        chips = subcategories.map { tag ->
                            tag to {
                                onGroupSelected(null)
                                onSubcategorySelected(if (selectedSubcategory == tag) null else tag)
                            }
                        },
                        selected = { tag -> !showOnlyFavorites && !showOnlyRecent && !showRecommended && !showExclusive && selectedSubcategory == tag }
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))

        // ── Channel list ─────────────────────────────────────────────────────
        if (channels.isEmpty() && !isRefreshing) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (searchText.isNotEmpty() || selectedGroup != null || selectedSubcategory != null || showOnlyFavorites)
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
                // In "全部" mode: favorites pinned at top with a section header
                if (isAllMode && favChannels.isNotEmpty()) {
                    item(key = "__fav_header__") {
                        SectionHeader(text = "★ 收藏 (${favChannels.size})", gold = true)
                    }
                    items(favChannels, key = { "fav_${it.id}" }) { ch ->
                        ChannelItem(
                            channel = ch,
                            isSelected = ch.id == selectedChannelId,
                            onSelect = { onSelectChannel(ch.id) },
                            onToggleFavorite = { onToggleFavorite(ch) }
                        )
                    }
                    if (restChannels.isNotEmpty()) {
                        item(key = "__all_header__") {
                            SectionHeader(text = "全部频道 (${restChannels.size})")
                        }
                    }
                }
                items(restChannels, key = { it.id }) { ch ->
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
private fun SectionHeader(text: String, gold: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 14.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (gold) {
            Box(modifier = Modifier.width(3.dp).height(14.dp).background(Color(0xFFFFCC00)))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = if (gold) Color(0xFFFFCC00)
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
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
        channel.isFavorite -> Color(0xFFFFCC00).copy(alpha = 0.06f)
        else       -> Color.Transparent
    }

    // Left accent bar: gold for favorites, primary for selected, subtle for focused
    val accentColor = when {
        channel.isFavorite && isSelected -> Color(0xFFFFCC00)
        channel.isFavorite -> Color(0xFFFFCC00).copy(alpha = 0.7f)
        isSelected -> MaterialTheme.colorScheme.primary
        isFocused  -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
        else       -> Color.Transparent
    }
    val accentWidth = if (channel.isFavorite) 4.dp else 3.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onSelect)
            .background(bgColor)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(accentWidth)
                .fillMaxHeight()
                .background(accentColor)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 15.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
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
                    contentDescription = if (channel.isFavorite) "取消收藏" else "收藏",
                    modifier = Modifier.size(22.dp),
                    tint = if (channel.isFavorite) Color(0xFFFFCC00)
                           else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                )
            }
        }
    }
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
        score >= 1.0 -> Color(0xFF4CAF50)   // green  — confirmed working
        score >= 0.5 -> Color(0xFFFFEB3B)   // yellow — probably working
        score > 0.0 -> Color(0xFFFF6B35)    // orange — checked but poor
        else        -> Color(0xFF555555)     // dark gray — not yet checked
    }
    Box(
        modifier = Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GroupChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    selectedColor: Color = Color.Unspecified
) {
    var isFocused by remember { mutableStateOf(false) }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val activeColor = if (selectedColor != Color.Unspecified) selectedColor
                      else MaterialTheme.colorScheme.primary

    LaunchedEffect(isFocused) {
        if (isFocused) bringIntoViewRequester.bringIntoView()
    }

    Row(
        modifier = Modifier
            .bringIntoViewRequester(bringIntoViewRequester)
            .clip(RoundedCornerShape(16.dp))
            .background(
                when {
                    selected  -> activeColor.copy(alpha = 0.2f)
                    isFocused -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                    else      -> MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .then(
                if (selected) Modifier.border(1.dp, activeColor, RoundedCornerShape(16.dp))
                else Modifier
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
                tint = if (selected) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) activeColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GroupChipFlow(
    chips: List<Pair<String, () -> Unit>>,
    selected: (String) -> Boolean,
    labelTransform: (String) -> String = { it }
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chips.forEach { (label, onClick) ->
            GroupChip(
                label = labelTransform(label),
                selected = selected(label),
                onClick = onClick
            )
        }
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
