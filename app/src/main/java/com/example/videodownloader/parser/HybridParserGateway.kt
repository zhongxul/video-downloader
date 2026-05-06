package com.example.videodownloader.parser

import com.example.videodownloader.core.text.AppText
import com.example.videodownloader.core.text.DefaultAppText
import com.example.videodownloader.domain.model.ParsedVideoInfo
import timber.log.Timber

class HybridParserGateway(
    private val webParser: ParserGateway,
    private val ytDlpParser: ParserGateway,
    private val douyinFallbackParser: ParserGateway? = null,
    private val appText: AppText = DefaultAppText,
) : ParserGateway {
    override suspend fun parse(url: String): ParsedVideoInfo {
        val webResult = runCatching { webParser.parse(url) }
        if (webResult.isSuccess) {
            val parsed = webResult.getOrThrow()
            val douyinFallback = douyinFallbackParser
            if (douyinFallback != null && shouldTryDouyinFallback(url, parsed)) {
                runCatching { douyinFallback.parse(url) }
                    .onFailure { Timber.w(it, "douyin detail fallback failed for url=%s", url) }
                    .getOrNull()
                    ?.takeIf(::hasVideoFormat)
                    ?.let {
                        Timber.i("douyin detail fallback success for url=%s", url)
                        return it
                    }
            }
            return parsed
        }

        val ytResult = runCatching { ytDlpParser.parse(url) }
            .onFailure { Timber.w(it, "yt-dlp fallback failed for url=%s", url) }

        ytResult.getOrNull()?.let {
            Timber.i("yt-dlp fallback success for url=%s", url)
            return it
        }

        val ytError = ytResult.exceptionOrNull()
        if (ytError is IllegalArgumentException && !ytError.message.isNullOrBlank()) {
            throw ytError
        }

        throw webResult.exceptionOrNull() ?: IllegalArgumentException(appText.parseNoDownloadableVideo())
    }

    private fun shouldTryDouyinFallback(url: String, parsed: ParsedVideoInfo): Boolean {
        val lowerUrl = url.lowercase()
        if (!lowerUrl.contains("douyin.com") && !lowerUrl.contains("iesdouyin.com")) return false
        if (hasVideoFormat(parsed)) return false
        return parsed.formats.isNotEmpty() && parsed.formats.all { it.ext.lowercase() in imageExts }
    }

    private fun hasVideoFormat(parsed: ParsedVideoInfo): Boolean {
        return parsed.formats.any { it.ext.lowercase() in videoExts }
    }

    private companion object {
        val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
        val videoExts = setOf("mp4", "m3u8", "mov", "webm")
    }
}
