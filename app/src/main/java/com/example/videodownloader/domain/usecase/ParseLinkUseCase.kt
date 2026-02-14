package com.example.videodownloader.domain.usecase

import android.util.Log
import android.util.Patterns
import com.example.videodownloader.domain.model.ParsedVideoInfo
import com.example.videodownloader.parser.ParserGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ParseLinkUseCase(
    private val parserGateway: ParserGateway,
) {
    private val tag = "ParseLinkUseCase"

    suspend operator fun invoke(rawInput: String): ParsedVideoInfo = withContext(Dispatchers.IO) {
        val resolvedUrl = resolveUrl(rawInput)
        Log.d(tag, "resolved url: $resolvedUrl")
        parserGateway.parse(resolvedUrl)
    }

    fun resolveUrl(rawInput: String): String {
        val text = rawInput.trim()
        require(text.isNotBlank()) { "请输入链接或包含链接的文案" }

        val extracted = extractUrl(text) ?: text
        val cleaned = cleanCandidate(extracted)
        require(Patterns.WEB_URL.matcher(cleaned).matches()) { "未识别到有效链接，请检查后重试" }
        return cleaned
    }

    fun extractUrl(rawInput: String): String? {
        val candidates = URL_REGEX.findAll(rawInput)
            .map { normalizeCandidate(it.value) }
            .toList()
        if (candidates.isEmpty()) return null

        val prioritized = candidates.firstOrNull {
            val value = it.lowercase()
            value.contains("douyin.com") ||
                value.contains("iesdouyin.com") ||
                value.contains("x.com") ||
                value.contains("twitter.com")
        }
        return prioritized ?: candidates.first()
    }

    private fun cleanCandidate(value: String): String {
        return value.trim()
            .trimStart('(', '[', '{', '<', '"', '\'')
            .trimEnd('.', ',', '!', '?', ';', ':', ')', ']', '}', '>', '"', '\'')
    }

    private fun normalizeCandidate(value: String): String {
        val clean = cleanCandidate(value)
        return if (clean.startsWith("http://", ignoreCase = true) || clean.startsWith("https://", ignoreCase = true)) {
            clean
        } else {
            "https://$clean"
        }
    }

    companion object {
        private val URL_REGEX = Regex(
            """(?:https?://|(?:www\.)?(?:v\.douyin\.com|douyin\.com|iesdouyin\.com|x\.com|twitter\.com)/)[^\s]+""",
            RegexOption.IGNORE_CASE,
        )
    }
}
