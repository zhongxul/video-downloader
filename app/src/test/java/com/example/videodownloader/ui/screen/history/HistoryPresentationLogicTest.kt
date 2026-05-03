package com.example.videodownloader.ui.screen.history

import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.ui.screen.result.buildBatchDownloadMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryPresentationLogicTest {
    @Test
    fun `same parse record group should appear in downloading and failed tabs`() {
        val tasks = listOf(
            task(
                id = "downloading",
                parseRecordId = "record-1",
                status = DownloadTaskStatus.DOWNLOADING,
                updatedAt = 10L,
            ),
            task(
                id = "failed",
                parseRecordId = "record-1",
                status = DownloadTaskStatus.FAILED,
                updatedAt = 20L,
            ),
        )

        val downloadingGroups = buildDownloadGroupsForUi(tasks, DownloadSubTab.DOWNLOADING)
        val failedGroups = buildDownloadGroupsForUi(tasks, DownloadSubTab.FAILED)

        assertEquals(1, downloadingGroups.size)
        assertEquals(1, failedGroups.size)
        assertEquals("parse:record-1", downloadingGroups.first().groupKey)
        assertEquals("parse:record-1", failedGroups.first().groupKey)
        assertEquals(listOf("downloading"), downloadingGroups.first().displayItems.map { it.id })
        assertEquals(listOf("failed"), failedGroups.first().displayItems.map { it.id })
        assertEquals(2, downloadingGroups.first().totalItems)
        assertEquals(2, failedGroups.first().totalItems)
    }

    @Test
    fun `single task without parse record should stay as single group`() {
        val groups = buildDownloadGroupsForUi(
            tasks = listOf(
                task(
                    id = "task-1",
                    parseRecordId = null,
                    status = DownloadTaskStatus.FAILED,
                    updatedAt = 5L,
                ),
            ),
            subTab = DownloadSubTab.FAILED,
        )

        assertEquals(1, groups.size)
        assertEquals("task:task-1", groups.first().groupKey)
        assertTrue(groups.first().isSingle)
    }

    @Test
    fun `batch download message should reflect full success`() {
        assertEquals("全部媒体已加入下载队列（3项）", buildBatchDownloadMessage(successCount = 3, failedCount = 0))
    }

    @Test
    fun `batch download message should reflect mixed result`() {
        assertEquals("已加入 2 项，失败 1 项", buildBatchDownloadMessage(successCount = 2, failedCount = 1))
    }

    private fun task(
        id: String,
        parseRecordId: String?,
        status: DownloadTaskStatus,
        updatedAt: Long,
    ) = DownloadTask(
        id = id,
        parseRecordId = parseRecordId,
        sourceUrl = "https://example.com/$id",
        downloadUrl = "https://example.com/$id.mp4",
        title = id,
        coverUrl = null,
        selectedFormatId = "format-$id",
        selectedResolution = "1080p",
        selectedExt = "mp4",
        status = status,
        progress = if (status == DownloadTaskStatus.DOWNLOADING) 50 else 0,
        saveUri = null,
        errorMessage = null,
        createdAt = updatedAt,
        updatedAt = updatedAt,
        retryFromTaskId = null,
        externalDownloadId = 1L,
    )
}
