package com.example.videodownloader.ui.screen.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.videodownloader.di.AppContainer
import com.example.videodownloader.domain.model.ParseRecord
import com.example.videodownloader.domain.model.ParseRecordStatus
import com.example.videodownloader.domain.model.ParsedVideoInfo
import com.example.videodownloader.domain.model.VideoFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

data class HomeUiState(
    val linkInput: String = "",
    val parsedSourceUrl: String? = null,
    val parseRecordId: String? = null,
    val isParsing: Boolean = false,
    val parseError: String? = null,
    val parsedInfo: ParsedVideoInfo? = null,
    val recommendedFormatId: String? = null,
    val isSubmitting: Boolean = false,
    val submitMessage: String? = null,
)

class HomeViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val probeClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        startDownloadStatusSync()
    }

    fun onLinkChanged(value: String) {
        _uiState.update {
            it.copy(
                linkInput = value,
                parseError = null,
                parsedSourceUrl = null,
                parseRecordId = null,
                parsedInfo = null,
                recommendedFormatId = null,
            )
        }
    }

    fun fillLinkFromClipboard(value: String) {
        if (value.isBlank()) {
            _uiState.update { it.copy(submitMessage = "剪贴板为空") }
            return
        }

        val extracted = container.parseLinkUseCase.extractUrl(value)
        if (extracted != null) {
            _uiState.update {
                it.copy(
                    linkInput = extracted,
                    parseError = null,
                    parsedSourceUrl = null,
                    parseRecordId = null,
                    parsedInfo = null,
                    recommendedFormatId = null,
                    submitMessage = "已从剪贴板提取链接",
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    linkInput = value.trim(),
                    parseError = null,
                    parsedSourceUrl = null,
                    parseRecordId = null,
                    parsedInfo = null,
                    recommendedFormatId = null,
                    submitMessage = "剪贴板中未识别到标准链接",
                )
            }
        }
    }

    fun clearSubmitMessage() {
        _uiState.update { it.copy(submitMessage = null) }
    }

    fun parseLink() {
        val rawInput = uiState.value.linkInput
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isParsing = true,
                    parseError = null,
                    parsedInfo = null,
                    parseRecordId = null,
                    recommendedFormatId = null,
                )
            }

            runCatching {
                val resolvedUrl = container.parseLinkUseCase.resolveUrl(rawInput)
                if (isXLink(resolvedUrl)) {
                    val validation = container.xCookieValidator.validateForParsing()
                    if (!validation.valid && validation.shouldBlock) {
                        throw IllegalArgumentException(validation.message ?: "X Cookie 不可用，请更新后重试")
                    }
                    if (!validation.valid && !validation.message.isNullOrBlank()) {
                        _uiState.update { state -> state.copy(submitMessage = validation.message) }
                    }
                }
                val info = container.parseLinkUseCase(rawInput)
                val enriched = enrichFormatMeta(info)
                val recordId = saveParseRecord(
                    rawInput = rawInput,
                    resolvedUrl = resolvedUrl,
                    title = enriched.info.title,
                    coverUrl = enriched.info.coverUrl,
                    status = ParseRecordStatus.PARSED,
                    message = "解析成功，请选择下载格式",
                )
                ParseSuccessResult(
                    resolvedUrl = resolvedUrl,
                    info = enriched.info,
                    recommendedFormatId = enriched.recommendedFormatId,
                    parseRecordId = recordId,
                )
            }.onSuccess { result ->
                _uiState.update {
                    it.copy(
                        linkInput = result.resolvedUrl,
                        parsedSourceUrl = result.resolvedUrl,
                        parseRecordId = result.parseRecordId,
                        isParsing = false,
                        parsedInfo = result.info,
                        recommendedFormatId = result.recommendedFormatId,
                    )
                }
            }.onFailure { throwable ->
                val resolvedUrl = runCatching { container.parseLinkUseCase.resolveUrl(rawInput) }.getOrNull()
                saveParseFailedRecordAsync(rawInput, resolvedUrl, throwable.message ?: "解析失败")
                _uiState.update {
                    it.copy(
                        isParsing = false,
                        parseError = throwable.message ?: "解析失败，请稍后重试",
                        recommendedFormatId = null,
                    )
                }
            }
        }
    }

    fun createTask(format: VideoFormat) {
        val current = uiState.value
        val parsedInfo = current.parsedInfo ?: return
        val sourceUrl = current.parsedSourceUrl ?: current.linkInput

        if (!format.downloadable) {
            _uiState.update {
                it.copy(submitMessage = "该选项不是可直接下载的视频文件，请选择其他可下载选项")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitMessage = null) }
            runCatching {
                container.createDownloadTaskUseCase(
                    sourceUrl = sourceUrl,
                    title = parsedInfo.title,
                    coverUrl = parsedInfo.coverUrl,
                    format = format,
                    parseRecordId = current.parseRecordId,
                )
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submitMessage = "已加入下载队列",
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        submitMessage = throwable.message ?: "创建下载任务失败",
                    )
                }
            }
        }
    }

    private suspend fun saveParseRecord(
        rawInput: String,
        resolvedUrl: String?,
        title: String?,
        coverUrl: String?,
        status: ParseRecordStatus,
        message: String?,
    ): String {
        val now = System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val record = ParseRecord(
            id = id,
            rawInput = rawInput,
            resolvedUrl = resolvedUrl,
            title = title,
            coverUrl = coverUrl,
            status = status,
            message = message,
            selectedFormatLabel = null,
            selectedExt = null,
            taskId = null,
            createdAt = now,
            updatedAt = now,
        )
        container.parseRecordRepository.insertRecord(record)
        return id
    }

    private fun saveParseFailedRecordAsync(rawInput: String, resolvedUrl: String?, message: String) {
        viewModelScope.launch {
            runCatching {
                saveParseRecord(
                    rawInput = rawInput,
                    resolvedUrl = resolvedUrl,
                    title = null,
                    coverUrl = null,
                    status = ParseRecordStatus.PARSE_FAILED,
                    message = message,
                )
            }
        }
    }

    private fun isXLink(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("x.com") || lower.contains("twitter.com")
    }

    private suspend fun enrichFormatMeta(info: ParsedVideoInfo): EnrichedFormatMeta = withContext(kotlinx.coroutines.Dispatchers.IO) {
        val cookie = container.xCookieStore.getCookie().takeIf { !it.isNullOrBlank() }
        val expandedFormats = expandM3u8Formats(info.formats, cookie)
        val sizeEnrichedFormats = enrichMp4Size(expandedFormats, cookie)

        if (sizeEnrichedFormats.size <= 1) {
            return@withContext EnrichedFormatMeta(
                info = info.copy(formats = sizeEnrichedFormats),
                recommendedFormatId = null,
            )
        }

        val recommendedId = pickRecommendedFormatId(sizeEnrichedFormats)
        val sortedFormats = if (recommendedId.isNullOrBlank()) {
            sizeEnrichedFormats
        } else {
            moveFormatToTop(sizeEnrichedFormats, recommendedId)
        }
        EnrichedFormatMeta(
            info = info.copy(formats = sortedFormats),
            recommendedFormatId = recommendedId,
        )
    }

    private suspend fun enrichMp4Size(formats: List<VideoFormat>, cookie: String?): List<VideoFormat> {
        val targets = formats
            .withIndex()
            .filter { (_, format) ->
                format.downloadable &&
                    format.fileSizeBytes == null &&
                    format.ext.equals("mp4", ignoreCase = true) &&
                    !isLikelyHlsFormat(format) &&
                    needsSizeProbe(format.sizeText)
            }
            .take(8)

        if (targets.isEmpty()) return formats

        val sizeByIndex = coroutineScope {
            targets.map { indexed ->
                async {
                    indexed.index to queryRemoteFileSize(indexed.value.downloadUrl, cookie)
                }
            }.awaitAll().toMap()
        }

        if (sizeByIndex.isEmpty()) return formats

        return formats.mapIndexed { index, format ->
            val remoteSize = sizeByIndex[index] ?: return@mapIndexed format
            if (remoteSize <= 0L) return@mapIndexed format
            format.copy(
                fileSizeBytes = remoteSize,
                sizeText = humanReadableSize(remoteSize),
            )
        }
    }

    private suspend fun expandM3u8Formats(formats: List<VideoFormat>, cookie: String?): List<VideoFormat> {
        val result = mutableListOf<VideoFormat>()
        formats.forEach { format ->
            if (!format.downloadable || !isLikelyHlsFormat(format)) {
                result += format
                return@forEach
            }

            val variants = queryM3u8Variants(format.downloadUrl, cookie)
            if (variants.isNullOrEmpty()) {
                result += normalizeM3u8Format(format)
                return@forEach
            }

            variants.forEachIndexed { index, variant ->
                result += format.copy(
                    formatId = "${format.formatId}_hls_$index",
                    resolution = variant.resolutionLabel,
                    ext = "m3u8",
                    sizeText = variant.bitrateText ?: "分片流",
                    downloadUrl = variant.url,
                    fileSizeBytes = null,
                )
            }
        }
        return result.distinctBy { it.downloadUrl }
    }

    private fun normalizeM3u8Format(format: VideoFormat): VideoFormat {
        val cleanSizeText = format.sizeText.orEmpty().trim()
        return format.copy(
            ext = "m3u8",
            sizeText = if (cleanSizeText.isBlank()) "分片流" else cleanSizeText,
            fileSizeBytes = null,
        )
    }

    private fun isLikelyHlsFormat(format: VideoFormat): Boolean {
        if (format.ext.equals("m3u8", ignoreCase = true)) return true
        val lowerUrl = format.downloadUrl.lowercase()
        if (lowerUrl.contains(".m3u8")) return true
        val sizeText = format.sizeText.orEmpty().lowercase()
        if (sizeText.contains("hls") || sizeText.contains("m3u8") || sizeText.contains("分片流")) return true
        return false
    }

    private fun queryM3u8Variants(url: String, cookie: String?): List<M3u8VariantMeta>? {
        val playlistText = fetchText(url, cookie) ?: return null
        if (!playlistText.contains("#EXTM3U", ignoreCase = true)) return null
        if (!playlistText.contains("#EXT-X-STREAM-INF", ignoreCase = true)) return null

        val result = mutableListOf<M3u8VariantMeta>()
        val lines = playlistText.lines()
        var pendingStreamInfo: String? = null

        lines.forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) return@forEach

            if (line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true)) {
                pendingStreamInfo = line
                return@forEach
            }

            val streamInfo = pendingStreamInfo ?: return@forEach
            if (line.startsWith("#")) return@forEach

            val resolvedUrl = resolveRelativeUrl(url, line) ?: run {
                pendingStreamInfo = null
                return@forEach
            }
            val height = parseM3u8Height(streamInfo)
            val bandwidth = parseM3u8Bandwidth(streamInfo)
            val resolutionLabel = if (height > 0) "${height}p" else "原始"
            val bitrateText = bandwidthToText(bandwidth)
            result += M3u8VariantMeta(
                url = resolvedUrl,
                resolutionLabel = resolutionLabel,
                height = height,
                bitrateText = bitrateText,
            )
            pendingStreamInfo = null
        }

        if (result.isEmpty()) return null
        return result.sortedWith(
            compareByDescending<M3u8VariantMeta> { it.height }
                .thenByDescending { parseBitrateKbps(it.bitrateText) }
                .thenBy { it.url },
        )
    }

    private fun fetchText(url: String, cookie: String?): String? {
        val requestBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        attachSiteHeaders(requestBuilder, url, cookie)

        return runCatching { probeClient.newCall(requestBuilder.build()).execute() }
            .getOrNull()
            ?.use { response ->
                if (!response.isSuccessful) return@use null
                response.body?.string()
            }
    }

    private fun resolveRelativeUrl(baseUrl: String, candidate: String): String? {
        val clean = candidate.trim()
        if (clean.isBlank()) return null
        if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            return clean
        }
        return runCatching { URI(baseUrl).resolve(clean).toString() }.getOrNull()
    }

    private fun parseM3u8Height(streamInfoLine: String): Int {
        val resolution = Regex("RESOLUTION=(\\d+)x(\\d+)", RegexOption.IGNORE_CASE)
            .find(streamInfoLine)
            ?.groupValues
            ?.getOrNull(2)
            ?.toIntOrNull()
        if (resolution != null && resolution > 0) {
            return resolution
        }
        return 0
    }

    private fun parseM3u8Bandwidth(streamInfoLine: String): Long {
        val avg = Regex("AVERAGE-BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
            .find(streamInfoLine)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
        if (avg != null && avg > 0L) {
            return avg
        }
        return Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
            .find(streamInfoLine)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
            ?: 0L
    }

    private fun bandwidthToText(bandwidth: Long): String? {
        if (bandwidth <= 0L) return null
        val kbps = bandwidth / 1000.0
        return String.format(Locale.getDefault(), "%.0fkbps", kbps)
    }

    private fun pickRecommendedFormatId(formats: List<VideoFormat>): String? {
        if (formats.size <= 1) return null

        var best: VideoFormat? = null
        var bestScore = Long.MIN_VALUE

        formats.forEach { format ->
            if (!format.downloadable) return@forEach
            val height = parseResolutionHeight(format.resolution)
            val bitrate = parseBitrateKbps(format.sizeText)
            val fileSize = format.fileSizeBytes ?: 0L
            if (height <= 0 && bitrate <= 0L && fileSize <= 0L) return@forEach

            val score = height.toLong() * 1_000_000_000L + bitrate * 1_000L + fileSize / 1024L
            if (score > bestScore) {
                bestScore = score
                best = format
            }
        }
        return best?.formatId
    }

    private fun moveFormatToTop(formats: List<VideoFormat>, formatId: String): List<VideoFormat> {
        val index = formats.indexOfFirst { it.formatId == formatId }
        if (index <= 0) return formats

        val first = formats[index]
        val remains = formats.filterIndexed { idx, _ -> idx != index }
        return listOf(first) + remains
    }

    private fun parseResolutionHeight(resolution: String?): Int {
        val text = resolution.orEmpty().lowercase()
        if (text.isBlank()) return 0

        val pValue = Regex("(\\d{3,4})\\s*p", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (pValue != null && pValue > 0) return pValue

        val xValue = Regex("(\\d{3,4})\\s*x\\s*(\\d{3,4})", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(2)
            ?.toIntOrNull()
        if (xValue != null && xValue > 0) return xValue

        return 0
    }

    private fun parseBitrateKbps(sizeText: String?): Long {
        val text = sizeText.orEmpty().lowercase().trim()
        if (text.isBlank()) return 0L
        val match = Regex("(\\d+(?:\\.\\d+)?)\\s*(k|m|g)?bps", RegexOption.IGNORE_CASE).find(text) ?: return 0L
        val value = match.groupValues.getOrNull(1)?.toDoubleOrNull() ?: return 0L
        val unit = match.groupValues.getOrNull(2).orEmpty().lowercase()
        val multiplier = when (unit) {
            "g" -> 1_000_000.0
            "m" -> 1_000.0
            "k" -> 1.0
            else -> 0.001
        }
        return (value * multiplier).toLong()
    }

    private fun queryRemoteFileSize(url: String, cookie: String?): Long? {
        val getBuilder = Request.Builder()
            .url(url)
            .get()
            .header("Range", "bytes=0-0")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        attachSiteHeaders(getBuilder, url, cookie)

        runCatching { probeClient.newCall(getBuilder.build()).execute() }
            .getOrNull()
            ?.use { response ->
                if (response.isSuccessful) {
                    val contentRange = response.header("Content-Range").orEmpty()
                    val totalFromRange = Regex("/(\\d+)$").find(contentRange)?.groupValues?.getOrNull(1)?.toLongOrNull()
                    if (totalFromRange != null && totalFromRange > 0L) return totalFromRange

                    val length = response.header("Content-Length")?.toLongOrNull()
                    if (length != null && length > 1024L) return length

                    val bodyLength = response.body?.contentLength() ?: -1L
                    if (bodyLength > 1024L) return bodyLength
                }
            }

        val headBuilder = Request.Builder()
            .url(url)
            .head()
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36")
            .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
        attachSiteHeaders(headBuilder, url, cookie)

        return runCatching { probeClient.newCall(headBuilder.build()).execute() }
            .getOrNull()
            ?.use { response ->
                if (!response.isSuccessful) return@use null
                response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 1024L }
            }
    }

    private fun attachSiteHeaders(builder: Request.Builder, url: String, cookie: String?) {
        val lower = url.lowercase()
        if (lower.contains("douyin.com") || lower.contains("iesdouyin.com")) {
            builder.header("Referer", "https://www.douyin.com/")
        }
        if (lower.contains("x.com") || lower.contains("twitter.com")) {
            builder.header("Referer", "https://x.com/")
            if (!cookie.isNullOrBlank()) {
                builder.header("Cookie", cookie)
            }
        }
    }

    private fun needsSizeProbe(sizeText: String?): Boolean {
        val text = sizeText?.trim().orEmpty()
        if (text.isBlank()) return true
        if (looksLikeBitrateText(text)) return true
        return !looksLikeSizeText(text)
    }

    private fun looksLikeSizeText(text: String): Boolean {
        val lower = text.lowercase()
        if (lower.contains("kbps") || lower.contains("mbps") || lower.contains("gbps")) {
            return false
        }
        return Regex("""\d+(?:\.\d+)?\s*(kb|mb|gb|tb)\b""", RegexOption.IGNORE_CASE).containsMatchIn(lower)
    }

    private fun looksLikeBitrateText(text: String): Boolean {
        return Regex("""\d+(?:\.\d+)?\s*(k|m|g)?bps\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
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

    private data class M3u8VariantMeta(
        val url: String,
        val resolutionLabel: String,
        val height: Int,
        val bitrateText: String?,
    )

    private data class EnrichedFormatMeta(
        val info: ParsedVideoInfo,
        val recommendedFormatId: String?,
    )

    private data class ParseSuccessResult(
        val resolvedUrl: String,
        val info: ParsedVideoInfo,
        val recommendedFormatId: String?,
        val parseRecordId: String,
    )

    private fun startDownloadStatusSync() {
        viewModelScope.launch {
            while (isActive) {
                runCatching {
                    container.syncDownloadStatusUseCase()
                }
                delay(1500L)
            }
        }
    }
}

class HomeViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(container) as T
    }
}
