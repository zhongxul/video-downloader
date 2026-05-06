package com.example.videodownloader.parser

import com.example.videodownloader.domain.model.ParsedVideoInfo
import com.example.videodownloader.domain.model.VideoFormat
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class HybridParserGatewayTest {
    @Test
    fun parse_usesDouyinFallbackWhenWebResultOnlyContainsImages() = runBlocking {
        val webInfo = ParsedVideoInfo(
            title = "网页图集",
            coverUrl = null,
            formats = listOf(imageFormat("web_image", "https://p3-sign.douyinpic.com/foo.webp")),
        )
        val fallbackInfo = ParsedVideoInfo(
            title = "抖音详情图集",
            coverUrl = null,
            formats = listOf(videoFormat("douyin_detail_video", "https://v26-web.douyinvod.com/foo/?mime_type=video_mp4")),
        )
        val gateway = HybridParserGateway(
            webParser = StaticParser(webInfo),
            ytDlpParser = ThrowingParser(),
            douyinFallbackParser = StaticParser(fallbackInfo),
        )

        val parsed = gateway.parse("https://v.douyin.com/example/")

        assertEquals("抖音详情图集", parsed.title)
        assertEquals("douyin_detail_video", parsed.formats.single().formatId)
    }

    @Test
    fun parse_keepsWebResultWhenDouyinFallbackOnlyReturnsImages() = runBlocking {
        val webInfo = ParsedVideoInfo(
            title = "网页图集",
            coverUrl = null,
            formats = listOf(imageFormat("web_image", "https://p3-sign.douyinpic.com/foo.webp")),
        )
        val fallbackInfo = ParsedVideoInfo(
            title = "抖音详情图片",
            coverUrl = null,
            formats = listOf(imageFormat("douyin_detail_image", "https://p3-sign.douyinpic.com/bar.webp")),
        )
        val gateway = HybridParserGateway(
            webParser = StaticParser(webInfo),
            ytDlpParser = ThrowingParser(),
            douyinFallbackParser = StaticParser(fallbackInfo),
        )

        val parsed = gateway.parse("https://v.douyin.com/example/")

        assertEquals("网页图集", parsed.title)
        assertEquals("web_image", parsed.formats.single().formatId)
    }

    private class StaticParser(private val info: ParsedVideoInfo) : ParserGateway {
        override suspend fun parse(url: String): ParsedVideoInfo = info
    }

    private class ThrowingParser : ParserGateway {
        override suspend fun parse(url: String): ParsedVideoInfo {
            throw IllegalArgumentException("解析失败")
        }
    }

    private fun imageFormat(id: String, url: String): VideoFormat {
        return VideoFormat(
            formatId = id,
            resolution = "图片",
            ext = "webp",
            sizeText = null,
            downloadUrl = url,
        )
    }

    private fun videoFormat(id: String, url: String): VideoFormat {
        return VideoFormat(
            formatId = id,
            resolution = "原画",
            ext = "mp4",
            sizeText = null,
            downloadUrl = url,
        )
    }
}
