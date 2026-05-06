package com.example.videodownloader.parser

import com.example.videodownloader.core.text.AppText
import com.example.videodownloader.core.text.DefaultAppText
import com.example.videodownloader.domain.model.ParsedVideoInfo
import com.example.videodownloader.parser.douyin.DouyinABogusSigner
import com.example.videodownloader.parser.douyin.DouyinDetailMediaExtractor
import com.example.videodownloader.parser.douyin.DouyinDetailRequestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class DouyinSignedWebParserGateway(
    private val detailEndpoint: String = DETAIL_ENDPOINT,
    private val appText: AppText = DefaultAppText,
    private val cookieProvider: () -> String? = { null },
    private val signer: DouyinABogusSigner = DouyinABogusSigner(),
) : ParserGateway {
    private val client = WebParserHttpClient.default()
    private val requestBuilder = DouyinDetailRequestBuilder()
    private val mediaExtractor = DouyinDetailMediaExtractor(appText)

    override suspend fun parse(url: String): ParsedVideoInfo = withContext(Dispatchers.IO) {
        val awemeId = extractAwemeId(url) ?: resolveAwemeId(url)
            ?: throw IllegalArgumentException(appText.parseNoDownloadableVideo())
        val params = requestBuilder.buildParams(awemeId)
        val query = requestBuilder.buildQuery(params)
        val signedUrl = "${detailEndpoint.trimEnd('/')}?$query&a_bogus=${encode(signer.sign(params))}"
        val request = Request.Builder()
            .url(signedUrl)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.douyin.com/")
            .apply {
                cookieProvider()?.takeIf { it.isNotBlank() }?.let { header("Cookie", it) }
            }
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful || body.isBlank()) {
                throw IllegalArgumentException(appText.parseNoDownloadableVideo())
            }
            mediaExtractor.extract(JSONObject(body))
        }
    }

    private fun resolveAwemeId(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.douyin.com/")
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                extractAwemeId(response.request.url.toString())
                    ?: extractAwemeId(response.body?.string().orEmpty())
            }
        }.getOrNull()
    }

    private fun extractAwemeId(text: String): String? {
        AWEME_PATTERNS.forEach { pattern ->
            pattern.find(text)?.groupValues?.getOrNull(1)?.takeIf { it.length >= 16 }?.let { return it }
        }
        return null
    }

    private fun encode(value: String): String {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }

    private companion object {
        private const val DETAIL_ENDPOINT = "https://www.douyin.com/aweme/v1/web/aweme/detail/"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36"

        private val AWEME_PATTERNS = listOf(
            Regex("""/video/(\d{16,})"""),
            Regex("""/note/(\d{16,})"""),
            Regex("""aweme_id[=:]"?(\d{16,})"""),
            Regex("""item_ids[=:]"?(\d{16,})"""),
            Regex("""modal_id[=:]"?(\d{16,})"""),
        )
    }
}
