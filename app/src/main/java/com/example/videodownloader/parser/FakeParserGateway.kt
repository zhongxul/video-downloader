package com.example.videodownloader.parser

import com.example.videodownloader.domain.model.ParsedVideoInfo
import com.example.videodownloader.domain.model.VideoFormat
import kotlinx.coroutines.delay

class FakeParserGateway : ParserGateway {
    override suspend fun parse(url: String): ParsedVideoInfo {
        delay(600)
        return ParsedVideoInfo(
            title = "示例视频",
            coverUrl = null,
            formats = listOf(
                VideoFormat(
                    formatId = "fhd",
                    resolution = "1080p",
                    ext = "mp4",
                    sizeText = "约25MB",
                    downloadUrl = url,
                ),
                VideoFormat(
                    formatId = "hd",
                    resolution = "720p",
                    ext = "mp4",
                    sizeText = "约14MB",
                    downloadUrl = url,
                ),
            ),
        )
    }
}
