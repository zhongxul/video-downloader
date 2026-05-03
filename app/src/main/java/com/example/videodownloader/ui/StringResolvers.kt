package com.example.videodownloader.ui

import android.content.Context
import com.example.videodownloader.R
import com.example.videodownloader.domain.model.DownloadTaskStatus
import com.example.videodownloader.domain.model.ParseRecordStatus

fun Context.downloadTaskStatusText(status: DownloadTaskStatus): String {
    return when (status) {
        DownloadTaskStatus.QUEUED -> getString(R.string.history_status_queued)
        DownloadTaskStatus.DOWNLOADING -> getString(R.string.history_status_downloading)
        DownloadTaskStatus.SUCCESS -> getString(R.string.history_status_success)
        DownloadTaskStatus.FAILED -> getString(R.string.history_status_failed)
        DownloadTaskStatus.CANCELED -> getString(R.string.history_status_canceled)
    }
}

fun Context.parseRecordStatusText(status: ParseRecordStatus): String {
    return when (status) {
        ParseRecordStatus.PARSED -> getString(R.string.history_parse_status_parsed)
        ParseRecordStatus.PARSE_FAILED -> getString(R.string.history_parse_status_parse_failed)
        ParseRecordStatus.QUEUED -> getString(R.string.history_status_queued)
        ParseRecordStatus.DOWNLOADING -> getString(R.string.history_status_downloading)
        ParseRecordStatus.SUCCESS -> getString(R.string.history_parse_status_success)
        ParseRecordStatus.FAILED -> getString(R.string.history_parse_status_failed)
        ParseRecordStatus.CANCELED -> getString(R.string.history_status_canceled)
    }
}
