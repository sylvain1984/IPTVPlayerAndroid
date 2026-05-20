package com.iptv.player.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "iptv_store")

class ChannelRepository(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private val aggregator = SourceAggregator(client)
    private val validator = StreamValidator(client)
    private val gson = Gson()

    private val KEY_CHANNELS      = stringPreferencesKey("channels_json")
    private val KEY_LAST_REFRESH  = longPreferencesKey("last_refresh_ms")
    private val KEY_WATCH_HISTORY = stringPreferencesKey("watch_history_json")

    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels: StateFlow<List<Channel>> = _channels.asStateFlow()

    // channelId -> WatchRecord，供 ViewModel 组合使用
    private val _watchHistory = MutableStateFlow<Map<String, WatchRecord>>(emptyMap())
    val watchHistory: StateFlow<Map<String, WatchRecord>> = _watchHistory.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _progress = MutableStateFlow("")
    val progress: StateFlow<String> = _progress.asStateFlow()

    private val _lastRefreshMs = MutableStateFlow<Long?>(null)
    val lastRefreshMs: StateFlow<Long?> = _lastRefreshMs.asStateFlow()

    private var validationJob: Job? = null

    suspend fun load() = withContext(Dispatchers.IO) {
        val prefs = context.dataStore.data.first()
        val json = prefs[KEY_CHANNELS] ?: return@withContext
        val type = object : TypeToken<List<Channel>>() {}.type
        _channels.value = gson.fromJson(json, type) ?: emptyList()
        _lastRefreshMs.value = prefs[KEY_LAST_REFRESH]
        val historyJson = prefs[KEY_WATCH_HISTORY]
        if (historyJson != null) {
            val histType = object : TypeToken<Map<String, WatchRecord>>() {}.type
            _watchHistory.value = gson.fromJson(historyJson, histType) ?: emptyMap()
        }
    }

    private suspend fun save() = withContext(Dispatchers.IO) {
        context.dataStore.edit { prefs ->
            prefs[KEY_CHANNELS] = gson.toJson(_channels.value)
            _lastRefreshMs.value?.let { prefs[KEY_LAST_REFRESH] = it }
            prefs[KEY_WATCH_HISTORY] = gson.toJson(_watchHistory.value)
        }
    }

    suspend fun recordWatch(channelId: String) {
        val now = System.currentTimeMillis()
        val existing = _watchHistory.value[channelId]
        _watchHistory.value = _watchHistory.value + (channelId to WatchRecord(
            channelId     = channelId,
            lastWatchedMs = now,
            watchCount    = (existing?.watchCount ?: 0) + 1
        ))
        // Persist immediately so history survives force-stop
        withContext(Dispatchers.IO) {
            context.dataStore.edit { prefs ->
                prefs[KEY_WATCH_HISTORY] = gson.toJson(_watchHistory.value)
            }
        }
    }

    suspend fun refresh(scope: CoroutineScope) {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        validationJob?.cancel()

        _progress.value = "正在拉取频道列表..."
        val fetched = aggregator.fetchAll()

        if (fetched.isEmpty()) {
            _progress.value = "拉取失败，保留现有频道"
            _isRefreshing.value = false
            save()
            return
        }

        val favoriteIds = _channels.value.filter { it.isFavorite }.map { it.id }.toSet()
        val oldSources = _channels.value
            .flatMap { it.sources }
            .associateBy { it.url }

        _channels.value = fetched.map { ch ->
            ch.copy(
                isFavorite = ch.id in favoriteIds,
                sources = ch.sources.map { src ->
                    oldSources[src.url]?.let { known ->
                        src.copy(
                            score = known.score,
                            latencyMs = known.latencyMs,
                            lastCheckedMs = known.lastCheckedMs,
                            lastWorkedMs = known.lastWorkedMs
                        )
                    } ?: src
                }
            )
        }

        _lastRefreshMs.value = System.currentTimeMillis()
        _progress.value = "已加载 ${_channels.value.size} 个频道"
        save()
        _isRefreshing.value = false

        validationJob = scope.launch { validateAllInBackground() }
    }

    private suspend fun validateAllInBackground() {
        val chunkSize = 32
        val list = _channels.value.toList()

        for (start in list.indices step chunkSize) {
            if (validationJob?.isCancelled == true) break
            val chunk = list.subList(start, minOf(start + chunkSize, list.size))

            val updated = chunk.map { ch ->
                withContext(Dispatchers.IO) { validator.validateChannel(ch, limit = 1) }
            }

            val current = _channels.value.toMutableList()
            for (ch in updated) {
                val idx = current.indexOfFirst { it.id == ch.id }
                if (idx >= 0) current[idx] = ch
            }
            _channels.value = current
        }

        if (validationJob?.isCancelled != true) {
            _progress.value = ""
            save()
        }
    }

    fun cancelValidation() {
        validationJob?.cancel()
        _progress.value = ""
    }

    suspend fun clearAndRefresh(scope: CoroutineScope) {
        validationJob?.cancel()
        _channels.value = emptyList()
        _lastRefreshMs.value = null
        context.dataStore.edit { it.clear() }
        refresh(scope)
    }

    fun toggleFavorite(channel: Channel) {
        val list = _channels.value.toMutableList()
        val idx = list.indexOfFirst { it.id == channel.id }
        if (idx >= 0) {
            list[idx] = list[idx].copy(isFavorite = !list[idx].isFavorite)
            _channels.value = list
        }
    }

    suspend fun addRemoteSource(urlString: String, scope: CoroutineScope) {
        _isRefreshing.value = true
        _progress.value = "正在拉取..."
        try {
            val request = okhttp3.Request.Builder()
                .url(urlString)
                .header("User-Agent", "IPTVPlayer/1.0")
                .build()
            val text = withContext(Dispatchers.IO) {
                client.newCall(request).execute().use { it.body?.string() ?: "" }
            }
            if (text.isNotBlank()) {
                mergeChannels(M3UParser.parse(text))
            }
            _progress.value = "已添加，共 ${_channels.value.size} 个频道"
        } catch (e: Exception) {
            _progress.value = "失败: ${e.message}"
        } finally {
            _isRefreshing.value = false
            save()
        }
    }

    fun importFromText(text: String) {
        mergeChannels(M3UParser.parse(text))
    }

    private fun mergeChannels(imported: List<Channel>) {
        val favoriteIds = _channels.value.filter { it.isFavorite }.map { it.id }.toSet()
        val byId = LinkedHashMap(_channels.value.associateBy { it.id })

        for (ch in imported) {
            if (byId.containsKey(ch.id)) {
                val existing = byId[ch.id]!!
                val newSources = ch.sources.filter { s -> existing.sources.none { it.url == s.url } }
                byId[ch.id] = existing.copy(sources = existing.sources + newSources)
            } else {
                byId[ch.id] = ch.copy(isFavorite = ch.id in favoriteIds)
            }
        }
        _channels.value = byId.values.sortedBy { it.name }
    }
}
