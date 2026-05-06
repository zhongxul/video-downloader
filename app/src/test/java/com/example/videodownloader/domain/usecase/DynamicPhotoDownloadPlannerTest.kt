package com.example.videodownloader.domain.usecase

import com.example.videodownloader.domain.model.VideoFormat
import org.junit.Assert.assertEquals
import org.junit.Test

class DynamicPhotoDownloadPlannerTest {
    @Test
    fun planDownloadFormatsForSaving_keepsDynamicPhotoAsSingleVideo() {
        val dynamic = VideoFormat(
            formatId = "douyin_detail_1",
            resolution = "动图2",
            ext = "mp4",
            sizeText = null,
            downloadUrl = "https://example.com/live.mp4",
            thumbnailUrl = "https://example.com/live-cover.webp?x=1",
            pairedImageUrl = "https://example.com/live-cover.webp?x=1",
        )

        val planned = planDownloadFormatsForSaving("作品标题", listOf(dynamic))

        assertEquals(1, planned.size)
        assertEquals("作品标题2_动态", planned.single().title)
        assertEquals("mp4", planned.single().format.ext)
        assertEquals("https://example.com/live.mp4", planned.single().format.downloadUrl)
        assertEquals("https://example.com/live-cover.webp?x=1", planned.single().coverUrl)
        assertEquals("https://example.com/live-cover.webp?x=1", planned.single().format.thumbnailUrl)
        assertEquals(null, planned.single().format.pairedImageUrl)
    }

    @Test
    fun planDownloadFormatsForSaving_keepsRegularVideoAsSingleItem() {
        val video = VideoFormat(
            formatId = "video_0",
            resolution = "原画",
            ext = "mp4",
            sizeText = null,
            downloadUrl = "https://example.com/video.mp4",
            thumbnailUrl = "https://example.com/cover.jpg",
        )

        val planned = planDownloadFormatsForSaving("普通视频", listOf(video))

        assertEquals(1, planned.size)
        assertEquals("普通视频", planned.single().title)
        assertEquals("https://example.com/video.mp4", planned.single().format.downloadUrl)
    }
}
