package com.example.videodownloader.domain.usecase

import com.example.videodownloader.core.text.AppText
import com.example.videodownloader.data.repository.DownloadTaskRepository
import com.example.videodownloader.data.repository.ParseRecordRepository
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.domain.model.ParseRecord
import com.example.videodownloader.domain.model.ParseRecordStatus
import com.example.videodownloader.domain.model.VideoFormat
import com.example.videodownloader.download.DownloadGateway
import com.example.videodownloader.download.DownloadProgressSnapshot
import com.example.videodownloader.download.DownloadProgressState
import com.example.videodownloader.download.StartDownloadResult
import com.example.videodownloader.parser.ParserGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UseCaseMessageProviderTest {
    @Test
    fun `parse link use case should use injected blank input message`() {
        val useCase = ParseLinkUseCase(
            parserGateway = object : ParserGateway {
                override suspend fun parse(url: String) = throw UnsupportedOperationException()
            },
            appText = fakeAppText(inputRequired = "需要先输入内容"),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            useCase.resolveUrl("   ")
        }

        assertEquals("需要先输入内容", error.message)
    }

    @Test
    fun `create download task use case should use injected non downloadable message`() {
        val useCase = CreateDownloadTaskUseCase(
            repository = FakeDownloadTaskRepository(),
            parseRecordRepository = FakeParseRecordRepository(),
            downloadGateway = FakeDownloadGateway(),
            appText = fakeAppText(nonDownloadableOption = "这个选项不能直接下载"),
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                useCase(
                    sourceUrl = "https://example.com/post",
                    title = "test",
                    coverUrl = null,
                    format = VideoFormat(
                        formatId = "f1",
                        resolution = "1080p",
                        ext = "mp4",
                        sizeText = null,
                        downloadUrl = "https://example.com/file.mp4",
                        downloadable = false,
                    ),
                )
            }
        }

        assertEquals("这个选项不能直接下载", error.message)
    }

    @Test
    fun `sync download status should use injected missing external id message`() = runBlocking {
        val task = baseTask().copy(externalDownloadId = null, parseRecordId = "record-1")
        val parseRecord = baseRecord()
        val repository = FakeDownloadTaskRepository(activeTasks = mutableListOf(task))
        val parseRepository = FakeParseRecordRepository(records = mutableMapOf(parseRecord.id to parseRecord))
        val useCase = SyncDownloadStatusUseCase(
            repository = repository,
            parseRecordRepository = parseRepository,
            downloadGateway = FakeDownloadGateway(),
            appText = fakeAppText(
                missingSystemDownloadId = "缺少系统下载编号",
                downloadFailed = "任务失败",
            ),
        )

        useCase()

        assertEquals("缺少系统下载编号", repository.updatedTasks.last().errorMessage)
        assertEquals("缺少系统下载编号", parseRepository.updatedRecords.last().message)
    }

    private fun fakeAppText(
        inputRequired: String = "请输入内容",
        nonDownloadableOption: String = "不可下载",
        missingSystemDownloadId: String = "缺少系统下载 ID",
        downloadFailed: String = "下载失败",
    ): AppText {
        return object : AppText {
            override fun inputRequired() = inputRequired
            override fun invalidLink() = "无效链接"
            override fun nonDownloadableOption() = nonDownloadableOption
            override fun downloadQueued() = "已加入下载队列"
            override fun taskNotFound() = "任务不存在"
            override fun missingSystemDownloadId() = missingSystemDownloadId
            override fun downloadSuccess() = "下载成功"
            override fun downloadFailed() = downloadFailed
            override fun downloadCanceled() = "下载已取消"
            override fun taskQueued() = "任务排队中"
            override fun downloading() = "下载中"
            override fun downloadedFileMissing() = "下载文件不存在"
            override fun downloadedFileEmpty() = "下载文件为空"
            override fun downloadedPlaylistNotMerged() = "下载结果仍是 m3u8 播放清单"
            override fun downloadedHtmlInsteadOfVideo() = "下载结果不是视频文件"
            override fun downloadedImageInvalid() = "下载图片格式异常"
            override fun downloadedVideoInvalid() = "下载文件格式异常"
            override fun downloadTaskMissingOrCleaned() = "下载任务不存在或已被系统清理"
            override fun downloadStatusUnknown() = "下载状态未知"
            override fun downloadCanceledByUser() = "下载已取消"
            override fun m3u8DownloadFailed() = "m3u8 下载失败"
            override fun downloadTaskMissingOrCleared() = "下载任务不存在或已被清理"
            override fun downloadCannotResume(reason: Int) = "下载无法恢复（原因码:$reason）"
            override fun storageDeviceNotFound() = "未找到存储设备"
            override fun targetFileAlreadyExists() = "目标文件已存在"
            override fun fileReadWriteFailed(reason: Int) = "文件读写失败（原因码:$reason）"
            override fun networkDataError(reason: Int) = "网络数据错误（原因码:$reason）"
            override fun insufficientStorage() = "存储空间不足"
            override fun tooManyRedirects() = "重定向过多"
            override fun serverStatusError(reason: Int) = "服务器返回异常状态码（原因码:$reason）"
            override fun unknownDownloadError(reason: Int) = "未知错误（原因码:$reason）"
            override fun genericDownloadFailed(reason: Int) = "下载失败（原因码:$reason）"
            override fun downloadPreflightHttpFailed(code: Int) = "下载预检请求失败，HTTP $code"
            override fun downloadNonVideoContentType(contentType: String) = "该链接返回非视频内容类型：$contentType"
            override fun downloadOptionNotPlayable() = "该下载选项返回的不是可直接播放的视频，请更换其他选项"
            override fun parseNoDisplayableMedia() = "未能解析到可展示媒体内容"
            override fun xNetworkUnavailable() = "当前网络可能无法访问 X 资源"
            override fun parseNoDownloadableVideo() = "未能解析到可下载视频"
            override fun parserInitFailed() = "解析器初始化失败"
            override fun xAuthFailedWithCookie() = "X 认证失败，Cookie 已失效"
            override fun xAuthFailedWithoutCookie() = "X 认证失败，请先填写 Cookie"
            override fun xNoVideoWithNetworkIssue() = "该 X 链接暂未解析到可下载视频，且网络链路不稳定"
            override fun xNoVideoFound() = "该 X 链接未检测到可下载视频"
            override fun xAccessFailed() = "访问 X 失败"
            override fun xTemporaryParseFailed() = "当前 X 链接暂时无法解析"
            override fun xCookieRequired() = "请先保存 Cookie"
            override fun xCookieMissingFields() = "Cookie 缺少字段"
            override fun xCookieExpired() = "X Cookie 已失效"
            override fun xCookieValidationSkipped() = "当前网络无法校验 Cookie"
        }
    }

    private fun baseTask(): DownloadTask {
        return DownloadTask(
            id = "task-1",
            parseRecordId = null,
            sourceUrl = "https://example.com/post",
            downloadUrl = "https://example.com/file.mp4",
            title = "task",
            coverUrl = null,
            selectedFormatId = "format-1",
            selectedResolution = "1080p",
            selectedExt = "mp4",
            status = DownloadTaskStatus.QUEUED,
            progress = 0,
            saveUri = null,
            errorMessage = null,
            createdAt = 1L,
            updatedAt = 1L,
            retryFromTaskId = null,
            externalDownloadId = 1L,
        )
    }

    private fun baseRecord(): ParseRecord {
        return ParseRecord(
            id = "record-1",
            rawInput = "https://example.com/post",
            resolvedUrl = "https://example.com/post",
            title = "record",
            coverUrl = null,
            status = ParseRecordStatus.QUEUED,
            message = null,
            selectedFormatLabel = null,
            selectedExt = null,
            taskId = null,
            createdAt = 1L,
            updatedAt = 1L,
        )
    }
}

