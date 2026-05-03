package com.example.videodownloader.core.net

internal fun isXMediaUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("x.com") ||
        lower.contains("twitter.com") ||
        lower.contains("twimg.com") ||
        lower.contains("twimg.cn") ||
        lower.contains("t.co")
}

internal fun buildXMediaHeaderMap(
    url: String,
    cookie: String? = null,
): Map<String, String> {
    if (!isXMediaUrl(url)) return emptyMap()
    return linkedMapOf<String, String>().apply {
        put("Referer", "https://x.com/")
        if (!cookie.isNullOrBlank()) {
            put("Cookie", cookie)
        }
    }
}
