package com.example.videodownloader.domain.usecase

import com.example.videodownloader.domain.model.ParsedVideoInfo
import com.example.videodownloader.domain.model.VideoFormat

data class RankedFormatResult(
    val info: ParsedVideoInfo,
    val recommendedFormatId: String?,
)

private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
private val playableExts = imageExts + setOf("mp4", "m4v", "mov", "mkv", "webm", "ts", "m3u8")

fun rankDisplayableFormats(info: ParsedVideoInfo): RankedFormatResult {
    val displayableFormats = info.formats
        .filter(::isDisplayableFormat)
        .distinctBy { it.downloadUrl }

    val selectedFormats = selectOneVariantPerVideo(displayableFormats)
    val recommendedId = if (selectedFormats.any(::isImageFormat)) {
        null
    } else {
        pickRecommendedFormatId(selectedFormats)
    }
    return RankedFormatResult(
        info = info.copy(formats = selectedFormats),
        recommendedFormatId = recommendedId,
    )
}

fun isDisplayableFormat(format: VideoFormat): Boolean {
    if (!format.downloadable) return false

    val ext = format.ext.lowercase().trimStart('.')
    if (ext !in playableExts) return false

    val label = format.resolution.lowercase()
    if (label.startsWith("audio") || label.contains("audio only")) return false

    val url = format.downloadUrl.lowercase()
    if (url.contains("/audio/") || url.contains("audio_")) return false

    return true
}

private fun isImageFormat(format: VideoFormat): Boolean {
    return format.ext.lowercase().trimStart('.') in imageExts
}

private fun selectOneVariantPerVideo(formats: List<VideoFormat>): List<VideoFormat> {
    if (formats.size <= 1) return formats

    val indexed = formats.withIndex().toList()
    val images = indexed.filter { (_, format) -> isImageFormat(format) }
    val videos = indexed.filterNot { (_, format) -> isImageFormat(format) }
    val selectedVideos = videos
        .groupBy { (_, format) -> format.videoGroupKey() }
        .values
        .sortedBy { group -> group.minOf { it.index } }
        .map { group ->
            val items = group.map { it.value }
            pickRecommendedFormat(items)?.let { recommended ->
                recommended
            } ?: group.minBy { it.index }.value
        }

    val selectedByUrl = (images.map { it.value } + selectedVideos)
        .associateBy { it.downloadUrl }
    return formats.filter { it.downloadUrl in selectedByUrl.keys }
}

private fun VideoFormat.videoGroupKey(): String {
    val url = downloadUrl.lowercase()
    val twimgId = Regex(
        """video\.twimg\.com/(?:ext_tw_video|amplify_video|tweet_video|dm_video)/(\d+)""",
        RegexOption.IGNORE_CASE,
    ).find(url)?.groupValues?.getOrNull(1)
    if (!twimgId.isNullOrBlank()) return "twimg:$twimgId"

    val hlsBase = Regex("""^(.+)_hls_\d+$""", RegexOption.IGNORE_CASE)
        .find(formatId)
        ?.groupValues
        ?.getOrNull(1)
    if (!hlsBase.isNullOrBlank()) return "hls:$hlsBase"

    return "single:$formatId:$downloadUrl"
}

private fun pickRecommendedFormatId(formats: List<VideoFormat>): String? {
    return pickRecommendedFormat(formats)?.formatId
}

private fun pickRecommendedFormat(formats: List<VideoFormat>): VideoFormat? {
    var best: VideoFormat? = null
    var bestScore = Long.MIN_VALUE

    formats.forEach { format ->
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
    return best
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