private class FakeDownloadTaskRepository(
    activeTasks: MutableList<DownloadTask> = mutableListOf(),
) : DownloadTaskRepository {
    private val tasks = linkedMapOf<String, DownloadTask>().apply {
        activeTasks.forEach { put(it.id, it) }
    }
    val updatedTasks = mutableListOf<DownloadTask>()

    override suspend fun insertTask(task: DownloadTask) {
        tasks[task.id] = task
    }

    override suspend fun updateTask(task: DownloadTask) {
        tasks[task.id] = task
        updatedTasks += task
    }

    override suspend fun getTask(taskId: String): DownloadTask? = tasks[taskId]

    override suspend fun getTasks(taskIds: List<String>): List<DownloadTask> = taskIds.mapNotNull(tasks::get)

    override suspend fun getActiveTasks(): List<DownloadTask> = tasks.values.toList()

    override suspend fun deleteTasks(taskIds: List<String>) {
        taskIds.forEach(tasks::remove)
    }

    override suspend fun clearFinishedTasks() = Unit

    override fun observeTask(taskId: String): Flow<DownloadTask?> = emptyFlow()

    override fun observeTasks(): Flow<List<DownloadTask>> = emptyFlow()
}

private class FakeParseRecordRepository(
    records: MutableMap<String, ParseRecord> = mutableMapOf(),
) : ParseRecordRepository {
    private val store = records
    val updatedRecords = mutableListOf<ParseRecord>()

    override suspend fun insertRecord(record: ParseRecord) {
        store[record.id] = record
    }

    override suspend fun updateRecord(record: ParseRecord) {
        store[record.id] = record
        updatedRecords += record
    }

    override suspend fun getRecord(recordId: String): ParseRecord? = store[recordId]

    override suspend fun deleteRecords(recordIds: List<String>) {
        recordIds.forEach(store::remove)
    }

    override fun observeRecords(): Flow<List<ParseRecord>> = emptyFlow()
}

private class FakeDownloadGateway : DownloadGateway {
    override suspend fun startDownload(url: String, fileName: String): StartDownloadResult {
        return StartDownloadResult(externalId = 1L, saveUri = "file:///tmp/test.mp4", fileName = fileName)
    }

    override suspend fun queryDownloadProgress(externalId: Long): DownloadProgressSnapshot {
        return DownloadProgressSnapshot(
            state = DownloadProgressState.SUCCESS,
            progress = 100,
            saveUri = "file:///tmp/test.mp4",
            errorMessage = null,
        )
    }

    override suspend fun cancelDownload(externalId: Long) = Unit
}
