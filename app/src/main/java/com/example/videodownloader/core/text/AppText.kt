package com.example.videodownloader.core.text

interface AppText {
    fun inputRequired(): String
    fun invalidLink(): String
    fun nonDownloadableOption(): String
    fun downloadQueued(): String
    fun taskNotFound(): String
    fun missingSystemDownloadId(): String
    fun downloadSuccess(): String
    fun downloadFailed(): String
    fun downloadCanceled(): String
    fun taskQueued(): String
    fun downloading(): String
    fun downloadedFileMissing(): String
    fun downloadedFileEmpty(): String
    fun downloadedPlaylistNotMerged(): String
    fun downloadedHtmlInsteadOfVideo(): String
    fun downloadedImageInvalid(): String
    fun downloadedVideoInvalid(): String
    fun downloadTaskMissingOrCleaned(): String
    fun downloadStatusUnknown(): String
    fun downloadCanceledByUser(): String
    fun m3u8DownloadFailed(): String
    fun downloadTaskMissingOrCleared(): String
    fun downloadCannotResume(reason: Int): String
    fun storageDeviceNotFound(): String
    fun targetFileAlreadyExists(): String
    fun fileReadWriteFailed(reason: Int): String
    fun networkDataError(reason: Int): String
    fun insufficientStorage(): String
    fun tooManyRedirects(): String
    fun serverStatusError(reason: Int): String
    fun unknownDownloadError(reason: Int): String
    fun genericDownloadFailed(reason: Int): String
    fun downloadPreflightHttpFailed(code: Int): String
    fun downloadNonVideoContentType(contentType: String): String
    fun downloadOptionNotPlayable(): String
    fun parseNoDisplayableMedia(): String
    fun xNetworkUnavailable(): String
    fun parseNoDownloadableVideo(): String
    fun parserInitFailed(): String
    fun xAuthFailedWithCookie(): String
    fun xAuthFailedWithoutCookie(): String
    fun xNoVideoWithNetworkIssue(): String
    fun xNoVideoFound(): String
    fun xAccessFailed(): String
    fun xTemporaryParseFailed(): String
    fun xCookieRequired(): String
    fun xCookieMissingFields(): String
    fun xCookieExpired(): String
    fun xCookieValidationSkipped(): String

    fun originalQuality(): String = "原始"
    fun segmentedStream(): String = "分片流"
    fun directVideoTitle(): String = "直链视频"
    fun douyinImageParseFailed(): String = "检测到抖音图集，图片地址提取失败"
    fun douyinVideoTitle(): String = "抖音视频"
    fun douyinGalleryTitle(): String = "抖音图集"
    fun gifLabel(index: Int): String = "动图$index"
    fun imageLabel(index: Int): String = "图片$index"
    fun xBroadcastTitle(): String = "X 直播回放"
    fun xVideoTitle(): String = "X 视频"
    fun webVideoTitle(): String = "网页视频"
}
