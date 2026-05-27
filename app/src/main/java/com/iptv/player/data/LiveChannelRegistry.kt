package com.iptv.player.data

import com.iptv.player.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest

data class LiveChannel(
    val id: String,
    val name: String,
    val roomId: String,
    val pinHash: String? = null,
    val hostId: String? = null,
    val startedAt: Double
) {
    fun toChannel(): Channel = Channel(
        id = "live_$id",
        name = name,
        groupTitle = "专属直播",
        sources = listOf(StreamSource(url = "rtc://$roomId")),
        isRtc = true,
        pinHash = pinHash
    )

    fun verify(pin: String): Boolean {
        return pinHash?.let { it == hashPin(pin) } ?: true
    }

    companion object {
        fun hashPin(pin: String): String {
            val normalized = pin.mapNotNull { it.digitToIntOrNull()?.toString() }.joinToString("")
            val data = "iptv_pin_$normalized".toByteArray(Charsets.UTF_8)
            val digest = MessageDigest.getInstance("SHA-256").digest(data)
            return digest.joinToString("") { "%02x".format(it) }
        }
    }
}

object LiveChannelRegistry {
    val isConfigured: Boolean
        get() = BuildConfig.LIVE_REGISTRY_URL.isNotBlank()

    private fun normalizeTimestamp(ts: Double): Double {
        // 兼容秒(10位)和毫秒(13位)时间戳
        return if (ts > 10_000_000_000) ts / 1000.0 else ts
    }

    private fun asString(any: Any?): String? = when (any) {
        is String -> any
        is Number -> any.toString()
        else -> null
    }

    private fun asDouble(any: Any?): Double? = when (any) {
        is Double -> any
        is Int -> any.toDouble()
        is Long -> any.toDouble()
        is Number -> any.toDouble()
        is String -> any.toDoubleOrNull()
        else -> null
    }

    private fun parseLiveChannel(any: Any): LiveChannel? {
        val obj = any as? Map<*, *> ?: return null
        val roomId = asString(obj["roomId"] ?: obj["roomID"] ?: obj["room"]) ?: "iptv_private"
        val name = asString(obj["name"] ?: obj["channelName"] ?: obj["title"]) ?: "专属直播"
        val id = asString(obj["id"]) ?: return null
        val pinHash = asString(obj["pinHash"] ?: obj["pin_hash"])
        val hostId = asString(obj["hostId"] ?: obj["host_id"])
        val startedAt = normalizeTimestamp(asDouble(obj["startedAt"] ?: obj["startAt"] ?: obj["timestamp"]) ?: System.currentTimeMillis() / 1000.0)
        return LiveChannel(
            id = id,
            name = name,
            roomId = roomId,
            pinHash = pinHash,
            hostId = hostId,
            startedAt = startedAt
        )
    }

    suspend fun fetchAll(client: OkHttpClient): List<LiveChannel> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()
        val registryUrl = BuildConfig.LIVE_REGISTRY_URL
        try {
            val req = Request.Builder().url("$registryUrl.json").build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: return@withContext emptyList()
            }
            if (body == "null") return@withContext emptyList()
            val parsed = com.google.gson.Gson().fromJson(body, Map::class.java) as? Map<*, *> ?: return@withContext emptyList()
            val now = System.currentTimeMillis() / 1000.0
            val maxAge = 8 * 3600.0
            parsed.values.mapNotNull { any ->
                any?.let { parseLiveChannel(it) }
            }.filter { ch ->
                (now - ch.startedAt) in 0.0..maxAge
            }.sortedBy { it.startedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
