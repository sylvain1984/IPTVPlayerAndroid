package com.iptv.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.player.data.Channel
import com.iptv.player.data.ChannelRepository
import com.iptv.player.data.SourceAggregator
import com.iptv.player.data.StreamSource
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChannelRepository(application)

    val isRefreshing: StateFlow<Boolean> = repository.isRefreshing
    val progress: StateFlow<String>      = repository.progress
    val lastRefreshMs: StateFlow<Long?>  = repository.lastRefreshMs

    private val _selectedChannelId  = MutableStateFlow<String?>(null)
    private val _manualSourceUrl    = MutableStateFlow<String?>(null)
    private val _searchText         = MutableStateFlow("")
    private val _selectedGroup      = MutableStateFlow<String?>(null)
    private val _showOnlyFavorites   = MutableStateFlow(false)
    private val _showAddSourceDialog = MutableStateFlow(false)
    private val _showOnlyRecent      = MutableStateFlow(false)
    private val _showRecommended     = MutableStateFlow(false)

    val showOnlyRecent:    StateFlow<Boolean> = _showOnlyRecent.asStateFlow()
    val showRecommended:   StateFlow<Boolean> = _showRecommended.asStateFlow()

    val selectedChannelId:   StateFlow<String?>  = _selectedChannelId.asStateFlow()
    val searchText:          StateFlow<String>   = _searchText.asStateFlow()
    val selectedGroup:       StateFlow<String?>  = _selectedGroup.asStateFlow()
    val showOnlyFavorites:   StateFlow<Boolean>  = _showOnlyFavorites.asStateFlow()
    val showAddSourceDialog: StateFlow<Boolean>  = _showAddSourceDialog.asStateFlow()

    /** Channel currently selected (null if none) */
    val selectedChannel: StateFlow<Channel?> =
        combine(repository.channels, _selectedChannelId) { chs, id ->
            id?.let { chs.firstOrNull { ch -> ch.id == it } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Best (or manually chosen) stream source for the selected channel */
    val activeSource: StateFlow<StreamSource?> =
        combine(selectedChannel, _manualSourceUrl) { ch, url ->
            ch?.let {
                if (url != null) ch.sources.firstOrNull { s -> s.url == url }
                else ch.bestSource
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Sorted list of group names present in the full channel list */
    val groups: StateFlow<List<String>> =
        repository.channels
            .map { chs -> chs.mapNotNull { it.groupTitle }.distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Last 20 watched channels, most recent first */
    val recentChannels: StateFlow<List<Channel>> =
        combine(repository.channels, repository.watchHistory) { chs, history ->
            history.values
                .sortedByDescending { it.lastWatchedMs }
                .take(20)
                .mapNotNull { rec -> chs.firstOrNull { it.id == rec.channelId } }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Recommended channels based on:
     * 1. Favorites that haven't been watched recently
     * 2. Channels from same groups as favorites / frequently watched
     * 3. Excludes the last 20 recently watched to keep suggestions fresh
     */
    val recommendedChannels: StateFlow<List<Channel>> =
        combine(repository.channels, repository.watchHistory) { chs, history ->
            val recentIds = history.values
                .sortedByDescending { it.lastWatchedMs }
                .take(20)
                .map { it.channelId }
                .toSet()

            val interestGroups: Set<String> = chs
                .filter { it.isFavorite || it.id in recentIds }
                .mapNotNull { it.groupTitle }
                .toSet()

            chs
                .filter { it.id !in recentIds }
                .map { ch ->
                    val watchCount = history[ch.id]?.watchCount ?: 0
                    val score =
                        (if (ch.isFavorite) 200 else 0) +
                        (if (ch.groupTitle in interestGroups) 100 else 0) +
                        watchCount * 10 +
                        ((ch.bestSource?.score ?: 0.0) * 5).toInt()
                    ch to score
                }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }
                .take(20)
                .map { it.first }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Channels after applying search / group / favorites / recent filters */
    private val _baseFiltered: StateFlow<List<Channel>> =
        combine(
            repository.channels,
            repository.watchHistory,
            _searchText,
            _selectedGroup,
            _showOnlyFavorites,
            _showOnlyRecent
        ) { arr ->
            @Suppress("UNCHECKED_CAST")
            val chs      = arr[0] as List<Channel>
            @Suppress("UNCHECKED_CAST")
            val history  = arr[1] as Map<String, com.iptv.player.data.WatchRecord>
            val search   = arr[2] as String
            val group    = arr[3] as String?
            val favOnly  = arr[4] as Boolean
            val recentOnly = arr[5] as Boolean

            val recentIds = if (recentOnly) {
                history.values
                    .sortedByDescending { it.lastWatchedMs }
                    .take(20)
                    .map { it.channelId }
                    .toSet()
            } else emptySet()

            chs.filter { ch ->
                if (recentOnly && ch.id !in recentIds) return@filter false
                if (favOnly && !ch.isFavorite) return@filter false
                if (group != null && ch.groupTitle != group) return@filter false
                if (search.isNotEmpty() && !ch.name.contains(search, ignoreCase = true))
                    return@filter false
                true
            }.let { list ->
                if (recentOnly) list.sortedBy { recentIds.indexOf(it.id) } else list
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Public list shown in the UI — switches to recommended list when that tab is active */
    val filteredChannels: StateFlow<List<Channel>> =
        combine(_showRecommended, _baseFiltered, recommendedChannels) { rec, base, recommended ->
            if (rec) recommended else base
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch {
            repository.load()
            val needRefresh = repository.channels.value.isEmpty() ||
                repository.lastRefreshMs.value.let { last ->
                    last == null || System.currentTimeMillis() - last > 24 * 3_600_000L
                }
            if (needRefresh) repository.refresh(viewModelScope)
        }
    }

    // ── User actions ──────────────────────────────────────────────────────────

    fun selectChannel(id: String?) {
        _selectedChannelId.value = id
        _manualSourceUrl.value = null
        if (id != null) viewModelScope.launch { repository.recordWatch(id) }
    }

    fun selectSource(url: String)  { _manualSourceUrl.value = url }
    fun setSearch(text: String)    { _searchText.value = text }
    fun setGroup(group: String?)   { _selectedGroup.value = group; _showOnlyRecent.value = false; _showRecommended.value = false }
    fun toggleFavoritesFilter()    { _showOnlyFavorites.value = !_showOnlyFavorites.value; _showOnlyRecent.value = false; _showRecommended.value = false }
    fun showAddSource()            { _showAddSourceDialog.value = true }
    fun hideAddSource()            { _showAddSourceDialog.value = false }
    fun showRecent() {
        val on = !_showOnlyRecent.value
        _showOnlyRecent.value = on
        if (on) { _showRecommended.value = false; _selectedGroup.value = null; _showOnlyFavorites.value = false }
    }
    fun toggleRecommended() {
        val on = !_showRecommended.value
        _showRecommended.value = on
        if (on) { _showOnlyRecent.value = false; _selectedGroup.value = null; _showOnlyFavorites.value = false }
    }

    fun toggleFavorite(channel: Channel)  { repository.toggleFavorite(channel) }

    fun refresh()       { viewModelScope.launch { repository.refresh(viewModelScope) } }
    fun clearAndRefresh() { viewModelScope.launch { repository.clearAndRefresh(viewModelScope) } }
    fun cancelValidation() { repository.cancelValidation() }

    fun addRemoteSource(url: String) {
        viewModelScope.launch { repository.addRemoteSource(url, viewModelScope) }
    }

    fun addOptionalSourceGroup(groupKey: String) {
        val urls = SourceAggregator.OPTIONAL_SOURCES[groupKey] ?: return
        viewModelScope.launch {
            urls.forEach { repository.addRemoteSource(it, viewModelScope) }
        }
    }
}
