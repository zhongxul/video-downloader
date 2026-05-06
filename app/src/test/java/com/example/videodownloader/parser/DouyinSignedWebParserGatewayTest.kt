package com.example.videodownloader.parser

import com.example.videodownloader.parser.douyin.DouyinABogusSigner
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinSignedWebParserGatewayTest {
    @Test
    fun parse_requestsSignedDetailEndpoint_andExtractsVideoFromJson() = runBlocking {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "aweme_detail": {
                        "desc": "实况图测试",
                        "images": [
                          {
                            "url_list": [
                              "https://p3-sign.douyinpic.com/foo.webp?sc=image&biz_tag=aweme_images"
                            ],
                            "video": {
                              "play_addr": {
                                "url_list": [
                                  "https://v26-web.douyinvod.com/foo/?mime_type=video_mp4&feature_id=clean"
                                ]
                              }
                            }
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
        )
        server.start()
        try {
            val gateway = DouyinSignedWebParserGateway(
                detailEndpoint = server.url("/aweme/v1/web/aweme/detail/").toString(),
                signer = DouyinABogusSigner(
                    clockMillis = { 1_700_000_000_000L },
                    randomInt = { _, index -> listOf(1234, 5678, 9012)[index] },
                    durationMillis = { 6 },
                ),
            )

            val parsed = gateway.parse("https://www.douyin.com/video/7635595401111291002")

            assertEquals("实况图测试", parsed.title)
            assertTrue(parsed.formats.any { it.ext == "mp4" && it.downloadUrl.contains("douyinvod.com") })

            val request = server.takeRequest()
            assertEquals("GET", request.method)
            assertNotNull(request.requestUrl?.queryParameter("a_bogus"))
            assertEquals("7635595401111291002", request.requestUrl?.queryParameter("aweme_id"))
            assertEquals("https://www.douyin.com/", request.getHeader("Referer"))
        } finally {
            server.shutdown()
        }
    }
}
