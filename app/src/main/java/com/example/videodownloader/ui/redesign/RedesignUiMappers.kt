package com.example.videodownloader.ui.redesign

import com.example.videodownloader.di.ParseResultPayload
import com.example.videodownloader.domain.model.DownloadTask
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.domain.model.ParseRecord
import com.example.videodownloader.domain.model.ParseRecordStatus
import com.example.videodownloader.domain.model.VideoFormat
import com.example.videodownloader.domain.usecase.isDisplayableFormat
import com.example.videodownloader.ui.redesign.detail.DetailUiState
import com.example.videodownloader.ui.redesign.detail.DetailMediaItem
import com.example.videodownloader.ui.redesign.detail.MetaItem
import com.example.videodownloader.ui.redesign.detail.TaskStatus
import com.example.videodownloader.ui.redesign.download.QueueSummary
import com.example.videodownloader.ui.redesign.download.RecentParseInfo
import com.example.videodownloader.ui.redesign.library.CompletedItem
import com.example.videodownloader.ui.redesign.library.InProgressGroup
import com.example.videodownloader.ui.redesign.library.LibraryTab
import com.example.videodownloader.ui.redesign.library.LibraryUiState
import com.example.videodownloader.ui.redesign.library.ParseRecordItem
import com.example.videodownloader.ui.redesign.parse_result.ParseResultUiState
import com.example.videodownloader.ui.redesign.parse_result.ResourceItem
import com.example.videodownloader.ui.redesign.parse_result.VersionInfo

private val imageExts = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
private val videoExts = setOf("mp4", "m4v", "mov", "mkv", "webm", "ts", "m3u8")

fun ParseResultPayload.toRedesignParseResultUiState(currentIndex: Int = 0): ParseResultUiState {
    val formats = parsedInfo.formats.filter(::isDisplayableFormat)
    val safeIndex = currentIndex.coerceIn(0, (formats.size - 1).coerceAtLeast(0))
    val selected = formats.getOrNull(safeIndex)
    return ParseResultUiState(
        title = parsedInfo.title,
        sourceTag = sourceUrl.sourceTag(formats),
        resourceMeta = formats.resourceMeta(),
        currentIndex = safeIndex,
        resources = formats.map { format ->
            val isImage = format.ext.lowercase() in imageExts
            val isVideo = format.ext.lowercase() in videoExts
            ResourceItem(
                id = format.formatId,
                thumbnailUrl = if (isImage) format.downloadUrl else format.thumbnailUrl ?: parsedInfo.coverUrl ?: format.downloadUrl,
                previewUrl = if (isImage) format.downloadUrl else format.thumbnailUrl ?: parsedInfo.coverUrl ?: format.downloadUrl,
                mediaUrl = format.downloadUrl,
                isVideo = isVideo && !isImage,
            )
        },
        selectedVersion = selected?.toVersionInfo(index = safeIndex),
    )
}

