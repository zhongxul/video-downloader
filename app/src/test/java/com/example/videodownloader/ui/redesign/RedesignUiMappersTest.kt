package com.example.videodownloader.ui.redesign

import com.example.videodownloader.di.ParseResultPayload
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.domain.model.ParseRecord
import com.example.videodownloader.domain.model.ParseRecordStatus
import com.example.videodownloader.domain.model.ParsedVideoInfo
import com.example.videodownloader.domain.model.VideoFormat
import com.example.videodownloader.ui.redesign.detail.TaskStatus
import com.example.videodownloader.ui.redesign.library.LibraryTab
import org.junit.Assert.assertEquals
import org.junit.Test

class RedesignUiMappersTest {
    @Test
    fun parseResultPayloadMapsResourcesAndSelectedVersion() {
        val payload = ParseResultPayload(
            parsedInfo = ParsedVideoInfo(
                title = "图集标题",
                coverUrl = "https://example.com/cover.webp",
                formats = listOf(
                    VideoFormat("img_1", "原图 1", "webp", "1.2MB", "https://example.com/1.webp"),
                    VideoFormat("img_2", "原图 2", "webp", "1.4MB", "https://example.com/2.webp"),
                ),
            ),
            sourceUrl = "https://v.douyin.com/abc",
            parseRecordId = "record-1",
            recommendedFormatId = null,
        )

        val state = payload.toRedesignParseResultUiState(currentIndex = 1)

        assertEquals("图集标题", state.title)
        assertEquals("抖音图集", state.sourceTag)
        assertEquals(2, state.resources.size)
        assertEquals("https://example.com/2.webp", state.resources[1].thumbnailUrl)
        assertEquals("图片 2 · 原图 2 · webp · 1.4MB", state.selectedVersion?.description)
    }

    @Test
    fun libraryStateGroupsTasksAndRecords() {
        val tasks = listOf(
            task("queued", DownloadTaskStatus.QUEUED, parseRecordId = "record-a", progress = 0),
            task("success", DownloadTaskStatus.SUCCESS, parseRecordId = "record-a", progress = 100),
            task("failed", DownloadTaskStatus.FAILED, parseRecordId = "record-a", progress = 20),
        )
        val records = listOf(record("record-a", ParseRecordStatus.PARSED, title = "图集标题"))

        val state = buildRedesignLibraryUiState(
            tasks = tasks,
            records = records,
            currentTab = LibraryTab.IN_PROGRESS,
            nowMillis = 2_000L,
        )

        assertEquals(2, state.inProgressCount)
        assertEquals(1, state.completedCount)
        assertEquals(1, state.parseRecordCount)
        assertEquals("record-a", state.inProgressItems.single().id)
        assertEquals(3, state.inProgressItems.single().totalCount)
        assertEquals(1, state.inProgressItems.single().successCount)
        assertEquals(1, state.inProgressItems.single().retryCount)
        assertEquals("图集标题", state.parseRecords.single().title)
    }

    @Test
    fun detailStateMapsFailedTaskForDiagnostics() {
        val state = task(
            id = "task-1",
            status = DownloadTaskStatus.FAILED,
            errorMessage = "文件不存在",
            saveUri = "content://downloads/item.mp4",
        ).toRedesignDetailUiState()

        assertEquals("task-1", state.taskId)
        assertEquals(TaskStatus.FAILED, state.status)
        assertEquals("失败 · 文件不存在", state.statusLabel)
        assertEquals(null, state.metaItems.firstOrNull { it.label == "保存路径" })
        assertEquals("文件不存在", state.metaItems.first { it.label == "异常提示" }.value)
    }

    @Test
    fun completedItemsAreGroupedByParseRecord() {
        val tasks = listOf(
            task("img-1", DownloadTaskStatus.SUCCESS, parseRecordId = "record-gallery", progress = 100),
            task("img-2", DownloadTaskStatus.SUCCESS, parseRecordId = "record-gallery", progress = 100),
        )
        val records = listOf(record("record-gallery", ParseRecordStatus.SUCCESS, title = "图集标题"))

        val state = buildRedesignLibraryUiState(
            tasks = tasks,
            records = records,
            currentTab = LibraryTab.COMPLETED,
            nowMillis = 2_000L,
        )

        val item = state.completedItems.single()
        assertEquals("record-gallery", item.id)
        assertEquals(2, item.itemCount)
        assertEquals(listOf("img-1", "img-2"), item.taskIds)
    }

    private fun task(
        id: String,
        status: DownloadTaskStatus,
        parseRecordId: String? = null,
        progress: Int = 0,
        errorMessage: String? = null,
        saveUri: String? = null,
    ) = DownloadTask(
        id = id,
        parseRecordId = parseRecordId,
        sourceUrl = "https://example.com/post",
        downloadUrl = "https://example.com/file.mp4",
        title = "图集标题",
        coverUrl = "https://example.com/cover.jpg",
        selectedFormatId = "fmt",
        selectedResolution = "1080p",
        selectedExt = "mp4",
        status = status,
        progress = progress,
        saveUri = saveUri,
        errorMessage = errorMessage,
        createdAt = 1_000L,
        updatedAt = 1_500L,
        retryFromTaskId = null,
        externalDownloadId = null,
    )

    private fun record(
        id: String,
        status: ParseRecordStatus,
        title: String?,
    ) = ParseRecord(
        id = id,
        rawInput = "https://example.com/raw",
        resolvedUrl = "https://example.com/post",
        title = title,
        coverUrl = null,
        status = status,
        message = null,
        selectedFormatLabel = null,
        selectedExt = null,
        taskId = null,
        createdAt = 1_000L,
        updatedAt = 1_500L,
    )
}
