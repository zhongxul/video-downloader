package com.example.videodownloader.parser

import android.util.Log
import android.util.Base64
import com.example.videodownloader.core.net.buildXMediaHeaderMap
import com.example.videodownloader.core.net.isXMediaUrl
import com.example.videodownloader.core.text.AppText
import com.example.videodownloader.core.text.DefaultAppText
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
    private val appText: AppText = DefaultAppText,
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
            throw IllegalArgumentException(appText.parseNoDisplayableMedia())
        }

        if (isXHost(url)) {
            val startAt = System.nanoTime()
            parseXFast(url)?.let {
                Log.i(tag, "parseXFast success, cost=${elapsedMs(startAt)}ms, url=$url")
                return it
            }
            Log.w(tag, "parse failed for x, cost=${elapsedMs(startAt)}ms, url=$url")
            throw IllegalArgumentException(appText.xNetworkUnavailable())
        }

        parseDouyin(url)?.let { return it }
        parseXBroadcast(url)?.let { return it }
        parseTwitterSyndication(url)?.let { return it }
        parseTwitterFx(url)?.let { return it }
        parseFromMeta(url)?.let { return it }

        Log.w(tag, "parse failed: no downloadable format, url=$url")
        throw IllegalArgumentException(appText.parseNoDisplayableMedia())
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
            title = appText.directVideoTitle(),
            coverUrl = null,
            formats = listOf(
                VideoFormat(
                    formatId = "direct",
                    resolution = appText.originalQuality(),
                    ext = ext,
                    sizeText = null,
                    downloadUrl = url,
                    downloadable = true,
                ),
            ),
        )
    }

    private fun parseDouyin(url: String, depth: Int = 0): ParsedVideoInfo? {
        val candidateIds = linkedSetOf<String>()
        extractDouyinVideoId(url)?.let { candidateIds += it }

        if (!isDouyinHost(url) && candidateIds.isEmpty()) {
            return null
        }

        Log.d(tag, "parseDouyin: ids from url=${candidateIds.joinToString(",")}")

        val htmlCandidates = linkedSetOf<String>()
        var hasImagePostHint = false

        val firstPage = httpGetWithFinalUrl(url)
        if (firstPage != null) {
            if (firstPage.body.isNotBlank()) {
                htmlCandidates += firstPage.body
                if (containsDouyinImagePostHint(firstPage.body)) {
                    hasImagePostHint = true
                }
            }
            extractDouyinJumpUrl(firstPage.body)?.let { jumpUrl ->
                extractDouyinVideoId(jumpUrl)?.let { candidateIds += it }
                if (depth < 2 && !jumpUrl.equals(url, ignoreCase = true)) {
                    parseDouyin(jumpUrl, depth + 1)?.let { return it }
                }
            }
            if (firstPage.finalUrl.contains("/share/slides/", ignoreCase = true)) {
                hasImagePostHint = true
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
                if (containsDouyinImagePostHint(sharePage.body)) {
                    hasImagePostHint = true
                }
                if (sharePage.finalUrl.contains("/share/slides/", ignoreCase = true)) {
                    hasImagePostHint = true
                }
                extractDouyinVideoId(sharePage.finalUrl)?.let { candidateIds += it }
                extractDouyinVideoId(sharePage.body)?.let { candidateIds += it }
            }
        }

        candidateIds.forEach { videoId ->
            parseDouyinByApi(videoId)?.let { return it }
        }

        val htmlResult = parseDouyinFromHtml(htmlCandidates)
        if (htmlResult != null) return htmlResult

        parseDouyinImagesFromHtml(htmlCandidates)?.let { return it }
        if (hasImagePostHint) {
            throw IllegalArgumentException(appText.douyinImageParseFailed())
        }
        if (htmlCandidates.isNotEmpty()) {
            Log.w(tag, "parseDouyin: html parse failed, ids=${candidateIds.joinToString(",")}")
        }
        return null
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
        val title = sanitizeTitle(item.optString("desc")).ifBlank { appText.douyinVideoTitle() }
        val imageUrls = extractDouyinImageUrls(item)
        val video = item.optJSONObject("video")

        val urls = linkedSetOf<String>()
        if (video != null) {
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
        }

        val normalizedUrls = urls
            .map(::normalizeVideoUrl)
            .filter(::isLikelyVideoUrl)
            .distinct()

        if (normalizedUrls.isEmpty()) {
            return buildDouyinImageParsedInfo(
                title = appText.douyinGalleryTitle(),
                imageUrls = imageUrls,
            )
        }

        val cover = video?.let(::extractBestCoverUrl) ?: imageUrls.firstOrNull()

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
            formats = selectPrimaryDouyinFormats(preferMp4Formats(formats)),
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
            title = sanitizeTitle(title).ifBlank { appText.douyinVideoTitle() },
            coverUrl = cover?.takeIf { it.isNotBlank() },
            formats = selectPrimaryDouyinFormats(preferMp4Formats(formats)),
        )
    }

    private fun parseDouyinImagesFromHtml(htmlCandidates: Collection<String>): ParsedVideoInfo? {
        if (htmlCandidates.isEmpty()) return null

        val imageUrls = linkedSetOf<String>()
        var title: String? = null

        htmlCandidates.forEach { html ->
            if (title.isNullOrBlank()) {
                title = extractMeta(html, "og:title") ?: extractTitleTag(html) ?: extractJsonField(html, "desc")
            }
            extractDouyinItemsFromHtml(html).forEach { item ->
                if (title.isNullOrBlank()) {
                    title = item.optString("desc")
                }
                imageUrls += extractDouyinImageUrls(item)
            }
        }

        return buildDouyinImageParsedInfo(
            title = sanitizeTitle(title).ifBlank { appText.douyinGalleryTitle() },
            imageUrls = imageUrls,
        )
    }

    private fun buildDouyinImageParsedInfo(
        title: String?,
        imageUrls: Collection<String>,
    ): ParsedVideoInfo? {
        val deduplicated = linkedMapOf<String, String>()
        imageUrls
            .map(::normalizeVideoUrl)
            .filter(::isLikelyImageDownloadUrl)
            .forEach { link ->
                val key = canonicalDouyinImageKey(link)
                val current = deduplicated[key]
                if (current == null || scoreDouyinImageUrl(link) > scoreDouyinImageUrl(current)) {
                    deduplicated[key] = link
                }
            }
        val normalizedImageUrls = deduplicated.values.toList()
        if (normalizedImageUrls.isEmpty()) return null

        val formats = normalizedImageUrls.mapIndexed { index, link ->
            val ext = inferMediaExtFromUrl(link, "jpg")
            val label = if (ext.equals("gif", ignoreCase = true)) appText.gifLabel(index + 1) else appText.imageLabel(index + 1)
            VideoFormat(
                formatId = "douyin_image_$index",
                resolution = label,
                ext = ext,
                sizeText = null,
                downloadUrl = link,
                downloadable = true,
            )
        }

        return ParsedVideoInfo(
            title = sanitizeTitle(title).ifBlank { appText.douyinGalleryTitle() },
            coverUrl = formats.firstOrNull()?.downloadUrl,
            formats = formats,
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
            ?: appText.xBroadcastTitle()
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
            title = sanitizeTitle(title).ifBlank { appText.xBroadcastTitle() },
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

        val title = json.optString("text").ifBlank { appText.xVideoTitle() }
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
            title = sanitizeTitle(title).ifBlank { appText.xVideoTitle() },
            coverUrl = videoObj?.optString("poster")?.takeIf { it.isNotBlank() }
                ?: extractFirstXMediaCoverUrl(json.optJSONArray("mediaDetails")),
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
            val apiStart = System.nanoTime()
            parseTwitterFxApi(url)?.let {
                Log.i(tag, "parseXFast hit=fx_api, cost=${elapsedMs(apiStart)}ms")
                return it
            }
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

    private fun parseTwitterFxApi(url: String): ParsedVideoInfo? {
        val tweetId = extractTweetId(url) ?: return null
        val apiUrls = listOf(
            "https://api.fxtwitter.com/i/status/$tweetId",
            "https://api.vxtwitter.com/i/status/$tweetId",
        )
        apiUrls.forEach { apiUrl ->
            val body = httpGet(apiUrl) ?: return@forEach
            parseFxTwitterApiResponse(body)?.let { return it }
        }
        return null
    }

    private fun parseFxTwitterApiResponse(body: String): ParsedVideoInfo? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        val formats = mutableListOf<VideoFormat>()
        var title = root.optString("text")

        val tweet = root.optJSONObject("tweet")
        if (tweet != null) {
            if (root.has("code") && root.optInt("code", 0) != 200) return null
            title = tweet.optString("text")
                .ifBlank { tweet.optJSONObject("raw_text")?.optString("text").orEmpty() }
            val media = tweet.optJSONObject("media")
            val photoArray = media?.optJSONArray("photos")
                ?: media?.optJSONArray("all")
            if (photoArray != null) {
                for (index in 0 until photoArray.length()) {
                    val item = photoArray.optJSONObject(index) ?: continue
                    val type = item.optString("type")
                    if (!type.equals("photo", ignoreCase = true)) continue
                    val imageUrl = normalizeVideoUrl(item.optString("url"))
                    if (!isLikelyXImageUrl(imageUrl)) continue
                    formats += VideoFormat(
                        formatId = "x_fx_api_$index",
                        resolution = appText.imageLabel(index + 1),
                        ext = inferImageExtFromUrl(imageUrl),
                        sizeText = null,
                        downloadUrl = imageUrl,
                        downloadable = true,
                    )
                }
            }
        }
        if (formats.isEmpty()) {
            val mediaExtended = root.optJSONArray("media_extended")
            if (mediaExtended != null) {
                for (index in 0 until mediaExtended.length()) {
                    val item = mediaExtended.optJSONObject(index) ?: continue
                    val type = item.optString("type")
                    if (!type.equals("image", ignoreCase = true) && !type.equals("photo", ignoreCase = true)) continue
                    val imageUrl = normalizeVideoUrl(item.optString("url").ifBlank { item.optString("thumbnail_url") })
                    if (!isLikelyXImageUrl(imageUrl)) continue
                    formats += VideoFormat(
                        formatId = "x_vx_api_$index",
                        resolution = appText.imageLabel(index + 1),
                        ext = inferImageExtFromUrl(imageUrl),
                        sizeText = null,
                        downloadUrl = imageUrl,
                        downloadable = true,
                    )
                }
            }
        }
        if (formats.isEmpty()) return null

        return ParsedVideoInfo(
            title = sanitizeTitle(title).ifBlank { appText.xVideoTitle() },
            coverUrl = formats.firstOrNull()?.downloadUrl,
            formats = formats.distinctBy { it.downloadUrl },
        )
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
        buildXInfoFromInitialState(url, html)?.let {
            runCatching { Log.i(tag, "parseFromMeta: initial state success, url=$url, count=${it.formats.size}") }
            return it
        }
        val ogVideo = extractMeta(html, "og:video")
            ?: extractMeta(html, "og:video:url")
            ?: extractMeta(html, "twitter:player:stream")
        if (ogVideo.isNullOrBlank()) {
            buildXImageInfoFromMeta(url, html)?.let {
                runCatching { Log.i(tag, "parseFromMeta: image fallback success, url=$url, count=${it.formats.size}") }
                return it
            }
            Log.w(tag, "parseFromMeta: no og video, url=$url")
            return null
        }

        val title = extractMeta(html, "og:title")?.trim()?.takeIf { it.isNotBlank() } ?: appText.webVideoTitle()
        val cover = extractMeta(html, "og:image")?.takeIf { it.isNotBlank() }
        val link = normalizeVideoUrl(ogVideo)
        val ext = if (link.contains(".m3u8", true)) "m3u8" else "mp4"

        return ParsedVideoInfo(
            title = sanitizeTitle(title),
            coverUrl = cover,
            formats = listOf(
                VideoFormat(
                    formatId = "meta",
                    resolution = appText.originalQuality(),
                    ext = ext,
                    sizeText = null,
                    downloadUrl = link,
                    downloadable = true,
                ),
            ),
        )
    }

    private fun buildXImageInfoFromMeta(url: String, html: String): ParsedVideoInfo? {
        if (!isXHost(url) && !isXMediaUrl(url)) return null

        val imageUrls = linkedSetOf<String>()
        val keys = buildList {
            add("og:image")
            add("twitter:image")
            for (index in 0..9) {
                add("og:image:$index")
                add("twitter:image:$index")
                add("twitter:image$index")
            }
        }
        keys.forEach { key ->
            extractMeta(html, key)
                ?.takeIf { it.isNotBlank() }
                ?.let { imageUrls += normalizeVideoUrl(it) }
        }
        if (imageUrls.isEmpty()) {
            extractXImageUrlsFromHtml(html).forEach { imageUrls += it }
        }

        val formats = imageUrls
            .filter { it.isNotBlank() }
            .mapIndexed { index, imageUrl ->
                VideoFormat(
                    formatId = "x_meta_image_$index",
                    resolution = appText.imageLabel(index + 1),
                    ext = inferImageExtFromUrl(imageUrl),
                    sizeText = null,
                    downloadUrl = imageUrl,
                    durationSec = null,
                    downloadable = true,
                )
            }
            .distinctBy { it.downloadUrl }

        if (formats.isEmpty()) {
            Log.w(tag, "buildXImageInfoFromMeta: no image meta found, url=$url")
            return null
        }

        val title = extractMeta(html, "og:title")
            ?: extractMeta(html, "twitter:title")
            ?: appText.xVideoTitle()

        runCatching { Log.d(tag, "buildXImageInfoFromMeta: found ${formats.size} images, url=$url") }
        return ParsedVideoInfo(
            title = sanitizeTitle(title).ifBlank { appText.xVideoTitle() },
            coverUrl = formats.firstOrNull()?.downloadUrl,
            formats = formats,
        )
    }

    private fun buildXInfoFromInitialState(url: String, html: String): ParsedVideoInfo? {
        if (!isXHost(url)) return null

        val stateJson = extractXInitialStateJson(html) ?: return null
        val state = runCatching { JSONObject(stateJson) }.getOrNull() ?: return null
        val tweets = state
            .optJSONObject("entities")
            ?.optJSONObject("tweets")
            ?.optJSONObject("entities")
            ?: return null

        val tweetId = extractTweetId(url)
        val tweet = when {
            !tweetId.isNullOrBlank() -> tweets.optJSONObject(tweetId)
            else -> null
        } ?: tweets.keys().asSequence().mapNotNull { key -> tweets.optJSONObject(key) }.firstOrNull()
            ?: return null

        val mediaArray = tweet.optJSONObject("extended_entities")?.optJSONArray("media")
            ?: tweet.optJSONObject("entities")?.optJSONArray("media")
            ?: return null
        val title = tweet.optString("full_text")
            .ifBlank { tweet.optString("text") }
            .ifBlank { appText.xVideoTitle() }
        val coverUrl = extractFirstXMediaCoverUrl(mediaArray)

        val videoVariants = mutableListOf<VideoFormat>()
        for (i in 0 until mediaArray.length()) {
            val item = mediaArray.optJSONObject(i) ?: continue
            val videoInfo = item.optJSONObject("video_info") ?: continue
            val durationSec = videoInfo
                .optDouble("duration_millis")
                .takeIf { !it.isNaN() && it > 0.0 }
                ?.div(1000.0)
            val variants = videoInfo.optJSONArray("variants") ?: continue
            videoVariants += parseTwitterVariants(variants, durationSec)
        }
        if (videoVariants.isNotEmpty()) {
            return ParsedVideoInfo(
                title = sanitizeTitle(title).ifBlank { appText.xVideoTitle() },
                coverUrl = coverUrl,
                formats = preferMp4Formats(videoVariants.distinctBy { it.downloadUrl }),
            )
        }

        val imageFormats = parseMediaDetails(mediaArray, durationSec = null)
            .filter { isLikelyXImageUrl(it.downloadUrl) }
        if (imageFormats.isEmpty()) {
            return null
        }
        return ParsedVideoInfo(
            title = sanitizeTitle(title).ifBlank { appText.xVideoTitle() },
            coverUrl = coverUrl ?: imageFormats.firstOrNull()?.downloadUrl,
            formats = imageFormats.distinctBy { it.downloadUrl },
        )
    }

    private fun extractXImageUrlsFromHtml(html: String): List<String> {
        val candidates = linkedSetOf<String>()
        val patterns = listOf(
            Regex("""https://pbs\.twimg\.com/media/[^\s"'<>\\]+""", RegexOption.IGNORE_CASE),
            Regex("""https:\\/\\/pbs\.twimg\.com\\/media\\/[^\s"'<>]+""", RegexOption.IGNORE_CASE),
        )
        patterns.forEach { regex ->
            regex.findAll(html).forEach { match ->
                val normalized = normalizeVideoUrl(match.value)
                    .trimEnd('"', '\'', '>', '/')
                if (isLikelyXImageUrl(normalized)) {
                    candidates += normalized
                }
            }
        }
        return candidates.toList()
    }

    private fun isLikelyXImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        val clean = lower.substringBefore('?').substringBefore('#')
        return lower.contains("pbs.twimg.com/media/") &&
            (
                lower.contains("?format=") ||
                    clean.endsWith(".jpg") ||
                    clean.endsWith(".jpeg") ||
                    clean.endsWith(".png") ||
                    clean.endsWith(".webp") ||
                    clean.endsWith(".gif")
                )
    }

    private fun extractXInitialStateJson(html: String): String? {
        val marker = "window.__INITIAL_STATE__="
        val start = html.indexOf(marker)
        if (start < 0) return null

        val jsonStart = html.indexOf('{', start + marker.length)
        if (jsonStart < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (index in jsonStart until html.length) {
            val ch = html[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return html.substring(jsonStart, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun parseTwitterVariants(array: JSONArray, durationSec: Double?): List<VideoFormat> {
        val result = mutableListOf<VideoFormat>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val src = normalizeVideoUrl(item.optString("src").ifBlank { item.optString("url") })
            if (src.isBlank()) continue

            val bitrate = item.optInt("bitrate", -1)
            val resolution = when {
                bitrate >= 2_000_000 -> "1080p"
                bitrate >= 1_000_000 -> "720p"
                bitrate >= 500_000 -> "480p"
                else -> appText.originalQuality()
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
            val isPhoto = type.equals("photo", ignoreCase = true)
            val isVideo = type.equals("video", ignoreCase = true) || normalized.contains(".mp4", true) || normalized.contains(".m3u8", true)
            if (!isPhoto && !isVideo) continue

            val ext = if (isPhoto) {
                inferImageExtFromUrl(normalized)
            } else if (normalized.contains(".m3u8", true)) {
                "m3u8"
            } else {
                "mp4"
            }
            val resolution = if (isPhoto) appText.imageLabel(i + 1) else appText.originalQuality()

            result += VideoFormat(
                formatId = "media_$i",
                resolution = resolution,
                ext = ext,
                sizeText = null,
                downloadUrl = normalized,
                durationSec = if (isPhoto) null else durationSec,
                downloadable = true,
            )
        }

        return result
    }

    private fun inferImageExtFromUrl(url: String): String {
        val clean = url.lowercase()
        val formatParam = Regex("[?&]format=([a-z0-9]+)", RegexOption.IGNORE_CASE)
            .find(clean)
            ?.groupValues
            ?.getOrNull(1)
            ?.takeIf { it.isNotBlank() }
        if (!formatParam.isNullOrBlank()) {
            return formatParam
        }
        return inferMediaExtFromUrl(clean, "jpg")
    }

    private fun extractFirstXMediaCoverUrl(mediaArray: JSONArray?): String? {
        if (mediaArray == null) return null
        for (i in 0 until mediaArray.length()) {
            val item = mediaArray.optJSONObject(i) ?: continue
            val mediaUrl = item.optString("media_url_https").ifBlank { item.optString("media_url") }
            val normalized = normalizeVideoUrl(mediaUrl)
            if (normalized.isNotBlank()) {
                return normalized
            }
        }
        return null
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

    private fun extractDouyinItemsFromHtml(html: String): List<JSONObject> {
        val routerData = extractScriptJsonObject(html, "_ROUTER_DATA") ?: return emptyList()
        val list = extractDouyinItemList(routerData) ?: return emptyList()
        return buildList {
            for (i in 0 until list.length()) {
                val item = list.optJSONObject(i) ?: continue
                add(item)
            }
        }
    }

    private fun extractScriptJsonObject(html: String, variableName: String): JSONObject? {
        val marker = Regex("window\\.$variableName\\s*=\\s*", RegexOption.IGNORE_CASE)
            .find(html)
            ?: return null
        val startIndex = marker.range.last + 1
        val raw = extractBalancedJsonObject(html, startIndex).orEmpty()
        if (raw.isBlank()) return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    private fun extractBalancedJsonObject(text: String, startIndex: Int): String? {
        var start = startIndex
        while (start < text.length && text[start].isWhitespace()) {
            start += 1
        }
        if (start >= text.length || text[start] != '{') return null

        var depth = 0
        var inString = false
        var escaped = false
        var quote = '"'

        for (index in start until text.length) {
            val ch = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                    continue
                }
                when (ch) {
                    '\\' -> escaped = true
                    quote -> inString = false
                }
                continue
            }

            when (ch) {
                '"', '\'' -> {
                    inString = true
                    quote = ch
                }

                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return text.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun extractDouyinItemList(routerData: JSONObject): JSONArray? {
        routerData.optJSONArray("item_list")?.let { return it }
        routerData.optJSONObject("videoInfoRes")?.optJSONArray("item_list")?.let { return it }

        val loaderData = routerData.optJSONObject("loaderData") ?: return null
        loaderData.optJSONObject("video_(id)/page")
            ?.optJSONObject("videoInfoRes")
            ?.optJSONArray("item_list")
            ?.let { return it }
        loaderData.optJSONObject("slides_(id)/page")
            ?.optJSONObject("videoInfoRes")
            ?.optJSONArray("item_list")
            ?.let { return it }
        loaderData.optJSONObject("video_page")
            ?.optJSONObject("videoInfoRes")
            ?.optJSONArray("item_list")
            ?.let { return it }
        loaderData.optJSONObject("slides_page")
            ?.optJSONObject("videoInfoRes")
            ?.optJSONArray("item_list")
            ?.let { return it }
        val keys = loaderData.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val pageObj = loaderData.optJSONObject(key) ?: continue
            pageObj.optJSONObject("videoInfoRes")
                ?.optJSONArray("item_list")
                ?.let { return it }
            pageObj.optJSONArray("item_list")?.let { return it }
        }

        return null
    }

    private fun extractDouyinImageUrls(item: JSONObject): List<String> {
        val urls = linkedSetOf<String>()

        val imageArray = item.optJSONArray("images")
        if (imageArray != null && imageArray.length() > 0) {
            for (i in 0 until imageArray.length()) {
                val imageObj = imageArray.optJSONObject(i) ?: continue
                pickBestDouyinImageUrl(imageObj)?.let { urls += it }
            }
            return urls.toList()
        }

        val imageInfosArray = item.optJSONArray("image_infos")
        if (imageInfosArray != null && imageInfosArray.length() > 0) {
            for (i in 0 until imageInfosArray.length()) {
                val imageObj = imageInfosArray.optJSONObject(i) ?: continue
                pickBestDouyinImageUrl(imageObj)?.let { urls += it }
            }
        }

        return urls.toList()
    }

    private fun pickBestDouyinImageUrl(imageObj: JSONObject): String? {
        val downloadUrls = extractStringList(imageObj.optJSONArray("download_url_list"))
            .map(::normalizeVideoUrl)
            .filter(::isLikelyImageDownloadUrl)

        val playUrls = extractStringList(imageObj.optJSONArray("url_list"))
            .map(::normalizeVideoUrl)
            .filter(::isLikelyImageDownloadUrl)

        val candidates = if (downloadUrls.isNotEmpty()) downloadUrls else playUrls
        if (candidates.isEmpty()) return null
        return candidates.maxByOrNull(::scoreDouyinImageUrl)
    }

    private fun canonicalDouyinImageKey(url: String): String {
        val clean = url.substringBefore('#').substringBefore('?')
        val path = runCatching { java.net.URI(clean).path.orEmpty() }.getOrDefault(clean)
        val fileName = path.substringAfterLast('/').substringBeforeLast('.').trim()
        if (fileName.isNotBlank()) return fileName
        return path.ifBlank { clean }
    }

    private fun scoreDouyinImageUrl(url: String): Int {
        val lower = url.lowercase()
        var score = 0
        if (lower.contains("-water:", ignoreCase = true)) score -= 40
        if (lower.contains("douyinpic.com")) score += 50
        if (lower.contains("-sign.")) score += 20
        if (lower.contains("biz_tag=aweme_images")) score += 20
        if (lower.contains("sc=image")) score += 10
        if (lower.contains(".webp")) score += 4
        if (lower.contains(".jpg") || lower.contains(".jpeg")) score += 3
        if (lower.contains(".png")) score += 2
        if (lower.contains(".gif")) score += 1
        return score
    }

    private fun extractStringList(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            appendStringValues(array.opt(i), result)
        }
        return result
    }

    private fun appendStringValues(value: Any?, output: MutableList<String>) {
        when (value) {
            is String -> {
                val trimmed = value.trim()
                if (trimmed.isNotBlank()) output += trimmed
            }

            is JSONObject -> {
                val direct = value.optString("url").trim()
                if (direct.isNotBlank()) output += direct
                appendStringValues(value.optJSONArray("url_list"), output)
                appendStringValues(value.optJSONArray("download_url_list"), output)
            }

            is JSONArray -> {
                for (i in 0 until value.length()) {
                    appendStringValues(value.opt(i), output)
                }
            }
        }
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
            Regex("/note/(\\d{8,20})"),
            Regex("/share/slides/(\\d{8,20})"),
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

    private fun extractDouyinJumpUrl(text: String): String? {
        val patterns = listOf(
            Regex("""https?://(?:www\.)?douyin\.com/(?:video|note|share/video|share/slides)/[^\s"'<>]+""", RegexOption.IGNORE_CASE),
            Regex("""https?://v\.douyin\.com/[^\s"'<>]+""", RegexOption.IGNORE_CASE),
        )
        patterns.forEach { regex ->
            val hit = regex.find(text)?.value?.trim()
            if (!hit.isNullOrBlank()) {
                return cleanCandidateUrl(hit)
            }
        }
        return null
    }

    private fun cleanCandidateUrl(value: String): String {
        return value
            .trim()
            .trimEnd('"', '\'', ')', ']', '}', '>', ',', '，', '。', '!')
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
            val normalizedBlock = normalizeDouyinHtmlBlock(block)
            listOf(block, normalizedBlock).forEach { candidateBlock ->
                URL_PLAIN_REGEX.findAll(candidateBlock).forEach { candidates += decodeUrlText(it.value) }
                URL_ESCAPED_REGEX.findAll(candidateBlock).forEach { candidates += decodeUrlText(it.value) }
                RELATIVE_PLAY_REGEX.findAll(candidateBlock).forEach { match ->
                    val path = decodeUrlText(match.value)
                        .replace("\\/", "/")
                        .trimEnd('"', '\'', ')', ']', '}')
                    if (path.startsWith("/")) {
                        candidates += "https://www.iesdouyin.com$path"
                    }
                }

                VIDEO_ID_REGEX.findAll(candidateBlock).forEach { match ->
                    val videoId = match.groupValues.getOrNull(1).orEmpty()
                    if (videoId.isNotBlank()) {
                        candidates += "https://www.iesdouyin.com/aweme/v1/play/?video_id=$videoId&ratio=1080p&line=0"
                        candidates += "https://www.iesdouyin.com/aweme/v1/playwm/?video_id=$videoId&ratio=1080p&line=0"
                    }
                }
            }
        }

        return candidates
            .map(::normalizeVideoUrl)
            .filter(::isLikelyVideoUrl)
            .filterNot(::isLikelyImageUrl)
            .distinct()
    }

    private fun isLikelyVideoUrl(url: String): Boolean {
        if (isLikelyImageUrl(url)) return false
        if (isLikelyAudioUrl(url)) return false
        if (!isLikelyValidDouyinPlayUrl(url)) return false

        val lower = url.lowercase()
        return lower.endsWith(".mp4") ||
            lower.endsWith(".m3u8") ||
            lower.contains("/aweme/v1/play/") ||
            lower.contains("/aweme/v1/playwm/") ||
            lower.contains("/aweme/v1/aweme/play/") ||
            lower.contains("/video/tos/") ||
            lower.contains("playwm")
    }

    private fun isLikelyImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("sc=image") ||
            lower.contains("biz_tag=aweme_images") ||
            lower.contains("douyinpic.com") ||
            lower.contains("tos-cn-i-") ||
            lower.contains(".image?") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".webp") ||
            lower.endsWith(".gif")
    }

    private fun isLikelyImageDownloadUrl(url: String): Boolean {
        val lower = url.lowercase()
        if (lower.contains("sc=cover")) return false
        if (lower.contains("biz_tag=aweme_video")) return false
        if (lower.startsWith("http://") || lower.startsWith("https://")) {
            return isLikelyImageUrl(lower)
        }
        return false
    }

    private fun isLikelyAudioUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.endsWith(".mp3") ||
            lower.contains(".mp3?") ||
            lower.contains("ies-music") ||
            lower.contains("mime_type=audio")
    }

    private fun isLikelyValidDouyinPlayUrl(url: String): Boolean {
        val lower = url.lowercase()
        val isPlayEndpoint = lower.contains("/aweme/v1/play/") ||
            lower.contains("/aweme/v1/playwm/") ||
            lower.contains("/aweme/v1/aweme/play/")
        if (!isPlayEndpoint) return true

        val videoIdRaw = Regex("[?&]video_id=([^&]+)", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
            ?: return true
        val videoId = decodeText(videoIdRaw).trim()
        if (videoId.isBlank()) return false
        if (videoId.startsWith("http://", ignoreCase = true)) return false
        if (videoId.startsWith("https://", ignoreCase = true)) return false
        if (videoId.contains('/')) return false

        return VIDEO_ID_VALUE_REGEX.matches(videoId)
    }

    private fun containsDouyinImagePostHint(text: String): Boolean {
        return text.contains("\"aweme_type\":2", ignoreCase = true) ||
            text.contains("\"images\":[", ignoreCase = true) ||
            text.contains("gallery-container__carousel__image", ignoreCase = true)
    }

    private fun selectPrimaryDouyinFormats(formats: List<VideoFormat>): List<VideoFormat> {
        if (formats.isEmpty()) return formats
        val deduplicated = formats
            .distinctBy { canonicalDouyinFormatKey(it.downloadUrl) }
            .distinctBy { it.downloadUrl }
        if (deduplicated.size <= 1) return deduplicated

        val best = deduplicated.maxByOrNull { scoreDouyinFormat(it) } ?: deduplicated.first()
        return listOf(best)
    }

    private fun canonicalDouyinFormatKey(url: String): String {
        val lower = url.lowercase()
        val isPlayEndpoint = lower.contains("/aweme/v1/play/") ||
            lower.contains("/aweme/v1/playwm/") ||
            lower.contains("/aweme/v1/aweme/play/")
        if (isPlayEndpoint) {
            val videoId = Regex("[?&]video_id=([^&]+)", RegexOption.IGNORE_CASE)
                .find(url)
                ?.groupValues
                ?.getOrNull(1)
                ?.let(::decodeText)
                ?.trim()
                .orEmpty()
            if (VIDEO_ID_VALUE_REGEX.matches(videoId)) {
                return "play:$videoId"
            }
        }
        return url.substringBefore('#')
    }

    private fun scoreDouyinFormat(format: VideoFormat): Int {
        val lower = format.downloadUrl.lowercase()
        var score = 0
        if (lower.contains("/video/tos/")) score += 100
        if (lower.endsWith(".mp4") || lower.contains("mime_type=video_mp4")) score += 80
        if (lower.contains("/aweme/v1/play/") || lower.contains("/aweme/v1/aweme/play/")) score += 50
        if (lower.contains("ratio=1080")) score += 20
        if (lower.contains("ratio=720")) score += 10
        if (lower.contains("line=0")) score += 2
        if (lower.contains("playwm")) score -= 20
        if (isLikelyAudioUrl(lower)) score -= 500
        return score
    }

    private fun inferMediaExtFromUrl(url: String, defaultExt: String): String {
        val clean = url.substringBefore('?').substringBefore('#').lowercase()
        return when {
            clean.endsWith(".jpeg") -> "jpeg"
            clean.endsWith(".jpg") -> "jpg"
            clean.endsWith(".png") -> "png"
            clean.endsWith(".webp") -> "webp"
            clean.endsWith(".gif") -> "gif"
            clean.endsWith(".bmp") -> "bmp"
            clean.endsWith(".heic") -> "heic"
            clean.endsWith(".mp4") -> "mp4"
            else -> defaultExt
        }
    }

    private fun inferResolution(url: String): String {
        val lower = url.lowercase()
        val ratio = Regex("ratio=([0-9a-z]+)").find(lower)?.groupValues?.getOrNull(1)
        if (!ratio.isNullOrBlank()) return ratio

        return when {
            lower.contains("1080") -> "1080p"
            lower.contains("720") -> "720p"
            lower.contains("540") -> "540p"
            else -> appText.originalQuality()
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
            Regex(
                """<meta\b[^>]*\bproperty=["']${Regex.escape(key)}["'][^>]*\bcontent=["']([^"']+)["'][^>]*>""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """<meta\b[^>]*\bcontent=["']([^"']+)["'][^>]*\bproperty=["']${Regex.escape(key)}["'][^>]*>""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """<meta\b[^>]*\bname=["']${Regex.escape(key)}["'][^>]*\bcontent=["']([^"']+)["'][^>]*>""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """<meta\b[^>]*\bcontent=["']([^"']+)["'][^>]*\bname=["']${Regex.escape(key)}["'][^>]*>""",
                RegexOption.IGNORE_CASE,
            ),
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
        val decoded = decodeUrlText(raw).trim().trimEnd('"', '\'', ')', ']', '}')
        val withoutWatermark = decoded.replace("playwm", "play")
        return if (withoutWatermark.startsWith("//")) {
            "https:$withoutWatermark"
        } else {
            withoutWatermark
        }
    }

    private fun decodeUrlText(raw: String): String {
        var normalized = raw
            .replace("&amp;", "&")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("\\u002f", "/")
            .replace("\\/", "/")

        repeat(3) {
            val lower = normalized.lowercase()
            val isEncodedAbsoluteUrl = lower.startsWith("https%3a%2f%2f") ||
                lower.startsWith("http%3a%2f%2f") ||
                lower.startsWith("%2f%2f")
            if (!isEncodedAbsoluteUrl) {
                return@repeat
            }
            normalized = runCatching { URLDecoder.decode(normalized, "UTF-8") }.getOrDefault(normalized)
        }

        return normalized
    }

    private fun decodeText(raw: String): String {
        val replaced = raw
            .replace("&amp;", "&")
            .replace("\\u0026", "&")
            .replace("\\u002F", "/")
            .replace("\\u002f", "/")
            .replace("\\/", "/")

        return runCatching { URLDecoder.decode(replaced, "UTF-8") }.getOrDefault(replaced)
    }

    private fun normalizeDouyinHtmlBlock(raw: String): String {
        var normalized = raw
        UNICODE_ESCAPED_SLASH_TOKENS.forEach { token ->
            normalized = normalized.replace(token, "/")
        }
        return normalized
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
                .forEach { candidates += decodeUrlText(it.value) }
            Regex("""https?:\\\\/\\\\/[^\s"'<>()]+\.m3u8[^\s"'<>)]*""", RegexOption.IGNORE_CASE)
                .findAll(block)
                .forEach { candidates += decodeUrlText(it.value) }
        }

        return candidates
            .map { it.trim().trimEnd('"', '\'', ')', ']', '}') }
            .filter { it.contains(".m3u8", ignoreCase = true) }
            .filter { it.startsWith("https://") || it.startsWith("http://") }
            .distinct()
    }

    private fun collectM3u8Candidates(result: MutableSet<String>, rawUrl: String) {
        val first = decodeUrlText(rawUrl)
        val second = decodeUrlText(first)
        val third = decodeUrlText(second)
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
        } else if (isXMediaUrl(url)) {
            buildXMediaHeaderMap(url, xCookieProvider?.invoke())
                .forEach { (key, value) -> requestBuilder.header(key, value) }
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
        private val VIDEO_ID_VALUE_REGEX = Regex("""[0-9A-Za-z_-]{8,}""")
        private val UNICODE_ESCAPED_SLASH_TOKENS = listOf(
            "\\" + "u002F",
            "\\" + "u002f",
            "\\\\" + "u002F",
            "\\\\" + "u002f",
        )
    }
}
