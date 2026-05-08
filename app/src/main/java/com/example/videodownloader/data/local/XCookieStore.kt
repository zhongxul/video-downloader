package com.example.videodownloader.data.local

import android.content.Context

class XCookieStore(context: Context) {
    private val preferences = EncryptedPreferenceStore(context, PREF_NAME)

    fun getCookie(): String? {
        return preferences.getString(KEY_X_COOKIE)
            ?.trim()
            ?.takeIf { it.isNotBlank() }
    }

    fun saveCookie(raw: String): String {
        val normalized = normalizeCookie(raw)
        if (normalized.isBlank()) {
            clearCookie()
            return ""
        }

        preferences.putString(KEY_X_COOKIE, normalized)
        return normalized
    }

    fun clearCookie() {
        preferences.remove(KEY_X_COOKIE)
    }

    private fun normalizeCookie(raw: String): String {
        val noPrefix = raw.trim()
            .removePrefix("Cookie:")
            .removePrefix("cookie:")
            .trim()
        return noPrefix
            .trim(';')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        private const val PREF_NAME = "video_downloader_settings"
        private const val KEY_X_COOKIE = "x_cookie"
    }
}
