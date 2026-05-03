package com.example.videodownloader.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFileNamingTest {
    @Test
    fun buildOutputFileName_usesTitleForSingleImage() {
        val fileName = buildOutputFileName(
            title = "测试图集",
            ext = "webp",
            formatId = "douyin_image_0",
            resolution = "图片1",
            isImage = true,
            totalImageCount = 1,
        )

        assertEquals("测试图集.webp", fileName)
    }

    @Test
    fun buildOutputFileName_appendsSequenceForMultiImages() {
        val first = buildOutputFileName(
            title = "测试图集",
            ext = "webp",
            formatId = "douyin_image_0",
            resolution = "图片1",
            isImage = true,
            totalImageCount = 4,
        )
        val second = buildOutputFileName(
            title = "测试图集",
            ext = "webp",
            formatId = "douyin_image_1",
            resolution = "图片2",
            isImage = true,
            totalImageCount = 4,
        )

        assertEquals("测试图集1.webp", first)
        assertEquals("测试图集2.webp", second)
    }

    @Test
    fun buildOutputFileName_sanitizesIllegalCharacters() {
        val fileName = buildOutputFileName(
            title = "测试:/图集?*#",
            ext = ".jpg",
            formatId = "douyin_image_2",
            resolution = "图片3",
            isImage = true,
            totalImageCount = 5,
        )

        assertEquals("测试__图集___3.jpg", fileName)
    }
}
