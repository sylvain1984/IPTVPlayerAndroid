package com.iptv.player.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
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
    // Worker URL，例如 https://iptv-live.xxx.workers.dev
    val isConfigured: Boolean
        get() = BuildConfig.LIVE_REGISTRY_URL.isNotBlank()

    private val gson = Gson()
    private val listType = TypeToken.getParameterized(List::class.java, Map::class.java).type

    private fun asString(any: Any?): String? = when (any) {
        is String -> any
        is Number -> any.toString()
        else -> null
    }

    private fun asDouble(any: Any?): Double? = when (any) {
        is Double -> any
        is Number -> any.toDouble()
        is String -> any.toDoubleOrNull()
        else -> null
    }

    private fun parseChannel(obj: Map<*, *>): LiveChannel? {
        val id      = asString(obj["id"])                                              ?: return null
        val roomId  = asString(obj["roomId"] ?: obj["roomID"] ?: obj["room"])          ?: "iptv_private"
        val name    = asString(obj["name"]   ?: obj["channelName"] ?: obj["title"])    ?: "专属直播"
        val pinHash = asString(obj["pinHash"] ?: obj["pin_hash"])
        val hostId  = asString(obj["hostId"]  ?: obj["host_id"])
        val rawTs   = asDouble(obj["startedAt"] ?: obj["startAt"] ?: obj["timestamp"])
            ?: (System.currentTimeMillis() / 1000.0)
        // 兼容毫秒时间戳
        val startedAt = if (rawTs > 1e10) rawTs / 1000.0 else rawTs
        return LiveChannel(id = id, name = name, roomId = roomId,
            pinHash = pinHash, hostId = hostId, startedAt = startedAt)
    }

    suspend fun fetchAll(client: OkHttpClient): List<LiveChannel> = withContext(Dispatchers.IO) {
        if (!isConfigured) return@withContext emptyList()
        try {
            val req = Request.Builder()
                .url("${BuildConfig.LIVE_REGISTRY_URL}/live/channels")
                .build()
            val body = client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                resp.body?.string() ?: return@withContext emptyList()
            }
            val list: List<Map<*, *>> = gson.fromJson(body, listType) ?: return@withContext emptyList()
            val now    = System.currentTimeMillis() / 1000.0
            val maxAge = 90.0
            list.mapNotNull { parseChannel(it) }
                .filter { (now - it.startedAt) in 0.0..maxAge }
                .sortedBy { it.startedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
