package com.iptv.player.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.iptv.player.data.StreamSource

@OptIn(UnstableApi::class)
@Composable
fun PlayerPanel(
    source: StreamSource?,
    onTryFallbackSource: () -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    // Once the stream plays once, suppress subsequent BUFFERING overlays for live streams
    var hasEverBeenReady by remember { mutableStateOf(false) }

    // Keep one player instance and switch media item on source change.
    val player = remember {
        ExoPlayer.Builder(context).build().also { exo ->
            exo.repeatMode = Player.REPEAT_MODE_ONE
            exo.playWhenReady = true
        }
    }

    LaunchedEffect(source?.url, source?.userAgent, source?.referer) {
        if (source == null) {
            player.stop()
            player.clearMediaItems()
            isLoading = false
            errorMessage = null
            return@LaunchedEffect
        }

        val ua = source.userAgent
            ?: "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36"
        val headers = buildMap<String, String> {
            put("User-Agent", ua)
            source.referer?.takeIf { it.isNotEmpty() }?.let { put("Referer", it) }
        }
        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(ua)
            .setDefaultRequestProperties(headers)
            .setAllowCrossProtocolRedirects(true)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        val mediaSource = mediaSourceFactory.createMediaSource(
            MediaItem.fromUri(Uri.parse(source.url))
        )

        player.setMediaSource(mediaSource)
        player.prepare()
        player.playWhenReady = true
        hasEverBeenReady = false
        isLoading = true
        errorMessage = null
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_READY -> {
                        isLoading = false
                        hasEverBeenReady = true
                        errorMessage = null
                    }
                    // Don't flash loading overlay on live-stream buffering after first play
                    Player.STATE_BUFFERING -> if (!hasEverBeenReady) isLoading = true
                    else -> {}
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                isLoading = false
                val switched = onTryFallbackSource()
                errorMessage = if (switched) {
                    "当前源失败，正在自动切换备用源..."
                } else {
                    error.localizedMessage ?: "播放失败，请尝试切换其他源"
                }
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (source == null) {
            EmptyPlayerView()
        } else {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false  // TV uses D-pad; we handle overlays ourselves
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("正在连接...", color = Color.White.copy(alpha = 0.7f))
                }
            } else if (errorMessage != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Default.Warning, contentDescription = null,
                        tint = Color(0xFFFFA726), modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("播放失败", color = Color.White,
                        style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(errorMessage!!, color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodySmall)
                    source.url.let { url ->
                        Spacer(Modifier.height(6.dp))
                        Text(url, color = Color.White.copy(alpha = 0.3f),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2)
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyPlayerView() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("📺", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "选择一个频道开始观看",
            color = Color.Gray,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
