package com.example.videodownloader.core.net

internal const val MOBILE_MEDIA_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36"

internal fun buildMediaPreviewHeaderMap(url: String): Map<String, String> {
    val lower = url.lowercase()
    val headers = linkedMapOf<String, String>()
    headers["User-Agent"] = MOBILE_MEDIA_USER_AGENT
    if (isDouyinRelatedUrl(lower)) {
        headers["Referer"] = "https://www.douyin.com/"
    } else {
        headers.putAll(buildXMediaHeaderMap(url))
    }
    return headers
}

internal fun isDouyinRelatedUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.contains("douyin.com") ||
        lower.contains("iesdouyin.com") ||
        lower.contains("douyinpic.com") ||
        lower.contains("douyinstatic.com") ||
        lower.contains("snssdk.com") ||
        lower.contains("amemv.com") ||
        lower.contains("ibytedtos.com") ||
        lower.contains("byteimg.com") ||
        lower.contains("tos-cn-")
}
