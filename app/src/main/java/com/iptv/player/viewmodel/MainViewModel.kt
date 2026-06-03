package com.iptv.player.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iptv.player.data.Channel
import com.iptv.player.data.ChannelRepository
import com.iptv.player.data.RtcManager
import com.iptv.player.data.SourceAggregator
import com.iptv.player.data.StreamSource
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ChannelRepository(application)
    val rtcManager = RtcManager(application)

    val isRefreshing: StateFlow<Boolean>  = repository.isRefreshing
    val progress: StateFlow<String>       = repository.progress
    val lastRefreshMs: StateFlow<Long?>   = repository.lastRefreshMs
    val allChannels: StateFlow<List<Channel>> = repository.channels

    private val _selectedChannelId  = MutableStateFlow<String?>(null)
    private val _manualSourceUrl    = MutableStateFlow<String?>(null)
    private val _rawSearchText      = MutableStateFlow("")
    private val _selectedGroup      = MutableStateFlow<String?>(null)
    private val _selectedSubcategory = MutableStateFlow<String?>(null)
    private val _showOnlyFavorites   = MutableStateFlow(false)
    private val _showAddSourceDialog = MutableStateFlow(false)
    private val _showOnlyRecent      = MutableStateFlow(false)
    private val _showRecommended     = MutableStateFlow(false)
    private val _showExclusive       = MutableStateFlow(false)

    val showOnlyRecent:    StateFlow<Boolean> = _showOnlyRecent.asStateFlow()
    val showRecommended:   StateFlow<Boolean> = _showRecommended.asStateFlow()
    val showExclusive:     StateFlow<Boolean> = _showExclusive.asStateFlow()

    val selectedChannelId:   StateFlow<String?>  = _selectedChannelId.asStateFlow()
    val searchText:          StateFlow<String>   = _rawSearchText.asStateFlow()
    private val _debouncedSearch: StateFlow<String> = _rawSearchText
        .debounce(300.milliseconds)
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val selectedGroup:       StateFlow<String?>  = _selectedGroup.asStateFlow()
    val selectedSubcategory: StateFlow<String?>  = _selectedSubcategory.asStateFlow()
    val showOnlyFavorites:   StateFlow<Boolean>  = _showOnlyFavorites.asStateFlow()
    val showAddSourceDialog: StateFlow<Boolean>  = _showAddSourceDialog.asStateFlow()

    /** Channel currently selected (null if none) */
    val selectedChannel: StateFlow<Channel?> =
        combine(repository.channels, _selectedChannelId) { chs, id ->
            id?.let { chs.firstOrNull { ch -> ch.id == it } }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

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
            // Only recompute when channel IDs change, not when source scores update during validation
            .distinctUntilChanged { a, b -> a.map { it.id } == b.map { it.id } }
            .map { chs ->
                chs.filter { !it.isRtc }
                    .mapNotNull { normalizeLabel(it.groupTitle) }
                    .distinct()
                    .sorted()
            }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val subcategoryOrder = listOf("儿童", "地方", "港澳台", "纪录片", "动漫", "音乐", "赛事专区")
    val subcategories: StateFlow<List<String>> =
        repository.channels
            .distinctUntilChanged { a, b -> a.map { it.id } == b.map { it.id } }
            .map { chs -> subcategoryOrder.filter { tag -> chs.any { !it.isRtc && matchesSubcategory(it, tag) } } }
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
        combine(
            repository.channels.debounce(300.milliseconds),
            repository.watchHistory
        ) { chs, history ->
            val recentIds = history.values
                .sortedByDescending { it.lastWatchedMs }
                .take(20)
                .map { it.channelId }
                .toSet()

            val prioritized = prioritizedFeaturedChannels(chs)

            val interestGroups: Set<String> = chs
                .filter { it.isFavorite || it.id in recentIds }
                .mapNotNull { it.groupTitle }
                .toSet()

            val scored = chs
                .filter { it.id !in recentIds }
                .filter { isUsableChannelStrict(it) || isPriorityCctv(it) }
                // Skip channels confirmed bad by validation (best source checked but score < 0.5)
                .filter { ch ->
                    val best = ch.bestSource
                    best?.lastCheckedMs == null || best.score >= 0.5
                }
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

            (prioritized + scored).distinctBy { it.id }.take(20)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Channels after applying search / group / favorites / recent filters */
    private val _baseFiltered: StateFlow<List<Channel>> =
        combine(
            repository.channels,
            repository.watchHistory,
            _debouncedSearch,
            _selectedGroup,
            _selectedSubcategory,
            _showOnlyFavorites,
            _showOnlyRecent,
            _showExclusive
        ) { arr ->
            @Suppress("UNCHECKED_CAST")
            val chs      = arr[0] as List<Channel>
            @Suppress("UNCHECKED_CAST")
            val history  = arr[1] as Map<String, com.iptv.player.data.WatchRecord>
            val search   = arr[2] as String
            val group    = arr[3] as String?
            val subcategory = arr[4] as String?
            val favOnly  = arr[5] as Boolean
            val recentOnly = arr[6] as Boolean
            val exclusiveOnly = arr[7] as Boolean

            val recentIds = if (recentOnly) {
                history.values
                    .sortedByDescending { it.lastWatchedMs }
                    .take(20)
                    .map { it.channelId }
                    .toSet()
            } else emptySet()

            chs.filter { ch ->
                if (recentOnly && ch.id !in recentIds) return@filter false
                if (exclusiveOnly && !ch.isRtc) return@filter false
                if (!exclusiveOnly && ch.isRtc) return@filter false
                if (favOnly && !ch.isFavorite) return@filter false
                if (group != null && ch.groupTitle != group) return@filter false
                if (subcategory != null && !matchesSubcategory(ch, subcategory)) return@filter false
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
            // Determine refresh need from cached state before any network call
            val needRefresh = repository.channels.value.isEmpty() ||
                repository.lastRefreshMs.value.let { last ->
                    last == null || System.currentTimeMillis() - last > 24 * 3_600_000L
                }
            // Live channels and M3U refresh run in parallel
            launch { repository.refreshLiveChannels() }
            if (needRefresh) repository.refresh(viewModelScope)
        }
        viewModelScope.launch {
            // Start polling after a brief delay so startup network burst settles first
            kotlinx.coroutines.delay(10_000)
            while (isActive) {
                repository.refreshLiveChannels()
                kotlinx.coroutines.delay(3_000)
            }
        }
    }

    // ── User actions ──────────────────────────────────────────────────────────

    fun selectChannel(id: String?) {
        _selectedChannelId.value = id
        _manualSourceUrl.value = null
        if (id != null) viewModelScope.launch { repository.recordWatch(id) }
    }

    fun selectSource(url: String)  { _manualSourceUrl.value = url }
    fun setSearch(text: String)    { _rawSearchText.value = text }
    fun setGroup(group: String?)   { _selectedGroup.value = group; _showOnlyRecent.value = false; _showRecommended.value = false; _showExclusive.value = false }
    fun setSubcategory(tag: String?) { _selectedSubcategory.value = tag; _showOnlyRecent.value = false; _showRecommended.value = false; _showExclusive.value = false }
    fun toggleFavoritesFilter()    { _showOnlyFavorites.value = !_showOnlyFavorites.value; _showOnlyRecent.value = false; _showRecommended.value = false; _showExclusive.value = false; _selectedSubcategory.value = null }
    fun showAddSource()            { _showAddSourceDialog.value = true }
    fun hideAddSource()            { _showAddSourceDialog.value = false }
    fun showRecent() {
        val on = !_showOnlyRecent.value
        _showOnlyRecent.value = on
        if (on) { _showRecommended.value = false; _selectedGroup.value = null; _selectedSubcategory.value = null; _showOnlyFavorites.value = false; _showExclusive.value = false }
    }
    fun toggleRecommended() {
        val on = !_showRecommended.value
        _showRecommended.value = on
        if (on) { _showOnlyRecent.value = false; _selectedGroup.value = null; _selectedSubcategory.value = null; _showOnlyFavorites.value = false; _showExclusive.value = false }
    }
    fun toggleExclusive() {
        val on = !_showExclusive.value
        _showExclusive.value = on
        if (on) { _showOnlyRecent.value = false; _showRecommended.value = false; _selectedGroup.value = null; _selectedSubcategory.value = null; _showOnlyFavorites.value = false }
    }

    fun toggleFavorite(channel: Channel)  {
        viewModelScope.launch { repository.toggleFavorite(channel) }
    }

    fun trySwitchToNextSource(): Boolean {
        val channel = selectedChannel.value ?: return false
        val currentUrl = activeSource.value?.url ?: return false
        if (channel.sources.size <= 1) return false

        val currentIndex = channel.sources.indexOfFirst { it.url == currentUrl }
        if (currentIndex < 0) return false

        val nextSource = channel.sources.drop(currentIndex + 1).firstOrNull()
            ?: channel.sources.firstOrNull { it.url != currentUrl }
            ?: return false

        _manualSourceUrl.value = nextSource.url
        return true
    }

    fun refresh()         { viewModelScope.launch { repository.refresh(viewModelScope) } }
    fun clearAndRefresh() { viewModelScope.launch { repository.clearAndRefresh(viewModelScope) } }
    fun cancelValidation() { repository.cancelValidation() }

    private fun normalizeLabel(value: String?): String? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return if (trimmed.any { isCjk(it) || it.isLetterOrDigit() }) trimmed else null
    }

    private fun isCjk(ch: Char): Boolean {
        val block = Character.UnicodeBlock.of(ch) ?: return false
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION ||
            block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS
    }

    /** Called on playback error: re-validate all sources for the current channel.
     *  If the manually selected source turns out to be bad, reset to bestSource. */
    fun revalidateCurrentChannel() {
        val channelId = _selectedChannelId.value ?: return
        viewModelScope.launch {
            repository.revalidateChannel(channelId)
            // If we're locked onto a manually selected source that scored badly, release it
            val manualUrl = _manualSourceUrl.value ?: return@launch
            val channel = repository.channels.value.firstOrNull { it.id == channelId }
            val manualScore = channel?.sources?.firstOrNull { it.url == manualUrl }?.score ?: 0.0
            if (manualScore < 0.5) _manualSourceUrl.value = null
        }
    }

    fun addRemoteSource(url: String) {
        viewModelScope.launch { repository.addRemoteSource(url, viewModelScope) }
    }

    override fun onCleared() {
        super.onCleared()
        rtcManager.release()
    }

    fun addOptionalSourceGroup(groupKey: String) {
        val urls = SourceAggregator.OPTIONAL_SOURCES[groupKey] ?: return
        viewModelScope.launch {
            urls.forEach { repository.addRemoteSource(it, viewModelScope) }
        }
    }

    private fun prioritizedFeaturedChannels(channels: List<Channel>): List<Channel> {
        val cctv = prioritizedCctvChannels(channels)
        // Include top Migu channels regardless of validation status
        val migu = channels
            .filter { ch ->
                val name = ch.name
                val group = ch.groupTitle ?: ""
                name.contains("咪咕") || group.contains("咪咕")
            }
            .sortedWith(compareBy(
                // 4K first, then numbered channels in order
                { if (it.name.contains("4K") || it.name.contains("4k")) 0 else 1 },
                { it.name }
            ))
            .take(6)
        return (cctv + migu).distinctBy { it.id }
    }

    private fun prioritizedCctvChannels(channels: List<Channel>): List<Channel> {
        val candidates = channels.filter { isUsableChannelStrict(it) || isPriorityCctv(it) }
        val cctv5Hd = candidates.filter { channelType(it) == PriorityType.CCTV5_HD }.sortedBy { cctvRank(it) }
        val cctv5 = preferred720pOnly(candidates.filter { channelType(it) == PriorityType.CCTV5 }, "cctv5")
            .sortedBy { cctvRank(it) }
        val cctv5Plus = preferred720pOnly(candidates.filter { channelType(it) == PriorityType.CCTV5_PLUS }, "cctv5+")
            .sortedBy { cctvRank(it) }
        val cctvOther = channels.filter { isUsableChannel(it) && channelType(it) == PriorityType.CCTV_OTHER }.sortedBy { cctvRank(it) }
        return cctv5Hd + cctv5 + cctv5Plus + cctvOther
    }

    private enum class PriorityType { NONE, CCTV5_HD, CCTV5, CCTV5_PLUS, CCTV_OTHER }

    private fun channelType(channel: Channel): PriorityType {
        val normalized = normalizedCctvName(channel.name)
        val isCctv5 = normalized.contains("cctv5")
        val isCctv5Plus = normalized.contains("cctv5+")
        val isHd = normalized.contains("高清") || normalized.contains("hd")
        if (isCctv5 && isHd) return PriorityType.CCTV5_HD
        if (isCctv5Plus) return PriorityType.CCTV5_PLUS
        if (isCctv5) return PriorityType.CCTV5
        if (normalized.contains("cctv")) return PriorityType.CCTV_OTHER
        return PriorityType.NONE
    }

    private fun normalizedCctvName(name: String): String {
        return name.lowercase()
            .replace("-", "")
            .replace("－", "")
            .replace("＋", "+")
            .replace("_", "")
            .replace(" ", "")
    }

    private fun cctvRank(channel: Channel): Int {
        val n = normalizedCctvName(channel.name)
        return when {
            n == "cctv5高清" -> 0
            n == "cctv5" -> 1
            n == "cctv5+" -> 2
            n.contains("cctv5高清") -> 3
            n.contains("cctv5+") -> 4
            n.contains("cctv5") -> 5
            else -> 10
        }
    }

    private fun preferred720pOnly(channels: List<Channel>, key: String): List<Channel> {
        val exact = channels.filter { matchesCctvKey(normalizedCctvName(it.name), key) }
        val only720 = exact.filter { normalizedCctvName(it.name).contains("720p") }
        return if (only720.isEmpty()) exact else only720
    }

    private fun matchesCctvKey(normalizedName: String, key: String): Boolean {
        if (key == "cctv5") {
            // cctv5 组不应吸入 cctv5+
            return normalizedName.contains("cctv5") && !normalizedName.contains("cctv5+")
        }
        if (key == "cctv5+") {
            return normalizedName.contains("cctv5+")
        }
        return normalizedName.contains(key)
    }

    private fun isUsableChannel(channel: Channel): Boolean {
        val best = channel.bestSource ?: return false
        return best.lastCheckedMs == null || best.score >= 0.5
    }

    private fun isUsableChannelStrict(channel: Channel): Boolean {
        return channel.sources.any { it.lastCheckedMs != null && it.score >= 0.5 }
    }

    private fun isPriorityCctv(channel: Channel): Boolean {
        if (channel.sources.isEmpty()) return false
        val n = normalizedCctvName(channel.name)
        return n.contains("cctv5")
    }

    private fun matchesSubcategory(channel: Channel, tag: String): Boolean {
        val text = "${channel.name} ${channel.groupTitle ?: ""}".lowercase()
        return when (tag) {
            "儿童" -> listOf("儿童", "少儿", "卡通", "动漫", "动画", "亲子", "kid").any { text.contains(it) }
            "地方" -> listOf("卫视", "地方", "都市", "公共", "新闻综合", "经济生活").any { text.contains(it) }
            "港澳台" -> listOf("香港", "澳门", "台湾", "tvb", "翡翠", "hk", "tw").any { text.contains(it) }
            "纪录片" -> listOf("纪录", "documentary", "discovery", "国家地理").any { text.contains(it) }
            "动漫" -> listOf("动漫", "动画", "anime", "二次元", "卡通").any { text.contains(it) }
            "音乐" -> listOf("音乐", "music", "mtv", "演唱会").any { text.contains(it) }
            "赛事专区" -> listOf("英超", "西甲", "欧冠", "nba", "cba", "ufc", "f1", "nfl", "mlb").any { text.contains(it) }
            else -> false
        }
    }
}