fun buildRedesignLibraryUiState(
    tasks: List<DownloadTask>,
    records: List<ParseRecord>,
    currentTab: LibraryTab,
    nowMillis: Long = System.currentTimeMillis(),
): LibraryUiState {
    val activeTasks = tasks.filter { it.status != DownloadTaskStatus.SUCCESS }
    val completedTasks = tasks.filter { it.status == DownloadTaskStatus.SUCCESS }
    val titleByRecordId = records.associate { it.id to (it.title ?: it.resolvedUrl ?: it.rawInput) }
    val inProgressGroups = tasks
        .groupBy { it.parseRecordId ?: it.id }
        .values
        .filter { group -> group.any { it.status != DownloadTaskStatus.SUCCESS } }
    return LibraryUiState(
        currentTab = currentTab,
        inProgressItems = inProgressGroups
            .map { group -> group.toInProgressGroup(titleByRecordId) }
            .sortedByDescending { it.progress },
        completedItems = completedTasks
            .groupBy { it.parseRecordId ?: it.id }
            .values
            .map { group ->
                val first = group.maxBy { it.updatedAt }
                val coverTask = group.firstOrNull { it.selectedExt.lowercase() in imageExts }
                    ?: group.firstOrNull { !it.coverUrl.isNullOrBlank() }
                    ?: first
                CompletedItem(
                    id = first.parseRecordId ?: first.id,
                    title = if (group.size > 1) "${titleByRecordId[first.parseRecordId].orEmpty().ifBlank { first.title }} · ${group.size} 项" else first.title,
                    thumbnailUrl = coverTask.mediaPreviewUrl(),
                    isVideo = coverTask.selectedExt.lowercase() in videoExts && coverTask.selectedExt.lowercase() !in imageExts,
                    taskIds = group.map { it.id },
                    itemCount = group.size,
                )
            }
            .sortedByDescending { item -> completedTasks.filter { it.id in item.taskIds }.maxOfOrNull { it.updatedAt } ?: 0L },
        parseRecords = records
            .sortedByDescending { it.updatedAt }
            .map { record ->
                ParseRecordItem(
                    id = record.id,
                    title = record.title ?: record.resolvedUrl ?: record.rawInput,
                    timeAgo = record.updatedAt.relativeTime(nowMillis),
                    status = record.status.displayText(),
                    sourceUrl = record.resolvedUrl ?: record.rawInput,
                    message = record.message.orEmpty(),
                )
            },
        inProgressCount = activeTasks.size,
        completedCount = completedTasks.size,
        parseRecordCount = records.size,
    )
}

fun buildRecentParseInfo(records: List<ParseRecord>, nowMillis: Long = System.currentTimeMillis()): RecentParseInfo? {
    val record = records.maxByOrNull { it.updatedAt } ?: return null
    return RecentParseInfo(
        title = record.title ?: record.resolvedUrl ?: record.rawInput,
        resourceDesc = record.selectedFormatLabel ?: record.selectedExt ?: record.status.displayText(),
        timeAgo = record.updatedAt.relativeTime(nowMillis),
    )
}

fun buildQueueSummary(tasks: List<DownloadTask>): QueueSummary? {
    val waiting = tasks.count { it.status == DownloadTaskStatus.QUEUED || it.status == DownloadTaskStatus.DOWNLOADING }
    val retry = tasks.count { it.status == DownloadTaskStatus.FAILED }
    return if (waiting == 0 && retry == 0) null else QueueSummary(waitingCount = waiting, retryCount = retry)
}

fun DownloadTask.toRedesignDetailUiState(): DetailUiState {
    return listOf(this).toRedesignDetailUiState(id)
}

