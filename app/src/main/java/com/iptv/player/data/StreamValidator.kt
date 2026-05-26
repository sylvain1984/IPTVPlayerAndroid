package com.iptv.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class StreamValidator(private val client: OkHttpClient) {

    suspend fun validate(source: StreamSource): StreamSource = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val updated = source.copy(lastCheckedMs = startMs)

        try {
            val ua = source.userAgent
                ?: "Mozilla/5.0 (Linux; Android 11; TV) AppleWebKit/537.36 Chrome/91.0 Safari/537.36"
            val builder = Request.Builder()
                .url(source.url)
                .header("User-Agent", ua)
                .header("Range", "bytes=0-8191")
            source.referer?.let { builder.header("Referer", it) }

            val response = client.newCall(builder.build()).execute()
            val latency = (System.currentTimeMillis() - startMs).toInt()
            val code = response.code
            val body = response.body?.bytes() ?: ByteArray(0)
            response.close()

            val score = when {
                code in 200..299 || code == 206 || code in 300..399 -> {
                    val text = body.take(8192).toByteArray().toString(Charsets.UTF_8)
                    when {
                        text.contains("#EXTM3U") || text.contains("#EXTINF") -> 1.0
                        body.isNotEmpty() -> 0.7
                        else -> 0.3
                    }
                }
                code == 403 -> 0.6
                code == 404 || code == 410 -> 0.1
                else -> 0.3
            }

            updated.copy(score = score, latencyMs = latency, lastWorkedMs = if (score > 0.5) startMs else null)
        } catch (e: UnknownHostException) {
            updated.copy(score = 0.05)
        } catch (e: SocketTimeoutException) {
            updated.copy(score = 0.1)
        } catch (e: IOException) {
            updated.copy(score = 0.2)
        } catch (e: Exception) {
            updated.copy(score = 0.2)
        }
    }

    suspend fun validateChannel(channel: Channel, limit: Int = 1): Channel =
        withContext(Dispatchers.IO) {
            val toCheck = channel.sources.take(limit)
            val rest = channel.sources.drop(limit)

            val validated = toCheck.map { async { validate(it) } }.awaitAll()

            val sorted = (validated + rest).sortedWith(
                compareByDescending<StreamSource> { it.score }
                    .thenBy { it.latencyMs ?: Int.MAX_VALUE }
            )
            channel.copy(sources = sorted)
        }
}
