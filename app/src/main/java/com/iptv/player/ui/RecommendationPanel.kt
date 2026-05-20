package com.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iptv.player.data.Channel

@Composable
fun RecommendationPanel(
    recentChannels: List<Channel>,
    recommendedChannels: List<Channel>,
    onSelectChannel: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (recentChannels.isEmpty() && recommendedChannels.isEmpty()) {
        EmptyPlayerView()
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(vertical = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        if (recentChannels.isNotEmpty()) {
            RecommendRow(
                title    = "最近观看",
                channels = recentChannels,
                onSelect = onSelectChannel
            )
            Spacer(Modifier.height(28.dp))
        }

        if (recommendedChannels.isNotEmpty()) {
            RecommendRow(
                title    = "猜你喜欢",
                channels = recommendedChannels,
                onSelect = onSelectChannel
            )
        }

        if (recentChannels.isEmpty() && recommendedChannels.isEmpty()) {
            EmptyPlayerView()
        }
    }
}

@Composable
private fun RecommendRow(
    title: String,
    channels: List<Channel>,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            title,
            style     = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier  = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        LazyRow(
            contentPadding       = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(channels, key = { it.id }) { ch ->
                RecommendCard(channel = ch, onClick = { onSelect(ch.id) })
            }
        }
    }
}

@Composable
private fun RecommendCard(channel: Channel, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }

    val cardBg = when {
        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else      -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
    }

    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(cardBg)
            .then(
                if (isFocused) Modifier.border(
                    1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)
                ) else Modifier
            )
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Logo
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (channel.logoUrl != null) {
                AsyncImage(
                    model              = channel.logoUrl,
                    contentDescription = null,
                    modifier           = Modifier.fillMaxSize()
                )
            } else {
                Text("📺", style = MaterialTheme.typography.titleLarge)
            }
        }

        Text(
            channel.name,
            style    = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color    = if (isFocused) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
        )

        channel.groupTitle?.let { g ->
            Text(
                g,
                style    = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color    = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
            )
        }

        // Quality dot
        val score = channel.bestSource?.score ?: 0.0
        val dotColor = when {
            score >= 1.0 -> Color(0xFF4CAF50)
            score >= 0.5 -> Color(0xFFFFEB3B)
            score > 0    -> Color(0xFFFF9800)
            else         -> Color.Gray
        }
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(dotColor)
        )
    }
}
