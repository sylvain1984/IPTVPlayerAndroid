package com.iptv.player.data

object M3UParser {

    private val NORMALIZED_GROUPS = listOf("体育", "国际", "娱乐", "新闻")

    private fun normalizeGroup(raw: String?): String {
        val g = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return "娱乐"
        val lower = g.lowercase()

        val sports = listOf("体育", "sport", "足球", "篮球", "网球", "赛事", "运动",
            "football", "basketball", "tennis", "golf", "esport", "电竞", "奥运", "olympic")
        if (sports.any { lower.contains(it) }) return "体育"

        val news = listOf("新闻", "news", "资讯", "财经", "cctv-13", "cctv13",
            "凤凰资讯", "cnn", "bbc", "时事", "纪录")
        if (news.any { lower.contains(it) }) return "新闻"

        val intl = listOf("国际", "海外", "港", "澳", "台", "tvb", "hk", "tw",
            "international", "global", "world", "欧美", "日本", "韩国", "英国",
            "美国", "france", "germany", "japan", "korea", "uk", "us", "foreign", "境外")
        if (intl.any { lower.contains(it) }) return "国际"

        return "娱乐"
    }

    fun parse(content: String): List<Channel> {
        val byId = LinkedHashMap<String, Channel>()

        var name: String? = null
        var tvgId: String? = null
        var logo: String? = null
        var group: String? = null
        var userAgent: String? = null
        var referer: String? = null

        for (rawLine in content.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTM3U") -> continue

                line.startsWith("#EXTINF:") -> {
                    tvgId = extractAttr(line, "tvg-id")
                    logo = extractAttr(line, "tvg-logo")
                    group = extractAttr(line, "group-title")
                    userAgent = null
                    referer = null
                    val commaIdx = line.lastIndexOf(',')
                    if (commaIdx >= 0) {
                        name = line.substring(commaIdx + 1).trim()
                    }
                }

                line.startsWith("#EXTVLCOPT:") -> {
                    val opt = line.removePrefix("#EXTVLCOPT:")
                    if (opt.startsWith("http-user-agent=")) userAgent = opt.removePrefix("http-user-agent=")
                    if (opt.startsWith("http-referrer=")) referer = opt.removePrefix("http-referrer=")
                }

                line.startsWith("#") -> continue

                name != null && isValidUrl(line) -> {
                    val id = tvgId?.takeIf { it.isNotEmpty() } ?: name!!
                    val src = StreamSource(url = line, userAgent = userAgent, referer = referer)

                    if (byId.containsKey(id)) {
                        val existing = byId[id]!!
                        if (existing.sources.none { it.url == line }) {
                            byId[id] = existing.copy(sources = existing.sources + src)
                        }
                    } else {
                        byId[id] = Channel(
                            id = id,
                            name = name!!,
                            logoUrl = logo,
                            groupTitle = normalizeGroup(group),
                            sources = listOf(src)
                        )
                    }

                    name = null; tvgId = null; logo = null; group = null
                    userAgent = null; referer = null
                }
            }
        }

        return byId.values.toList()
    }

    private fun extractAttr(line: String, attrName: String): String? {
        val pattern = Regex("""$attrName="([^"]*)"""")
        return pattern.find(line)?.groupValues?.getOrNull(1)
    }

    private fun isValidUrl(s: String): Boolean {
        return s.startsWith("http://") || s.startsWith("https://")
                || s.startsWith("rtmp://") || s.startsWith("rtsp://")
    }
}
