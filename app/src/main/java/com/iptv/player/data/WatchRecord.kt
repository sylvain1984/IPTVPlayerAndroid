package com.iptv.player.data

data class WatchRecord(
    val channelId: String,
    val lastWatchedMs: Long,
    val watchCount: Int = 1
)
