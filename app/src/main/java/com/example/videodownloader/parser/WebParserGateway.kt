package com.example.videodownloader.parser

import android.util.Log
import android.util.Base64
import com.example.videodownloader.domain.model.ParsedVideoInfo
import com.example.videodownloader.domain.model.VideoFormat
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

class WebParserGateway(
    private val xCookieProvider: (() -> String?)? = null,
) : ParserGateway {
    private val tag = "WebParserGateway"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val xClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun parse(url: String): ParsedVideoInfo {
        Log.d(tag, "parse start: $url")

        parseBlueGayHash(url)?.let { return it }

        parseDirectMedia(url)?.let { return it }

        if (isDouyinHost(url)) {
            parseDouyin(url)?.let { return it }
            parseFromMeta(url)?.let { return it }
            Log.w(tag, "parse failed for douyin url=$url")
            throw IllegalArgumentException("未能解析到可下载视频，请更换链接后重试")
        }

        if (isXHost(url)) {
            val startAt = System.nanoTime()
            parseXFast(url)?.let {
                Log.i(tag, "parseXFast success, cost=${elapsedMs(startAt)}ms, url=$url")
                return it
            }
            Log.w(tag, "parse failed for x, cost=${elapsedMs(startAt)}ms, url=$url")
            throw IllegalArgumentException("当前网络可能无法访问 X 资源，请稍后重试或更换网络")
        }

        parseDouyin(url)?.let { return it }
        parseXBroadcast(url)?.let { return it }
        parseTwitterSyndication(url)?.let { return it }
        parseTwitterFx(url)?.let { return it }
        parseFromMeta(url)?.let { return it }

        Log.w(tag, "parse failed: no downloadable format, url=$url")
        throw IllegalArgumentException("未能解析到可下载视频，请更换链接后重试")
    }

    private fun parseBlueGayHash(url: String): ParsedVideoInfo? {
        val lowerUrl = url.lowercase()
        if (!lowerUrl.contains("kstore.vip/") || !lowerUrl.contains(".html#")) {
            return null
        }

        val fragment = url.substringAfter('#', "").substringBefore('#').trim()
        if (fragment.isBlank()) return null

        val standardBase64 = fragment
            .replace('-', '+')
            .replace('_', '/')
            .replace('.', '=')
            .filter { it.isLetterOrDigit() || it == '+' || it == '/' || it == '=' }

        val payloadText = runCatching {
            val decoded = Base64.decode(standardBase64, Base64.DEFAULT)
            String(decoded, Charsets.UTF_8)
        }.getOrNull() ?: return null

        val payload = runCatching { JSONObject(payloadText) }.getOrNull() ?: return null
        val rawVideoUrl = payload.optString("url").trim()
        if (rawVideoUrl.isBlank()) return null

        val normalizedVideoUrl = normalizeVideoUrl(rawVideoUrl)
        val cleanVideoUrl = normalizedVideoUrl.substringBefore('?').substringBefore('#')
        if (!cleanVideoUrl.endsWith(".mp4", ignoreCase = true) &&
            !cleanVideoUrl.endsWith(".m3u8", ignoreCase = true)
        ) {
            return null
        }

        val ext = if (cleanVideoUrl.endsWith(".m3u8", ignoreCase = true)) "m3u8" else "mp4"
        val title = sanitizeTitle(payload.optString("title")).ifBlank { "web_video" }
        return ParsedVideoInfo(
            title = title,
            coverUrl = null,
            formats = listOf(
                VideoFormat(
                    formatId = "bluegay_hash",
                    resolution = inferResolution(normalizedVideoUrl),
                    ext = ext,
                    sizeText = null,
                    downloadUrl = normalizedVideoUrl,
                    downloadable = true,
                ),
            ),
        )
    }

    private fun parseDirectMedia(url: String): ParsedVideoInfo? {
        val clean = url.substringBefore("?")
        if (!clean.endsWith(".mp4", ignoreCase = true) && !clean.endsWith(".m3u8", ignoreCase = true)) {
            return null
        }

        val ext = if (clean.endsWith(".m3u8", true)) "m3u8" else "mp4"
        return ParsedVideoInfo(
            title = "直链视频",
            coverUrl = null,
            formats = listOf(
                VideoFormat(
                    formatId = "direct",
                    resolution = "原始",
                    ext = ext,
                    sizeText = null,
                    downloadUrl = url,
                    downloadable = true,
                ),
            ),
        )
    }

    private fun parseDouyin(url: String): ParsedVideoInfo? {
        val candidateIds = linkedSetOf<String>()
        extractDouyinVideoId(url)?.let { candidateIds += it }

        if (!isDouyinHost(url) && candidateIds.isEmpty()) {
            return null
        }

        Log.d(tag, "parseDouyin: ids from url=${candidateIds.joinToString(",")}")

        val htmlCandidates = linkedSetOf<String>()

        val firstPage = httpGetWithFinalUrl(url)
        if (firstPage != null) {
            if (firstPage.body.isNotBlank()) {
                htmlCandidates += firstPage.body
            }
            extractDouyinVideoId(firstPage.finalUrl)?.let { candidateIds += it }
            extractDouyinVideoId(firstPage.body)?.let { candidateIds += it }
            Log.d(tag, "parseDouyin: firstPage final=${firstPage.finalUrl}, ids=${candidateIds.joinToString(",")}, htmlCount=${htmlCandidates.size}")
        }

        candidateIds.toList().forEach { videoId ->
            val shareUrl = "https://www.iesdouyin.com/share/video/$videoId/"
            val sharePage = httpGetWithFinalUrl(shareUrl)
            if (sharePage?.body?.isNotBlank() == true) {
                htmlCandidates += sharePage.body
                extractDouyinVideoId(sharePage.finalUrl)?.let { candidateIds += it }
                extractDouyinVideoId(sharePage.body)?.let { candidateIds += it }
            }
        }

        candidateIds.forEach { videoId ->
            parseDouyinByApi(videoId)?.let { return it }
        }

        val htmlResult = parseDouyinFromHtml(htmlCandidates)
        if (htmlResult == null) {
            Log.w(tag, "parseDouyin: html parse failed, ids=${candidateIds.joinToString(",")}")
        }
        return htmlResult
    }

    private fun parseDouyinByApi(videoId: String): ParsedVideoInfo? {
        val endpoints = listOf(
            "https://www.iesdouyin.com/web/api/v2/aweme/iteminfo/?item_ids=$videoId",
            "https://www.iesdouyin.com/aweme/v1/web/aweme/detail/?aweme_id=$videoId",
            "https://www.douyin.com/aweme/v1/web/aweme/detail/?aweme_id=$videoId&aid=6383&version_name=23.5.0&device_platform=android&os_version=2333",
        )

        endpoints.forEach { endpoint ->
            val body = httpGet(endpoint) ?: return@forEach
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@forEach

            val item = json.optJSONArray("item_list")?.optJSONObject(0)
                ?: json.optJSONObject("aweme_detail")
                ?: json.optJSONObject("aweme")
                ?: return@forEach

            parseDouyinItem(item)?.let { return it }
        }

        Log.w(tag, "parseDouyinByApi: no result for id=$videoId")
        return null
    }

    private fun parseDouyinItem(item: JSONObject): ParsedVideoInfo? {
        val video = item.optJSONObject("video") ?: return null

        val urls = linkedSetOf<String>()
        urls += extractUrlList(video.optJSONObject("play_addr"))
        urls += extractUrlList(video.optJSONObject("play_addr_h264"))
        urls += extractUrlList(video.optJSONObject("play_addr_265"))
        urls += extractUrlList(video.optJSONObject("download_addr"))
        urls += extractUrlList(video.optJSONObject("download_suffix_logo_addr"))

        val bitRates = video.optJSONArray("bit_rate")
        if (bitRates != null) {
            for (i in 0 until bitRates.length()) {
                val bitRate = bitRates.optJSONObject(i) ?: continue
                urls += extractUrlList(bitRate.optJSONObject("play_addr"))
            }
        }

        val normalizedUrls = urls
            .map(::normalizeVideoUrl)
            .filter(::isLikelyVideoUrl)
            .distinct()

        if (normalizedUrls.isEmpty()) return null

        val title = sanitizeTitle(item.optString("desc")).ifBlank { "抖音视频" }
        val cover = extractBestCoverUrl(video)

        val formats = normalizedUrls.mapIndexed { index, link ->
            VideoFormat(
                formatId = "douyin_api_$index",
                resolution = inferResolution(link),
                ext = if (link.contains(".m3u8", true)) "m3u8" else "mp4",
                sizeText = null,
                downloadUrl = link,
                downloadable = true,
            )
        }

        return ParsedVideoInfo(
            title = title,
            coverUrl = cover,
            formats = preferMp4Formats(formats),
        )
    }

    private fun parseDouyinFromHtml(htmlCandidates: Collection<String>): ParsedVideoInfo? {
        if (htmlCandidates.isEmpty()) return null

        val videoUrls = linkedSetOf<String>()
        var title: String? = null
        var cover: String? = null

        htmlCandidates.forEach { html ->
            if (title.isNullOrBlank()) {
                title = extractMeta(html, "og:title") ?: extractTitleTag(html) ?: extractJsonField(html, "desc")
            }
            if (cover.isNullOrBlank()) {
                cover = extractMeta(html, "og:image") ?: extractJsonField(html, "cover")
            }
            videoUrls += extractDouyinVideoUrls(html)
        }

        if (videoUrls.isEmpty()) return null

        val formats = videoUrls.mapIndexed { index, link ->
            val normalized = normalizeVideoUrl(link)
            VideoFormat(
                formatId = "douyin_html_$index",
                resolution = inferResolution(normalized),
                ext = if (normalized.contains(".m3u8", true)) "m3u8" else "mp4",
                sizeText = null,
                downloadUrl = normalized,
                downloadable = true,
            )
        }.distinctBy { it.downloadUrl }

        return ParsedVideoInfo(
            title = sanitizeTitle(title).ifBlank { "抖音视频" },
            coverUrl = cover?.takeIf { it.isNotBlank() },
            formats = preferMp4Formats(formats),
        )
    }

    private fun parseXBroadcast(url: String): ParsedVideoInfo? {
        val broadcastId = extractBroadcastId(url) ?: return null
        val page = httpGetWithFinalUrl(url) ?: return null
        val html = page.body

        val m3u8Urls = extractBroadcastM3u8Urls(html)
            .map(::normalizeVideoUrl)
            .distinct()
            .take(6)
        if (m3u8Urls.isEmpty()) {
            Log.w(tag, "parseXBroadcast: no m3u8 found, broadcastId=$broadcastId")
            return null
        }

        val title = extractMeta(html, "og:title")
            ?: extractTitleTag(html)
            ?: "X 直播回放"
        val cover = extractMeta(html, "og:image")
            ?: extractMeta(html, "twitter:image")
        val durationSec = parseBroadcastDurationSec(html)

        val formats = m3u8Urls.mapIndexed { index, playlistUrl ->
            VideoFormat(
                formatId = "x_broadcast_$index",
                resolution = inferResolution(playlistUrl),
                ext = "m3u8",
                sizeText = "HLS",
                downloadUrl = playlistUrl,
                durationSec = durationSec,
                downloadable = true,
            )
        }

        Log.d(tag, "parseXBroadcast: variants=${formats.size}, broadcastId=$broadcastId")
        return ParsedVideoInfo(
            title = sanitizeTitle(title).ifBlank { "X 直播回放" },
            coverUrl = cover?.takeIf { it.isNotBlank() },
            formats = formats,
        )
    }

    private fun parseTwitterSyndication(url: String): ParsedVideoInfo? {
        val tweetId = extractTweetId(url) ?: return null
        val api = "https://cdn.syndication.twimg.com/tweet-result?id=$tweetId&lang=zh-cn"
        val body = httpGet(api) ?: run {
            Log.w(tag, "parseTwitterSyndication: empty response for tweetId=$tweetId")
            return null
        }
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return null

        val title = json.optString("text").ifBlank { "X 视频" }
        val durationSec = parseDurationSec(
            videoObj = json.optJSONObject("video"),
            mediaArray = json.optJSONArray("mediaDetails"),
        )
        val variants = mutableListOf<VideoFormat>()
        val videoObj = json.optJSONObject("video")
        val variantArray = videoObj?.optJSONArray("variants")
        if (variantArray != null) {
            variants += parseTwitterVariants(variantArray, durationSec)
        }
        if (variants.isEmpty()) {
            val mediaArray = json.optJSONArray("mediaDetails")
            if (mediaArray != null) {
                variants += parseMediaDetails(mediaArray, durationSec)
            }
        }
        if (variants.isEmpty()) {
            Log.w(tag, "parseTwitterSyndication: no variants, tweetId=$tweetId")
            return null
        }

        Log.d(tag, "parseTwitterSyndication: variants=${variants.size}, tweetId=$tweetId")

        return ParsedVideoInfo(
            title = sanitizeTitle(title).ifBlank { "X 视频" },
            coverUrl = videoObj?.optString("poster")?.takeIf { it.isNotBlank() },
            formats = preferMp4Formats(variants.distinctBy { it.downloadUrl }),
        )
    }

    private suspend fun parseXFast(url: String): ParsedVideoInfo? {
        val startAt = System.nanoTime()
        extractBroadcastId(url)?.let {
            val broadcastStart = System.nanoTime()
            parseXBroadcast(url)?.let {
                Log.i(tag, "parseXFast hit=broadcast, cost=${elapsedMs(broadcastStart)}ms")
                return it
            }
            val metaStart = System.nanoTime()
            parseFromMeta(url)?.let {
                Log.i(tag, "parseXFast hit=broadcast_meta, cost=${elapsedMs(metaStart)}ms")
                return it
            }
            Log.w(tag, "parseXFast broadcast empty, total=${elapsedMs(startAt)}ms")
            return null
        }

        extractTweetId(url)?.let {
            val statusStart = System.nanoTime()
            parseXStatusFast(url)?.let {
                Log.i(tag, "parseXFast hit=status_parallel, cost=${elapsedMs(statusStart)}ms")
                return it
            }
            val metaStart = System.nanoTime()
            parseFromMeta(url)?.let {
                Log.i(tag, "parseXFast hit=status_meta, cost=${elapsedMs(metaStart)}ms")
                return it
            }
            Log.w(tag, "parseXFast status empty, total=${elapsedMs(startAt)}ms")
            return null
        }

        val syndicationStart = System.nanoTime()
        parseTwitterSyndication(url)?.let {
            Log.i(tag, "parseXFast hit=fallback_syndication, cost=${elapsedMs(syndicationStart)}ms")
            return it
        }
        val fxStart = System.nanoTime()
        parseTwitterFx(url)?.let {
            Log.i(tag, "parseXFast hit=fallback_fx, cost=${elapsedMs(fxStart)}ms")
            return it
        }
        val metaStart = System.nanoTime()
        parseFromMeta(url)?.let {
            Log.i(tag, "parseXFast hit=fallback_meta, cost=${elapsedMs(metaStart)}ms")
            return it
        }
        Log.w(tag, "parseXFast no result, total=${elapsedMs(startAt)}ms")
        return null
    }

    private suspend fun parseXStatusFast(url: String): ParsedVideoInfo? = coroutineScope {
        val totalStart = System.nanoTime()
        val result = withTimeoutOrNull(X_STATUS_TOTAL_TIMEOUT_MS) {
            val attempts = listOf(
                async(Dispatchers.IO) {
                    val startAt = System.nanoTime()
                    val parsed = parseTwitterSyndication(url)
                    Log.d(
                        tag,
                        "parseXStatusFast branch=syndication hit=${parsed != null} cost=${elapsedMs(startAt)}ms",
                    )
                    parsed
                },
                async(Dispatchers.IO) {
                    val startAt = System.nanoTime()
                    val parsed = parseTwitterFx(url)
                    Log.d(tag, "parseXStatusFast branch=fx hit=${parsed != null} cost=${elapsedMs(startAt)}ms")
                    parsed
                },
            )
            firstNonNull(attempts)
        }
        if (result == null) {
            Log.w(tag, "parseXStatusFast timeout/empty, cost=${elapsedMs(totalStart)}ms, url=$url")
        } else {
            Log.i(tag, "parseXStatusFast success, cost=${elapsedMs(totalStart)}ms, url=$url")
        }
        result
    }

    private suspend fun parseTwitterFx(url: String): ParsedVideoInfo? = coroutineScope {
        val tweetId = extractTweetId(url) ?: return@coroutineScope null
        val handle = extractTweetHandle(url)
        val path = if (handle.isNullOrBlank()) "i/status/$tweetId" else "$handle/status/$tweetId"
        val mirrors = listOf("fxtwitter.com", "vxtwitter.com", "fixupx.com")

        val totalStart = System.nanoTime()
        val result = withTimeoutOrNull(X_FX_TOTAL_TIMEOUT_MS) {
            val attempts = mirrors.map { host ->
                async(Dispatchers.IO) {
                    val startAt = System.nanoTime()
                    val parsed = parseFromMeta("https://$host/$path")
                    Log.d(tag, "parseTwitterFx mirror=$host hit=${parsed != null} cost=${elapsedMs(startAt)}ms")
                    parsed
                }
            }
            firstNonNull(attempts)
        }
        if (result == null) {
            Log.w(tag, "parseTwitterFx empty/timeout, cost=${elapsedMs(totalStart)}ms, tweetId=$tweetId")
        } else {
            Log.i(tag, "parseTwitterFx success, cost=${elapsedMs(totalStart)}ms, tweetId=$tweetId")
        }
        result
    }

    private suspend fun <T> firstNonNull(attempts: List<Deferred<T?>>): T? {
        val pending = attempts.toMutableList()
        return try {
            while (pending.isNotEmpty()) {
                val (finished, value) = select<Pair<Deferred<T?>, T?>> {
                    pending.forEach { deferred ->
                        deferred.onAwait { result -> deferred to result }
                    }
                }
                pending.remove(finished)
                if (value != null) {
                    return value
                }
            }
            null
        } finally {
            attempts.forEach { deferred ->
                if (deferred.isActive) {
                    deferred.cancel()
                }
            }
        }
    }

    private fun parseFromMeta(url: String): ParsedVideoInfo? {
        val html = httpGet(url) ?: return null
        val ogVideo = extractMeta(html, "og:video")
            ?: extractMeta(html, "og:video:url")
            ?: extractMeta(html, "twitter:player:stream")
        if (ogVideo.isNullOrBlank()) {
            Log.w(tag, "parseFromMeta: no og video, url=$url")
            return null
        }

        val title = extractMeta(html, "og:title")?.trim()?.takeIf { it.isNotBlank() } ?: "网页视频"
        val cover = extractMeta(html, "og:image")?.takeIf { it.isNotBlank() }
        val link = normalizeVideoUrl(ogVideo)
        val ext = if (link.contains(".m3u8", true)) "m3u8" else "mp4"

        return ParsedVideoInfo(
            title = sanitizeTitle(title),
            coverUrl = cover,
            formats = listOf(
                VideoFormat(
                    formatId = "meta",
                    resolution = "原始",
                    ext = ext,
                    sizeText = null,
                    downloadUrl = link,
                    downloadable = true,
                ),
            ),
        )
    }

    private fun parseTwitterVariants(array: JSONArray, durationSec: Double?): List<VideoFormat> {
        val result = mutableListOf<VideoFormat>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val src = normalizeVideoUrl(item.optString("src"))
            if (src.isBlank()) continue

            val bitrate = item.optInt("bitrate", -1)
            val resolution = when {
                bitrate >= 2_000_000 -> "1080p"
                bitrate >= 1_000_000 -> "720p"
                bitrate >= 500_000 -> "480p"
                else -> "原始"
            }

            result += VideoFormat(
                formatId = "x_$i",
                resolution = resolution,
                ext = if (src.contains(".m3u8", true)) "m3u8" else "mp4",
                sizeText = if (bitrate > 0) "${bitrate / 1000}kbps" else null,
                downloadUrl = src,
                durationSec = durationSec,
                downloadable = true,
            )
        }

        return result.sortedByDescending { it.sizeText ?: "" }
    }

    private fun parseMediaDetails(array: JSONArray, durationSec: Double?): List<VideoFormat> {
        val result = mutableListOf<VideoFormat>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val mediaUrl = item.optString("media_url_https").ifBlank { item.optString("media_url") }
            val normalized = normalizeVideoUrl(mediaUrl)
            if (normalized.isBlank()) continue

            val type = item.optString("type")
            if (type != "video" && !normalized.contains(".mp4", true)) continue

            result += VideoFormat(
                formatId = "media_$i",
                resolution = "原始",
                ext = if (normalized.contains(".m3u8", true)) "m3u8" else "mp4",
                sizeText = null,
                downloadUrl = normalized,
                durationSec = durationSec,
                downloadable = true,
            )
        }

        return result
    }

    private fun parseBroadcastDurationSec(html: String): Double? {
        val patterns = listOf(
            Regex("duration_ms\"\\s*:\\s*(\\d{3,})"),
            Regex("durationMillis\"\\s*:\\s*(\\d{3,})"),
            Regex("duration\"\\s*:\\s*(\\d{1,6})"),
        )
        patterns.forEach { regex ->
            val value = regex.find(html)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: return@forEach
            if (value > 0L) {
                return if (value > 10_000L) value / 1000.0 else value.toDouble()
            }
        }
        return null
    }

    private fun parseDurationSec(videoObj: JSONObject?, mediaArray: JSONArray?): Double? {
        val fromVideo = videoObj
            ?.optDouble("duration_millis")
            ?.takeIf { !it.isNaN() && it > 0.0 }
            ?.div(1000.0)
        if (fromVideo != null) return fromVideo

        if (mediaArray != null) {
            for (i in 0 until mediaArray.length()) {
                val item = mediaArray.optJSONObject(i) ?: continue
                val sec = item.optDouble("duration")
                if (!sec.isNaN() && sec > 0.0) return sec
            }
        }
        return null
    }

    private fun extractBestCoverUrl(video: JSONObject): String? {
        val candidates = listOf(
            video.optJSONObject("cover"),
            video.optJSONObject("origin_cover"),
            video.optJSONObject("dynamic_cover"),
            video.optJSONObject("animated_cover"),
        )

        candidates.forEach { obj ->
            val first = extractUrlList(obj).firstOrNull()?.takeIf { it.isNotBlank() }
            if (!first.isNullOrBlank()) {
                return normalizeVideoUrl(first)
            }
        }

        return null
    }

    private fun extractUrlList(obj: JSONObject?): List<String> {
        if (obj == null) return emptyList()

        val urls = mutableListOf<String>()
        val array = obj.optJSONArray("url_list")
        if (array != null) {
            for (i in 0 until array.length()) {
                val value = array.optString(i)
                if (value.isNotBlank()) urls += value
            }
        }

        val fallback = obj.optString("url")
        if (fallback.isNotBlank()) {
            urls += fallback
        }

        return urls
    }

    private fun isDouyinHost(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("douyin.com") || lower.contains("iesdouyin.com")
    }

    private fun isXHost(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("x.com") ||
            lower.contains("twitter.com") ||
            lower.contains("fxtwitter.com") ||
            lower.contains("vxtwitter.com") ||
            lower.contains("fixupx.com")
    }

    private fun extractDouyinVideoId(text: String): String? {
        val candidates = listOf(
            Regex("/video/(\\d{8,20})"),
            Regex("item_id=(\\d{8,20})"),
            Regex("aweme_id=(\\d{8,20})"),
            Regex("modal_id=(\\d{8,20})"),
            Regex("\"(?:awemeId|itemId|group_id|videoId)\"\\s*:?\\s*\"?(\\d{8,20})\"?"),
        )

        candidates.forEach { regex ->
            val match = regex.find(text)?.groupValues?.getOrNull(1)
            if (!match.isNullOrBlank()) {
                return match
            }
        }

        return null
    }

    private fun extractDouyinVideoUrls(html: String): List<String> {
        val candidates = linkedSetOf<String>()
        val sourceBlocks = mutableListOf<String>()
        sourceBlocks += html

        Regex(
            "<script[^>]*id=[\"']RENDER_DATA[\"'][^>]*>(.*?)</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(html).forEach { match ->
            val raw = match.groupValues.getOrNull(1).orEmpty()
            val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
            sourceBlocks += decoded
        }

        sourceBlocks.forEach { block ->
            URL_PLAIN_REGEX.findAll(block).forEach { candidates += decodeText(it.value) }
            URL_ESCAPED_REGEX.findAll(block).forEach { candidates += decodeText(it.value) }

            RELATIVE_PLAY_REGEX.findAll(block).forEach { match ->
                val path = decodeText(match.value)
                    .replace("\\/", "/")
                    .trimEnd('"', '\'', ')', ']', '}')
                if (path.startsWith("/")) {
                    candidates += "https://www.iesdouyin.com$path"
                }
            }

            VIDEO_ID_REGEX.findAll(block).forEach { match ->
                val videoId = match.groupValues.getOrNull(1).orEmpty()
                if (videoId.isNotBlank()) {
                    candidates += "https://www.iesdouyin.com/aweme/v1/play/?video_id=$videoId&ratio=1080p&line=0"
                    candidates += "https://www.iesdouyin.com/aweme/v1/playwm/?video_id=$videoId&ratio=1080p&line=0"
                }
            }
        }

        return candidates
            .map(::normalizeVideoUrl)
            .filter(::isLikelyVideoUrl)
            .distinct()
    }

    private fun isLikelyVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp4") ||
            lower.endsWith(".m3u8") ||
            lower.contains("/aweme/v1/play/") ||
            lower.contains("/aweme/v1/playwm/") ||
            lower.contains("/aweme/v1/aweme/play/") ||
            lower.contains("/video/tos/") ||
            lower.contains("playwm")
    }

    private fun inferResolution(url: String): String {
        val lower = url.lowercase()
        val ratio = Regex("ratio=([0-9a-z]+)").find(lower)?.groupValues?.getOrNull(1)
        if (!ratio.isNullOrBlank()) return ratio

        return when {
            lower.contains("1080") -> "1080p"
            lower.contains("720") -> "720p"
            lower.contains("540") -> "540p"
            else -> "原始"
        }
    }

    private fun extractTweetId(url: String): String? {
        return Regex("(?:twitter|x)\\.com/.+/status/(\\d+)", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun extractBroadcastId(url: String): String? {
        return Regex("(?:twitter|x)\\.com/i/broadcasts/([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun extractTweetHandle(url: String): String? {
        return Regex("(?:twitter|x)\\.com/([^/]+)/status/\\d+", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it != "i" }
    }

    private fun extractMeta(html: String, key: String): String? {
        val patterns = listOf(
            Regex("<meta\\s+property=[\"']$key[\"']\\s+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
            Regex("<meta\\s+content=[\"']([^\"']+)[\"']\\s+property=[\"']$key[\"']", RegexOption.IGNORE_CASE),
            Regex("<meta\\s+name=[\"']$key[\"']\\s+content=[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE),
            Regex("<meta\\s+content=[\"']([^\"']+)[\"']\\s+name=[\"']$key[\"']", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }?.let(::decodeText)
    }

    private fun extractTitleTag(html: String): String? {
        return Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let(::decodeText)
    }

    private fun extractJsonField(text: String, field: String): String? {
        val regex = Regex("\"$field\"\\s*:\\s*\"([^\"]+)\"")
        return regex.find(text)?.groupValues?.getOrNull(1)?.let(::decodeText)
    }

    private fun sanitizeTitle(raw: String?): String {
        return raw
            ?.replace("\n", " ")
            ?.replace("\r", " ")
            ?.trim()
            ?.take(60)
            .orEmpty()
    }

    private fun preferMp4Formats(formats: List<VideoFormat>): List<VideoFormat> {
        val mp4Formats = formats.filter { it.ext.equals("mp4", ignoreCase = true) }
        return if (mp4Formats.isNotEmpty()) mp4Formats else formats
    }

    private fun normalizeVideoUrl(raw: String): String {
        val decoded = decodeText(raw).trim().trimEnd('"', '\'', ')', ']', '}')
        val withoutWatermark = decoded.replace("playwm", "play")
        return if (withoutWatermark.startsWith("//")) {
            "https:$withoutWatermark"
        } else {
            withoutWatermark
        }
    }

    private fun decodeText(raw: String): String {
        val replaced = raw
            .replace("&amp;", "&")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("\\/", "/")

        return runCatching { URLDecoder.decode(replaced, "UTF-8") }.getOrDefault(replaced)
    }

    private fun extractBroadcastM3u8Urls(html: String): List<String> {
        val candidates = linkedSetOf<String>()
        val sourceBlocks = mutableListOf<String>()
        sourceBlocks += html

        Regex(
            "<script[^>]*id=[\"']RENDER_DATA[\"'][^>]*>(.*?)</script>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).findAll(html).forEach { match ->
            val raw = match.groupValues.getOrNull(1).orEmpty()
            val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
            sourceBlocks += decoded
        }

        sourceBlocks.forEach { block ->
            // 先抓取所有 URL，再做多轮解码，尽可能命中被编码隐藏的 m3u8。
            Regex("""https?://[^\s"'<>\\]+""", RegexOption.IGNORE_CASE)
                .findAll(block)
                .forEach { match ->
                    collectM3u8Candidates(candidates, match.value)
                }
            Regex("""https?:\\\\/\\\\/[^\s"'<>]+""", RegexOption.IGNORE_CASE)
                .findAll(block)
                .forEach { match ->
                    collectM3u8Candidates(candidates, match.value)
                }
            Regex("""//[^\s"'<>\\]+""")
                .findAll(block)
                .forEach { match ->
                    collectM3u8Candidates(candidates, "https:${match.value}")
                }

            Regex("""https?://[^\s"'<>()\\]+\.m3u8[^\s"'<>)]*""", RegexOption.IGNORE_CASE)
                .findAll(block)
                .forEach { candidates += decodeText(it.value) }
            Regex("""https?:\\\\/\\\\/[^\s"'<>()]+\.m3u8[^\s"'<>)]*""", RegexOption.IGNORE_CASE)
                .findAll(block)
                .forEach { candidates += decodeText(it.value) }
        }

        return candidates
            .map { it.trim().trimEnd('"', '\'', ')', ']', '}') }
            .filter { it.contains(".m3u8", ignoreCase = true) }
            .filter { it.startsWith("https://") || it.startsWith("http://") }
            .distinct()
    }

    private fun collectM3u8Candidates(result: MutableSet<String>, rawUrl: String) {
        val first = decodeText(rawUrl)
        val second = decodeText(first)
        val third = decodeText(second)
        listOf(first, second, third).forEach { decoded ->
            if (decoded.contains(".m3u8", ignoreCase = true)) {
                result += decoded
            }
        }
    }

    private fun elapsedMs(startAtNano: Long): Long {
        return ((System.nanoTime() - startAtNano) / 1_000_000L).coerceAtLeast(0L)
    }

    private fun httpGet(url: String): String? = httpGetWithFinalUrl(url)?.body

    private fun httpGetWithFinalUrl(url: String): HttpResult? {
        val requestBuilder = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36",
            )
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")

        if (isDouyinHost(url)) {
            requestBuilder.header("Referer", "https://www.douyin.com/")
        } else if (isXHost(url)) {
            requestBuilder.header("Referer", "https://x.com/")
            xCookieProvider?.invoke()
                ?.takeIf { it.isNotBlank() }
                ?.let { requestBuilder.header("Cookie", it) }
        }

        val request = requestBuilder.build()

        val callClient = if (isXHost(url)) xClient else client

        return runCatching {
            callClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(tag, "http failed code=${response.code}, url=$url")
                    return@use null
                }

                val body = response.body?.string().orEmpty()
                if (body.isBlank()) {
                    Log.w(tag, "http empty body, url=$url")
                    return@use null
                }

                HttpResult(
                    finalUrl = response.request.url.toString(),
                    body = body,
                )
            }
        }.getOrElse { throwable ->
            Log.e(tag, "http exception, url=$url", throwable)
            null
        }
    }

    private data class HttpResult(
        val finalUrl: String,
        val body: String,
    )

    companion object {
        private const val X_STATUS_TOTAL_TIMEOUT_MS = 12_000L
        private const val X_FX_TOTAL_TIMEOUT_MS = 9_000L
        private val URL_PLAIN_REGEX = Regex("""https?://[^\s\"'<>\\]+""", RegexOption.IGNORE_CASE)
        private val URL_ESCAPED_REGEX = Regex("""https?:\\\\/\\\\/[^\s\"'<>]+""", RegexOption.IGNORE_CASE)
        private val RELATIVE_PLAY_REGEX = Regex("""/aweme/v1/(?:play|playwm)/\?[^\s\"'<>]+""", RegexOption.IGNORE_CASE)
        private val VIDEO_ID_REGEX = Regex("""video_id[=:"\\\s]+([0-9A-Za-z_-]{6,})""", RegexOption.IGNORE_CASE)
    }
}
