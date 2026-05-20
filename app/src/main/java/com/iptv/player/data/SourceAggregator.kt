package com.iptv.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class SourceAggregator(private val client: OkHttpClient) {

    companion object {
        val DEFAULT_SOURCES = listOf(
            "https://raw.githubusercontent.com/fanmingming/live/main/tv/m3u/ipv4.m3u",
            "https://iptv-org.github.io/iptv/countries/cn.m3u",
            "https://raw.githubusercontent.com/YueChan/Live/main/IPTV.m3u",
            "https://raw.githubusercontent.com/YanG-1989/m3u/main/Gather.m3u",
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
            ),
        )
    }

    suspend fun fetchAll(urls: List<String> = DEFAULT_SOURCES): List<Channel> =
        withContext(Dispatchers.IO) {
            val results = urls.map { url ->
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
