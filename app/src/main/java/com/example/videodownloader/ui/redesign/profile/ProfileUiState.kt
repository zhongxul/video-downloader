package com.example.videodownloader.ui.redesign.profile

data class ProfileUiState(
    val namingRule: String = "标题 + 序号",
    val networkPolicy: String = "仅 Wi-Fi 自动下载",
    val savePath: String = "Download/Video Downloader",
    val notificationEnabled: Boolean = true,
)
