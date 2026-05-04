package com.example.videodownloader.domain.usecase

import com.example.videodownloader.domain.model.ParsedVideoInfo
import com.example.videodownloader.domain.model.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatRankingTest {
    @Test
    fun rankDisplayableFormats_removesAudioOnlyAndKeepsOneRecommendedVariantPerVideo() {
        val info = ParsedVideoInfo(
            title = "X 视频",
            coverUrl = null,
            formats = listOf(
                VideoFormat("audio-before", "audio only", "m3u8", "128kbps", "https://video.twimg.com/audio/track-a.m3u8"),
                VideoFormat("video-720", "720p", "m3u8", "2500kbps", "https://video.twimg.com/ext_tw_video/123/pu/pl/720.m3u8"),
                VideoFormat("video-480", "480p", "m3u8", "1200kbps", "https://video.twimg.com/ext_tw_video/123/pu/pl/480.m3u8"),
                VideoFormat("video-360", "360p", "m3u8", "800kbps", "https://video.twimg.com/ext_tw_video/123/pu/pl/360.m3u8"),
                VideoFormat("audio-after", "Audio", "mp4", "96kbps", "https://video.twimg.com/audio/track-b.mp4"),
            ),
        )

        val ranked = rankDisplayableFormats(info)

        assertEquals("video-720", ranked.recommendedFormatId)
        assertEquals(listOf("video-720"), ranked.info.formats.map { it.formatId })
    }

    @Test
    fun rankDisplayableFormats_keepsOneVariantForEachDifferentVideoInSameLink() {
        val info = ParsedVideoInfo(
            title = "X 多视频",
            coverUrl = null,
            formats = listOf(
                VideoFormat("video-a-480", "480p", "mp4", "1200kbps", "https://video.twimg.com/ext_tw_video/111/pu/vid/480x480/a.mp4"),
                VideoFormat("video-a-720", "720p", "mp4", "2200kbps", "https://video.twimg.com/ext_tw_video/111/pu/vid/720x720/a.mp4"),
                VideoFormat("video-b-360", "360p", "mp4", "800kbps", "https://video.twimg.com/ext_tw_video/222/pu/vid/360x360/b.mp4"),
                VideoFormat("video-b-720", "720p", "mp4", "2000kbps", "https://video.twimg.com/ext_tw_video/222/pu/vid/720x720/b.mp4"),
            ),
        )

        val ranked = rankDisplayableFormats(info)

        assertEquals(listOf("video-a-720", "video-b-720"), ranked.info.formats.map { it.formatId })
    }

    @Test
    fun rankDisplayableFormats_keepsImagesAndOneVariantPerVideoForMixedPost() {
        val info = ParsedVideoInfo(
            title = "X 图文视频",
            coverUrl = null,
            formats = listOf(
                VideoFormat("image-1", "图片1", "jpg", null, "https://pbs.twimg.com/media/a.jpg?name=orig"),
                VideoFormat("video-480", "480p", "mp4", "1200kbps", "https://video.twimg.com/ext_tw_video/444/pu/vid/480x852/a.mp4"),
                VideoFormat("video-1080", "1080p", "mp4", "4200kbps", "https://video.twimg.com/ext_tw_video/444/pu/vid/1080x1920/a.mp4"),
            ),
        )

        val ranked = rankDisplayableFormats(info)

        assertEquals(listOf("image-1", "video-1080"), ranked.info.formats.map { it.formatId })
    }

    @Test
    fun rankDisplayableFormats_prefersHigherResolutionOverLargerLowerResolutionFile() {
        val info = ParsedVideoInfo(
            title = "X 视频",
            coverUrl = null,
            formats = listOf(
                VideoFormat("video-480-large", "480p", "mp4", "2000kbps", "https://video.twimg.com/ext_tw_video/333/pu/vid/480x480/a.mp4", fileSizeBytes = 80_000_000),
                VideoFormat("video-720-small", "720p", "mp4", "1400kbps", "https://video.twimg.com/ext_tw_video/333/pu/vid/720x720/a.mp4", fileSizeBytes = 40_000_000),
            ),
        )

        val ranked = rankDisplayableFormats(info)

        assertEquals("video-720-small", ranked.recommendedFormatId)
        assertEquals("video-720-small", ranked.info.formats.first().formatId)
    }
}