fun List<DownloadTask>.toRedesignDetailUiState(
    groupId: String,
    currentIndex: Int = 0,
    successOnly: Boolean = false,
): DetailUiState {
    if (isEmpty()) {
        return DetailUiState(taskId = groupId, title = "任务不存在", statusLabel = "未知")
    }
    val displayTasks = if (successOnly) {
        filter { it.status == DownloadTaskStatus.SUCCESS }.ifEmpty { this }
    } else {
        this
    }
    val sorted = displayTasks.sortedBy { it.createdAt }
    val safeIndex = currentIndex.coerceIn(0, sorted.lastIndex)
    val first = sorted.first()
    val selected = sorted[safeIndex]
    val status = when {
        sorted.any { it.status == DownloadTaskStatus.FAILED } -> DownloadTaskStatus.FAILED
        sorted.any { it.status == DownloadTaskStatus.DOWNLOADING } -> DownloadTaskStatus.DOWNLOADING
        sorted.any { it.status == DownloadTaskStatus.QUEUED } -> DownloadTaskStatus.QUEUED
        sorted.all { it.status == DownloadTaskStatus.SUCCESS } -> DownloadTaskStatus.SUCCESS
        sorted.any { it.status == DownloadTaskStatus.CANCELED } -> DownloadTaskStatus.CANCELED
        else -> selected.status
    }
    val mappedStatus = status.toRedesignTaskStatus()
    return DetailUiState(
        taskId = groupId,
        title = if (sorted.size > 1) "${first.title.substringBeforeLast(" · ")} · ${sorted.size} 项" else first.title,
        sourceTitle = sorted.sourceTitle(),
        subtitle = "${first.sourceUrl.sourceName()} · ${sorted.maxOf { it.updatedAt }.relativeTime()}",
        status = mappedStatus,
        statusLabel = status.statusLabel(sorted.firstOrNull { it.errorMessage != null }?.errorMessage),
        previewUrl = selected.coverUrl,
        currentIndex = safeIndex,
        mediaItems = sorted.map {
            DetailMediaItem(
                taskId = it.id,
                title = it.title,
                previewUrl = it.mediaPreviewUrl(),
                mediaUrl = it.saveUri ?: it.downloadUrl,
                saveUri = it.saveUri,
                ext = it.selectedExt,
                isVideo = it.selectedExt.lowercase() in videoExts && it.selectedExt.lowercase() !in imageExts,
            )
        },
        metaItems = listOf(
            MetaItem("资源类型", if (sorted.size > 1) "合集 · ${sorted.size} 项" else "${selected.selectedResolution} · ${selected.selectedExt.uppercase()}"),
            MetaItem("文件状态", "${status.displayText()} · 平均进度 ${sorted.map { it.progress }.average().toInt()}%"),
            MetaItem("异常提示", sorted.firstOrNull { it.errorMessage != null }?.errorMessage ?: "暂无异常"),
        ),
        hint = when (mappedStatus) {
            TaskStatus.FAILED -> "失败任务可以直接重试；如果源站限流，建议稍后再试。"
            TaskStatus.COMPLETED -> "已完成任务默认突出“打开内容”，也可以删除记录。"
            TaskStatus.DOWNLOADING, TaskStatus.QUEUED -> "任务正在队列中，可进入资源库查看整体进度。"
            TaskStatus.PAUSED -> "任务已暂停，可以恢复后继续下载。"
            TaskStatus.UNKNOWN -> "当前任务状态不可识别，请返回资源库刷新。"
        },
    )
}

private fun DownloadTask.mediaPreviewUrl(): String? {
    val ext = selectedExt.lowercase()
    return when {
        ext in imageExts -> saveUri ?: downloadUrl
        !coverUrl.isNullOrBlank() -> coverUrl
        ext in videoExts -> saveUri ?: downloadUrl
        else -> coverUrl ?: saveUri ?: downloadUrl
    }
}

private fun List<DownloadTask>.sourceTitle(): String {
    val first = firstOrNull() ?: return "媒体视频"
    val platform = when {
        first.sourceUrl.contains("douyin", ignoreCase = true) -> "抖音"
        first.sourceUrl.contains("x.com", ignoreCase = true) ||
            first.sourceUrl.contains("twitter.com", ignoreCase = true) -> "X"
        else -> "媒体"
    }
    val hasImage = any { it.selectedExt.lowercase() in imageExts }
    return "$platform${if (hasImage) "图集" else "视频"}"
}

private fun VideoFormat.toVersionInfo(index: Int): VersionInfo {
    val label = if (ext.lowercase() in imageExts) "图片 ${index + 1}" else resolution
    val parts = listOfNotNull(
        label,
        resolution.takeIf { it.isNotBlank() },
        ext.takeIf { it.isNotBlank() },
        sizeText?.takeIf { it.isNotBlank() },
    ).distinct()
    return VersionInfo(
        label = label,
        description = parts.joinToString(" · "),
    )
}

private fun List<VideoFormat>.resourceMeta(): String {
    if (isEmpty()) return "未发现可下载资源"
    val downloadable = count { it.downloadable }
    val imageCount = count { it.ext.lowercase() in imageExts }
    val extSummary = map { it.ext.uppercase() }.distinct().take(3).joinToString(" / ")
    return when {
        imageCount == size -> "原图下载 · $extSummary · 建议整组保存"
        imageCount > 0 -> "$downloadable 项可下载 · 含图片与视频 · $extSummary"
        else -> "$downloadable 项可下载 · $extSummary"
    }
}

