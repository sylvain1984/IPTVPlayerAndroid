package com.iptv.player.ui

import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.iptv.player.data.Channel
import com.iptv.player.data.RtcManager
import com.iptv.player.data.RtcState

@Composable
fun RtcPlayerView(channel: Channel, rtcManager: RtcManager, modifier: Modifier = Modifier) {
    val state     by rtcManager.state.collectAsStateWithLifecycle()
    val remoteUid by rtcManager.remoteUid.collectAsStateWithLifecycle()
    var texView        by remember { mutableStateOf<TextureView?>(null) }
    var surfaceVersion by remember { mutableStateOf(0) }

    LaunchedEffect(channel.id, channel.rtcRoomId) { rtcManager.join(channel.rtcRoomId) }
    DisposableEffect(Unit) { onDispose { rtcManager.leave() } }

    // Re-bind canvas whenever the remote stream arrives OR the surface is recreated
    LaunchedEffect(remoteUid, surfaceVersion) {
        val uid = remoteUid ?: return@LaunchedEffect
        val tv  = texView   ?: return@LaunchedEffect
        rtcManager.renderRemote(uid, tv)
    }

    Box(modifier = modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                TextureView(ctx).also { tv ->
                    texView = tv
                    tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            surfaceVersion++
                        }
                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean = true
                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        when (state) {
            RtcState.CONNECTING -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("等待开播...", color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium)
            }
            RtcState.ERROR -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("连接失败", color = Color.White,
                    style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text("请检查网络并重试", color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall)
            }
            else -> {}
        }

        if (state == RtcState.LIVE) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Red.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
            ) {
                Text("● 直播中", color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
    }
}
