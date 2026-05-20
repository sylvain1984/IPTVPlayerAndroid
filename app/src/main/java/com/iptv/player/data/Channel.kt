package com.iptv.player.data

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val groupTitle: String? = null,
    val sources: List<StreamSource> = emptyList(),
    val isFavorite: Boolean = false
) {
    val bestSource: StreamSource?
        get() = sources
            .sortedWith(compareByDescending<StreamSource> { it.score }
                .thenBy { it.latencyMs ?: Int.MAX_VALUE })
            .firstOrNull()
}

data class StreamSource(
    val url: String,
    val userAgent: String? = null,
    val referer: String? = null,
    val score: Double = 0.0,
    val lastCheckedMs: Long? = null,
    val lastWorkedMs: Long? = null,
    val latencyMs: Int? = null
)