private fun String.sourceTag(formats: List<VideoFormat>): String {
    val lower = lowercase()
    val platform = when {
        "douyin" in lower -> "抖音"
        "x.com" in lower || "twitter.com" in lower -> "X"
        else -> "媒体"
    }
    val type = if (formats.any { it.ext.lowercase() in imageExts }) "图集" else "视频"
    return "$platform$type"
}

private fun String.sourceName(): String = when {
    contains("douyin", ignoreCase = true) -> "抖音来源"
    contains("x.com", ignoreCase = true) || contains("twitter.com", ignoreCase = true) -> "X 来源"
    else -> "链接来源"
}

private fun List<DownloadTask>.toInProgressGroup(titleByRecordId: Map<String, String>): InProgressGroup {
    val first = first()
    val success = count { it.status == DownloadTaskStatus.SUCCESS }
    val downloading = count { it.status == DownloadTaskStatus.DOWNLOADING || it.status == DownloadTaskStatus.QUEUED }
    val retry = count { it.status == DownloadTaskStatus.FAILED }
    val avgProgress = map { it.progress.coerceIn(0, 100) }.average().takeIf { !it.isNaN() } ?: 0.0
    return InProgressGroup(
        id = first.parseRecordId ?: first.id,
        title = first.parseRecordId?.let { titleByRecordId[it] } ?: first.title,
        taskIds = map { it.id },
        totalCount = size,
        successCount = success,
        downloadingCount = downloading,
        retryCount = retry,
        progress = (avgProgress / 100.0).toFloat(),
    )
}

private fun DownloadTaskStatus.toRedesignTaskStatus(): TaskStatus = when (this) {
    DownloadTaskStatus.QUEUED -> TaskStatus.QUEUED
    DownloadTaskStatus.DOWNLOADING -> TaskStatus.DOWNLOADING
    DownloadTaskStatus.SUCCESS -> TaskStatus.COMPLETED
    DownloadTaskStatus.FAILED -> TaskStatus.FAILED
    DownloadTaskStatus.CANCELED -> TaskStatus.PAUSED
}

private fun DownloadTaskStatus.statusLabel(errorMessage: String?): String = when (this) {
    DownloadTaskStatus.QUEUED -> "排队中"
    DownloadTaskStatus.DOWNLOADING -> "下载中"
    DownloadTaskStatus.SUCCESS -> "已完成 · 封面与媒体信息完整"
    DownloadTaskStatus.FAILED -> "失败 · ${errorMessage ?: "等待重试"}"
    DownloadTaskStatus.CANCELED -> "已暂停"
}

private fun DownloadTaskStatus.displayText(): String = when (this) {
    DownloadTaskStatus.QUEUED -> "排队中"
    DownloadTaskStatus.DOWNLOADING -> "下载中"
    DownloadTaskStatus.SUCCESS -> "已完成"
    DownloadTaskStatus.FAILED -> "失败"
    DownloadTaskStatus.CANCELED -> "已暂停"
}

private fun ParseRecordStatus.displayText(): String = when (this) {
    ParseRecordStatus.PARSED -> "解析成功"
    ParseRecordStatus.PARSE_FAILED -> "解析失败"
    ParseRecordStatus.QUEUED -> "已加入队列"
    ParseRecordStatus.DOWNLOADING -> "下载中"
    ParseRecordStatus.SUCCESS -> "已完成"
    ParseRecordStatus.FAILED -> "失败"
    ParseRecordStatus.CANCELED -> "已取消"
}

private fun Long.relativeTime(nowMillis: Long = System.currentTimeMillis()): String {
    val delta = (nowMillis - this).coerceAtLeast(0L)
    val minutes = delta / 60_000L
    val hours = minutes / 60L
    val days = hours / 24L
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        hours < 24 -> "${hours} 小时前"
        else -> "${days} 天前"
    }
}
