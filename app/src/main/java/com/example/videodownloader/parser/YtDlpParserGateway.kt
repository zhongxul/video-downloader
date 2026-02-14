package com.example.videodownloader.parser

import android.content.Context
import com.example.videodownloader.domain.model.ParsedVideoInfo
import com.example.videodownloader.domain.model.VideoFormat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat as YtVideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale

class YtDlpParserGateway(
    context: Context,
    private val xCookieProvider: (() -> String?)? = null,
) {
    private val appContext = context.applicationContext

    @Volatile
    private var initialized = false

    suspend fun parse(url: String): ParsedVideoInfo? = withContext(Dispatchers.IO) {
        if (!isXHost(url)) return@withContext null
        require(ensureInitialized()) { "解析器初始化失败，请重启应用后重试" }

        val cookie = xCookieProvider?.invoke()?.takeIf { it.isNotBlank() }
        val csrfToken = extractCookieValue(cookie, "ct0")
        val canonicalUrl = normalizeXStatusUrl(url)
        val attempts = listOf(
            FetchAttempt(extractorArgs = null, retryCount = 2),
            FetchAttempt(extractorArgs = "twitter:api=syndication", retryCount = 1),
        )

        var sawNoVideo = false
        var sawTransientNetworkIssue = false
        var sawAuthIssue = false
        var lastError: Throwable? = null

        attempts.forEach { attempt ->
            repeat(attempt.retryCount) { retryIndex ->
                val info = fetchInfo(
                    url = canonicalUrl,
                    extractorArgs = attempt.extractorArgs,
                    cookie = cookie,
                    csrfToken = csrfToken,
                ).onFailure { throwable ->
                    lastError = throwable
                    val failureType = classifyFailure(throwable)
                    when (failureType) {
                        FailureType.NO_VIDEO -> sawNoVideo = true
                        FailureType.TRANSIENT_NETWORK -> sawTransientNetworkIssue = true
                        FailureType.AUTH -> sawAuthIssue = true
                        FailureType.UNKNOWN -> Unit
                    }
                    if (failureType == FailureType.TRANSIENT_NETWORK && retryIndex < attempt.retryCount - 1) {
                        Timber.i(
                            "yt-dlp transient failure, retrying url=%s extractorArgs=%s retry=%d",
                            canonicalUrl,
                            attempt.extractorArgs ?: "<default>",
                            retryIndex + 1,
                        )
                    }
                }.getOrNull()

                if (info != null) {
                    mapInfo(info)?.let { return@withContext it }
                    sawNoVideo = true
                }
            }
        }

        val reason = buildFailureMessage(
            sawNoVideo = sawNoVideo,
            sawTransientNetworkIssue = sawTransientNetworkIssue,
            sawAuthIssue = sawAuthIssue,
            hasCookie = !cookie.isNullOrBlank(),
        )
        throw IllegalArgumentException(reason, lastError)
    }

    private fun ensureInitialized(): Boolean {
        if (initialized) return true
        return synchronized(this) {
            if (initialized) return@synchronized true
            runCatching {
                YoutubeDL.getInstance().init(appContext)
            }.onFailure {
                Timber.e(it, "yt-dlp init failed")
            }.isSuccess.also { success ->
                initialized = success
            }
        }
    }

    private fun fetchInfo(
        url: String,
        extractorArgs: String?,
        cookie: String?,
        csrfToken: String?,
    ): Result<VideoInfo> {
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--no-update")
            if (!extractorArgs.isNullOrBlank()) {
                addOption("--extractor-args", extractorArgs)
            }
            if (!cookie.isNullOrBlank()) {
                addOption("--add-header", "Cookie: $cookie")
            }
            if (!csrfToken.isNullOrBlank()) {
                addOption("--add-header", "x-csrf-token: $csrfToken")
            }
        }
        return runCatching { YoutubeDL.getInstance().getInfo(request) }
            .onFailure {
                Timber.w(
                    it,
                    "yt-dlp getInfo failed for url=%s extractorArgs=%s",
                    url,
                    extractorArgs ?: "<default>",
                )
            }
    }

    private fun mapInfo(info: VideoInfo): ParsedVideoInfo? {
        val formats = mutableListOf<VideoFormat>()
        val durationSec = extractDurationSec(info)
        info.formats.orEmpty().forEachIndexed { index, format: YtVideoFormat ->
            val directUrl = format.url.orEmpty().trim()
            if (directUrl.isBlank()) return@forEachIndexed

            val ext = normalizeExt(format.ext, directUrl)
            val resolution = format.formatNote.orEmpty().ifBlank { "原始" }
            val fileSize = format.fileSize.takeIf { it > 0L && !ext.equals("m3u8", ignoreCase = true) }
            val sizeText = when {
                ext.equals("m3u8", ignoreCase = true) -> "分片流"
                fileSize != null -> humanReadableSize(fileSize)
                else -> null
            }
            val downloadable = isLikelyDownloadable(resolution)

            formats += VideoFormat(
                formatId = format.formatId.orEmpty().ifBlank { "yt_x_$index" },
                resolution = resolution,
                ext = ext,
                sizeText = sizeText,
                downloadUrl = directUrl,
                durationSec = durationSec,
                fileSizeBytes = fileSize,
                downloadable = downloadable,
            )
        }

        if (formats.isEmpty()) {
            val directUrl = info.url.orEmpty().trim()
            if (directUrl.isNotBlank()) {
                formats += VideoFormat(
                    formatId = "yt_x_direct",
                    resolution = "原始",
                    ext = normalizeExt(ext = null, fallbackUrl = directUrl),
                    sizeText = null,
                    downloadUrl = directUrl,
                    durationSec = durationSec,
                    fileSizeBytes = null,
                    downloadable = true,
                )
            }
        }

        if (formats.isEmpty()) {
            return null
        }

        val sortedFormats = preferMp4Formats(formats.distinctBy { it.downloadUrl })
        return ParsedVideoInfo(
            title = info.title.orEmpty().ifBlank { "X 视频" },
            coverUrl = info.thumbnail.orEmpty().ifBlank { null },
            formats = sortedFormats,
        )
    }

    private fun isLikelyDownloadable(resolution: String): Boolean {
        val lowerResolution = resolution.lowercase()
        return !lowerResolution.contains("audio only") && !lowerResolution.startsWith("audio")
    }

    private fun extractDurationSec(info: VideoInfo): Double? {
        val method = info.javaClass.methods.firstOrNull { it.name == "getDuration" } ?: return null
        val value = runCatching { method.invoke(info) }.getOrNull() ?: return null
        return when (value) {
            is Number -> value.toDouble().takeIf { it > 0.0 }
            is String -> value.toDoubleOrNull()?.takeIf { it > 0.0 }
            else -> null
        }
    }

    private fun preferMp4Formats(formats: List<VideoFormat>): List<VideoFormat> {
        val mp4 = formats.filter { it.ext.equals("mp4", ignoreCase = true) }
        return if (mp4.isNotEmpty()) mp4 else formats
    }

    private fun normalizeExt(ext: String?, fallbackUrl: String): String {
        val value = ext.orEmpty().trim().lowercase().trimStart('.')
        return when (value) {
            "mp4", "m3u8", "webm", "mov", "mkv" -> value
            "m3u8_native", "hls" -> "m3u8"
            else -> inferExtFromUrl(fallbackUrl)
        }
    }

    private fun inferExtFromUrl(url: String): String {
        val lower = url.substringBefore("?").substringBefore("#").lowercase()
        return when {
            lower.contains(".m3u8") -> "m3u8"
            lower.contains(".mp4") -> "mp4"
            lower.contains(".webm") -> "webm"
            lower.contains(".mov") -> "mov"
            else -> "mp4"
        }
    }

    private fun humanReadableSize(bytes: Long): String {
        if (bytes <= 0L) return "0B"
        val kb = bytes / 1024.0
        if (kb < 1024.0) {
            return String.format(Locale.getDefault(), "%.0fKB", kb)
        }
        val mb = kb / 1024.0
        if (mb < 1024.0) {
            return String.format(Locale.getDefault(), "%.1fMB", mb)
        }
        return String.format(Locale.getDefault(), "%.2fGB", mb / 1024.0)
    }

    private fun classifyFailure(throwable: Throwable): FailureType {
        val message = throwable.message.orEmpty().lowercase()
        return when {
            message.contains("could not authenticate you") ||
                message.contains("error(s) while querying api") ||
                message.contains("authentication") -> FailureType.AUTH

            message.contains("no video could be found in this tweet") -> FailureType.NO_VIDEO
            message.contains("unexpected_eof_while_reading") ||
                message.contains("ssl:") ||
                message.contains("tls") ||
                message.contains("connection reset") -> FailureType.TRANSIENT_NETWORK

            else -> FailureType.UNKNOWN
        }
    }

    private fun buildFailureMessage(
        sawNoVideo: Boolean,
        sawTransientNetworkIssue: Boolean,
        sawAuthIssue: Boolean,
        hasCookie: Boolean,
    ): String {
        if (sawAuthIssue) {
            return if (hasCookie) {
                "X 认证失败，当前 Cookie 可能已失效或不匹配，请重新获取 auth_token 与 ct0 后重试"
            } else {
                "X 认证失败，请先在“X 登录设置”中填写有效 Cookie（auth_token + ct0）"
            }
        }

        return when {
            sawNoVideo && sawTransientNetworkIssue ->
                "该 X 链接暂未解析到可下载视频，且网络链路不稳定，请更换代理节点后重试"

            sawNoVideo ->
                "该 X 链接未检测到可下载视频（可能是图文/GIF、已删除、仅登录可见或受地区限制）"

            sawTransientNetworkIssue ->
                "访问 X 失败（TLS/代理链路不稳定），请更换代理节点后重试"

            else -> "当前 X 链接暂时无法解析，请稍后重试"
        }
    }

    private fun extractCookieValue(cookie: String?, key: String): String? {
        val content = cookie?.trim().orEmpty()
        if (content.isBlank()) return null
        return content.split(';')
            .asSequence()
            .map { it.trim() }
            .mapNotNull { part ->
                val index = part.indexOf('=')
                if (index <= 0) return@mapNotNull null
                val name = part.substring(0, index).trim()
                val value = part.substring(index + 1).trim()
                if (name == key && value.isNotBlank()) value else null
            }
            .firstOrNull()
    }

    private fun normalizeXStatusUrl(url: String): String {
        val tweetId = extractTweetId(url) ?: return url
        return "https://x.com/i/status/$tweetId"
    }

    private fun extractTweetId(url: String): String? {
        return Regex("(?:twitter|x)\\.com/.+/status/(\\d+)", RegexOption.IGNORE_CASE)
            .find(url)
            ?.groupValues
            ?.getOrNull(1)
    }

    private fun isXHost(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("x.com") ||
            lower.contains("twitter.com") ||
            lower.contains("fxtwitter.com") ||
            lower.contains("vxtwitter.com") ||
            lower.contains("fixupx.com")
    }

    private data class FetchAttempt(
        val extractorArgs: String?,
        val retryCount: Int,
    )

    private enum class FailureType {
        NO_VIDEO,
        TRANSIENT_NETWORK,
        AUTH,
        UNKNOWN,
    }
}
