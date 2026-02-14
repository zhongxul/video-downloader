package com.example.videodownloader.parser

import com.example.videodownloader.domain.model.ParsedVideoInfo
import timber.log.Timber

class HybridParserGateway(
    private val webParser: WebParserGateway,
    private val ytDlpParser: YtDlpParserGateway,
) : ParserGateway {
    override suspend fun parse(url: String): ParsedVideoInfo {
        val webResult = runCatching { webParser.parse(url) }
        if (webResult.isSuccess) {
            return webResult.getOrThrow()
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

        throw webResult.exceptionOrNull() ?: IllegalArgumentException("未能解析到可下载视频")
    }
}
