package com.iptv.player.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SourceAggregator(
    private val client: OkHttpClient,
    private val context: Context? = null
) {

    companion object {
        val DEFAULT_SOURCES = listOf(
            "https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/ipv4.m3u",
            "https://iptv-org.github.io/iptv/countries/cn.m3u",
            "https://raw.githubusercontent.com/YueChan/Live/main/IPTV.m3u",
            "https://raw.githubusercontent.com/YanG-1989/m3u/main/Gather.m3u",
            "https://raw.githubusercontent.com/joevess/IPTV/main/m3u/iptv.m3u",
            "https://iptv-org.github.io/iptv/categories/sports.m3u",
        )

        val OPTIONAL_SOURCES = mapOf(
            "咪咕" to listOf(
                "https://raw.githubusercontent.com/YueChan/Live/main/Migu.m3u",
                "https://raw.githubusercontent.com/YanG-1989/m3u/main/Migu.m3u",
            ),
            "海外体育" to listOf(
                "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us_sports.m3u",
                "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/uk_sports.m3u",
            ),
            "中文聚合" to listOf(
                "https://iptv-org.github.io/iptv/languages/zho.m3u",
                "https://live.fanmingming.com/tv/m3u/global.m3u",
                "https://raw.githubusercontent.com/joevess/IPTV/main/iptv-search.m3u",
            ),
            "国际精选" to listOf(
                "https://raw.githubusercontent.com/Free-TV/IPTV/master/playlist.m3u8",
            ),
        )
    }

    suspend fun fetchAll(urls: List<String>? = null): List<Channel> =
        withContext(Dispatchers.IO) {
            val sources = urls ?: loadSourceCatalog().ifEmpty { DEFAULT_SOURCES }
            val results = sources.map { url ->
                async { fetchOne(url) }
            }.awaitAll()

            val merged = LinkedHashMap<String, Channel>()
            for (channels in results) {
                for (ch in channels) {
                    if (merged.containsKey(ch.id)) {
                        val existing = merged[ch.id]!!
                        val newSources = ch.sources.filter { s -> existing.sources.none { it.url == s.url } }
                        merged[ch.id] = existing.copy(
                            sources = existing.sources + newSources,
                            logoUrl = existing.logoUrl ?: ch.logoUrl,
                            groupTitle = existing.groupTitle ?: ch.groupTitle
                        )
                    } else {
                        merged[ch.id] = ch
                    }
                }
            }
            merged.values.toList()
        }

    private fun loadSourceCatalog(): List<String> {
        val ctx = context ?: return emptyList()
        val candidates = buildList {
            ctx.filesDir?.resolve("source_catalog.json")?.let { add(it) }
            runCatching {
                ctx.assets.open("source_catalog.json").bufferedReader().use { it.readText() }
            }.getOrNull()?.let { add(it) }
        }

        for (candidate in candidates) {
            val text = when (candidate) {
                is java.io.File -> if (candidate.exists()) candidate.readText() else null
                is String -> candidate
                else -> null
            } ?: continue

            val urls = gsonFromJson(text)
            if (urls.isNotEmpty()) return urls
        }
        return emptyList()
    }

    private fun gsonFromJson(text: String): List<String> {
        val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        return runCatching { com.google.gson.Gson().fromJson<List<String>>(text, type) ?: emptyList() }
            .getOrDefault(emptyList())
    }

    private suspend fun fetchOne(urlString: String): List<Channel> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(urlString)
                    .header("User-Agent", "Mozilla/5.0 IPTVPlayer")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext emptyList()
                val text = response.body?.string() ?: return@withContext emptyList()
                M3UParser.parse(text)
            } catch (e: Exception) {
                emptyList()
            }
        }
}
