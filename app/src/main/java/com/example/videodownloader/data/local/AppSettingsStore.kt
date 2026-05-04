package com.example.videodownloader.data.local

import android.content.Context

class AppSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun isDownloadNotificationEnabled(): Boolean {
        return preferences.getBoolean(KEY_DOWNLOAD_NOTIFICATION_ENABLED, true)
    }

    fun setDownloadNotificationEnabled(enabled: Boolean) {
        preferences.edit()
            .putBoolean(KEY_DOWNLOAD_NOTIFICATION_ENABLED, enabled)
            .apply()
    }

    companion object {
        private const val PREF_NAME = "video_downloader_settings"
        private const val KEY_DOWNLOAD_NOTIFICATION_ENABLED = "download_notification_enabled"
    }
}
