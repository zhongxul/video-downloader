package com.example.videodownloader.parser

import com.example.videodownloader.data.local.XCookieStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class XCookieValidationResult(
    val valid: Boolean,
    val shouldBlock: Boolean,
    val message: String?,
)

class XCookieValidator(
    private val xCookieStore: XCookieStore,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun validateForParsing(): XCookieValidationResult = withContext(Dispatchers.IO) {
        val cookie = xCookieStore.getCookie().orEmpty().trim()
        if (cookie.isBlank()) {
            return@withContext XCookieValidationResult(
                valid = false,
                shouldBlock = true,
                message = "请先在“X 登录设置”中获取并保存 Cookie",
            )
        }

        val authToken = extractCookieValue(cookie, "auth_token")
        val ct0 = extractCookieValue(cookie, "ct0")
        if (authToken.isNullOrBlank() || ct0.isNullOrBlank()) {
            return@withContext XCookieValidationResult(
                valid = false,
                shouldBlock = true,
                message = "当前 Cookie 缺少 auth_token 或 ct0，请重新获取",
            )
        }

        val request = Request.Builder()
            .url("https://x.com/settings/account")
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36",
            )
            .header("Cookie", cookie)
            .header("x-csrf-token", ct0)
            .header("Referer", "https://x.com/")
            .build()

        return@withContext runCatching {
            client.newCall(request).execute().use { response ->
                val finalUrl = response.request.url.toString().lowercase()
                val body = response.body?.string().orEmpty().lowercase()
                val looksLikeLoginPage = finalUrl.contains("/i/flow/login") ||
                    finalUrl.contains("/login") ||
                    body.contains("log in to x") ||
                    body.contains("sign in to x")
                if (response.code == 401 || response.code == 403 || looksLikeLoginPage) {
                    XCookieValidationResult(
                        valid = false,
                        shouldBlock = true,
                        message = "X Cookie 可能已失效，请在“X 登录设置”重新获取",
                    )
                } else {
                    XCookieValidationResult(
                        valid = true,
                        shouldBlock = false,
                        message = null,
                    )
                }
            }
        }.getOrElse {
            XCookieValidationResult(
                valid = false,
                shouldBlock = false,
                message = "当前网络无法校验 Cookie，已继续尝试解析",
            )
        }
    }

    fun extractCookieValue(cookie: String?, key: String): String? {
        val content = cookie?.trim().orEmpty()
        if (content.isBlank()) return null
        return content.split(";")
            .asSequence()
            .map { it.trim() }
            .mapNotNull { entry ->
                val idx = entry.indexOf("=")
                if (idx <= 0) return@mapNotNull null
                val name = entry.substring(0, idx).trim()
                val value = entry.substring(idx + 1).trim()
                if (name == key && value.isNotBlank()) value else null
            }
            .firstOrNull()
    }
}

