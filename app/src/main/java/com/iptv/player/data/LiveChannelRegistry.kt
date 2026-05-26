package com.iptv.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

private const val REGISTRY_URL = "https://iptv-75390-default-rtdb.firebaseio.com/live_channels"

data class LiveChannel(
    val id: String,
    val name: String,
    val roomId: String,
    val pinHash: String,
    val hostId: String,
    val startedAt: Double
) {
    fun toChannel(): Channel = Channel(
        id = "live_$id",
        name = name,
        groupTitle = "专属直播",
        sources = listOf(StreamSource(url = "rtc://$roomId")),
        isRtc = true
    )
}

object LiveChannelRegistry {
    val isConfigured: Boolean
        get() = true

    suspend fun fetchAll(client: OkHttpClient): List<LiveChannel> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()
        try {
            val req = Request.Builder().url("$REGISTRY_URL.json").build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: return@withContext emptyList()
            }
            if (body == "null") return@withContext emptyList()
            val parsed = com.google.gson.Gson().fromJson(body, Map::class.java) as? Map<*, *> ?: return@withContext emptyList()
            parsed.values.mapNotNull { any ->
                runCatching {
                    val json = com.google.gson.Gson().toJson(any)
                    com.google.gson.Gson().fromJson(json, LiveChannel::class.java)
                }.getOrNull()
            }.sortedBy { it.startedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
