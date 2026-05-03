package com.example.videodownloader.parser

import com.example.videodownloader.domain.model.ParsedVideoInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

class WebParserGatewayDouyinTest {
    @Test
    fun extractDouyinVideoUrls_handlesUnicodeEscapedVideoUrl_andFiltersImage() {
        val gateway = WebParserGateway()
        val html = buildDouyinHtml()

        val urls = invokeExtractDouyinVideoUrls(gateway, html)

        assertEquals(1, urls.size)
        assertTrue(urls.first().contains("/aweme/v1/play/"))
        assertFalse(urls.first().contains("douyinpic.com"))
    }

    @Test
    fun parseDouyinFromHtml_returnsOnlyVideoFormats() {
        val gateway = WebParserGateway()
        val html = buildDouyinHtml()

        val parsed = invokeParseDouyinFromHtml(gateway, listOf(html))

        assertNotNull(parsed)
        val info = parsed!!
        assertEquals(1, info.formats.size)
        assertTrue(info.formats.first().downloadUrl.contains("/aweme/v1/play/"))
        assertFalse(info.formats.first().downloadUrl.contains("douyinpic.com"))
    }

    @Test
    fun parseDouyinFromHtml_deduplicatesPlayEndpointsWithSameVideoId() {
        val gateway = WebParserGateway()
        val slash = "\\" + "u002F"
        val html = """
            <html>
            <body>
            <script>
            window._ROUTER_DATA = {
              "video":{
                "play_addr":{
                  "url_list":[
                    "https:${slash}${slash}aweme.snssdk.com${slash}aweme${slash}v1${slash}play${slash}?video_id=v0300abcxyz12345&ratio=720p&line=0",
                    "https:${slash}${slash}www.iesdouyin.com${slash}aweme${slash}v1${slash}play${slash}?line=0&ratio=720p&video_id=v0300abcxyz12345"
                  ]
                }
              }
            };
            </script>
            </body>
            </html>
        """.trimIndent()

        val parsed = invokeParseDouyinFromHtml(gateway, listOf(html))

        assertNotNull(parsed)
        assertEquals(1, parsed!!.formats.size)
    }

    @Test
    fun extractDouyinVideoUrls_filtersPseudoPlayUrlWithHttpVideoId() {
        val gateway = WebParserGateway()
        val html = buildPseudoPlayUrlHtml()

        val urls = invokeExtractDouyinVideoUrls(gateway, html)

        assertTrue(urls.isEmpty())
    }

    @Test
    fun parseDouyinImagesFromHtml_extractsImageUrlsInOrder() {
        val gateway = WebParserGateway()
        val slash = "\\" + "u002F"
        val html = """
            <html>
            <body>
            <script>
            window._ROUTER_DATA = {
              "loaderData":{
                "video_(id)/page":{
                  "videoInfoRes":{
                    "item_list":[
                      {
                        "aweme_type":2,
                        "desc":"图集测试",
                        "images":[
                          {"download_url_list":["https:${slash}${slash}p3-sign.douyinpic.com${slash}foo1.webp?sc=image&biz_tag=aweme_images"]},
                          {"download_url_list":["https:${slash}${slash}p3-sign.douyinpic.com${slash}foo2.gif?sc=image&biz_tag=aweme_images"]}
                        ]
                      }
                    ]
                  }
                }
              }
            };
            </script>
            </body>
            </html>
        """.trimIndent()

        val parsed = invokeParseDouyinImagesFromHtml(gateway, listOf(html))

        assertNotNull(parsed)
        assertEquals(2, parsed!!.formats.size)
        assertTrue(parsed.formats[0].downloadUrl.contains("foo1.webp"))
        assertTrue(parsed.formats[1].downloadUrl.contains("foo2.gif"))
    }

    @Test
    fun parseDouyinItem_returnsImageFormats_whenNoValidVideoUrl() {
        val gateway = WebParserGateway()
        val slash = "\\" + "u002F"
        val raw = """
            {
              "aweme_type":2,
              "desc":"图集接口测试",
              "video":{
                "play_addr":{
                  "url_list":[
                    "https:${slash}${slash}aweme.snssdk.com${slash}aweme${slash}v1${slash}playwm${slash}?video_id=https:${slash}${slash}cdn.example.com${slash}foo.mp3"
                  ]
                }
              },
              "images":[
                {"download_url_list":["https:${slash}${slash}p3-sign.douyinpic.com${slash}api1.webp?sc=image&biz_tag=aweme_images"]},
                {"download_url_list":["https:${slash}${slash}p3-sign.douyinpic.com${slash}api2.gif?sc=image&biz_tag=aweme_images"]}
              ]
            }
        """.trimIndent()

        val parsed = invokeParseDouyinItem(gateway, JSONObject(raw))

        assertNotNull(parsed)
        assertEquals(2, parsed!!.formats.size)
        assertEquals("webp", parsed.formats[0].ext)
        assertEquals("gif", parsed.formats[1].ext)
    }

    @Test
    fun parseDouyinItem_prefersImagesOverImageInfos_andDeduplicatesByImageKey() {
        val gateway = WebParserGateway()
        val slash = "\\" + "u002F"
        val raw = """
            {
              "aweme_type":2,
              "desc":"图集去重测试",
              "images":[
                {"download_url_list":["https:${slash}${slash}p3-sign.douyinpic.com${slash}dup_a.webp?sc=image&biz_tag=aweme_images"]},
                {"download_url_list":["https:${slash}${slash}p3-sign.douyinpic.com${slash}dup_b.webp?sc=image&biz_tag=aweme_images"]}
              ],
              "image_infos":[
                {"download_url_list":["https:${slash}${slash}p6-sign.douyinpic.com${slash}dup_a.webp?sc=image&biz_tag=aweme_images&from=image_infos"]},
                {"download_url_list":["https:${slash}${slash}p6-sign.douyinpic.com${slash}dup_b.webp?sc=image&biz_tag=aweme_images&from=image_infos"]}
              ]
            }
        """.trimIndent()

        val parsed = invokeParseDouyinItem(gateway, JSONObject(raw))

        assertNotNull(parsed)
        assertEquals(2, parsed!!.formats.size)
        assertTrue(parsed.formats[0].downloadUrl.contains("dup_a"))
        assertTrue(parsed.formats[1].downloadUrl.contains("dup_b"))
    }

    @Test
    fun normalizeVideoUrl_keepsSignedDouyinImageQueryEncoded() {
        val gateway = WebParserGateway()
        val rawUrl = "https:\\/\\/p3-sign.douyinpic.com\\/tos-cn-i-0813c000-ce\\/foo.webp?x-signature=abc%2Bdef%2Fghi%3D&sig=Jm9v%2Bbar%3D&sc=image&biz_tag=aweme_images"

        val normalized = invokeNormalizeVideoUrl(gateway, rawUrl)

        assertTrue(normalized.contains("x-signature=abc%2Bdef%2Fghi%3D"))
        assertTrue(normalized.contains("sig=Jm9v%2Bbar%3D"))
        assertFalse(normalized.contains("x-signature=abc+def/ghi="))
    }

    @Test
    fun extractDouyinVideoId_supportsSlidesAndNoteLink() {
        val gateway = WebParserGateway()
        val slidesUrl = "https://www.douyin.com/share/slides/7491111222233334444/"
        val noteUrl = "https://www.douyin.com/note/7491111222233335555"

        val slidesId = invokeExtractDouyinVideoId(gateway, slidesUrl)
        val noteId = invokeExtractDouyinVideoId(gateway, noteUrl)

        assertEquals("7491111222233334444", slidesId)
        assertEquals("7491111222233335555", noteId)
    }

    @Test
    fun parseMediaDetails_returnsPhotoFormats_forXImageTweet() {
        val gateway = WebParserGateway()
        val mediaArray = org.json.JSONArray(
            """
            [
              {
                "type":"photo",
                "media_url_https":"https://pbs.twimg.com/media/abc123?format=jpg&name=orig"
              },
              {
                "type":"photo",
                "media_url_https":"https://pbs.twimg.com/media/xyz456?format=png&name=large"
              }
            ]
            """.trimIndent(),
        )

        val formats = invokeParseMediaDetails(gateway, mediaArray, null)

        assertEquals(2, formats.size)
        assertEquals("jpg", formats[0].ext)
        assertEquals("png", formats[1].ext)
        assertTrue(formats.all { it.downloadable })
    }

    @Test
    fun extractFirstXMediaCoverUrl_returnsFirstPhotoUrl() {
        val gateway = WebParserGateway()
        val mediaArray = org.json.JSONArray(
            """
            [
              {
                "type":"photo",
                "media_url_https":"https://pbs.twimg.com/media/cover1?format=jpg&name=orig"
              },
              {
                "type":"photo",
                "media_url_https":"https://pbs.twimg.com/media/cover2?format=png&name=large"
              }
            ]
            """.trimIndent(),
        )

        val coverUrl = invokeExtractFirstXMediaCoverUrl(gateway, mediaArray)

        assertEquals("https://pbs.twimg.com/media/cover1?format=jpg&name=orig", coverUrl)
    }

    @Test
    fun buildXImageInfoFromMeta_returnsImageFormats() {
        val gateway = WebParserGateway()
        val html = """
            <html>
            <head>
            <meta property="og:title" content="图片帖子"/>
            <meta property="og:description" content="desc"/>
            <meta property="og:image" content="https://pbs.twimg.com/media/first.jpg?name=orig"/>
            <meta property="twitter:image" content="https://pbs.twimg.com/media/second.png?name=large"/>
            </head>
            </html>
        """.trimIndent()

        val parsed = invokeBuildXImageInfoFromMeta(gateway, "https://fxtwitter.com/i/status/123", html)

        assertNotNull(parsed)
        assertEquals(2, parsed!!.formats.size)
        assertEquals("jpg", parsed.formats[0].ext)
        assertEquals("png", parsed.formats[1].ext)
        assertEquals("https://pbs.twimg.com/media/first.jpg?name=orig", parsed.coverUrl)
    }

    @Test
    fun buildXInfoFromInitialState_returnsImageFormats_whenMetaMissing() {
        val gateway = WebParserGateway()
        val html = """
            <html>
            <body>
            <script>
            window.__INITIAL_STATE__={
              "entities":{
                "tweets":{
                  "entities":{
                    "2048322830174167104":{
                      "full_text":"图片帖子",
                      "extended_entities":{
                        "media":[
                          {
                            "type":"photo",
                            "media_url_https":"https://pbs.twimg.com/media/HG0awhibYAAw6Je.jpg?name=orig"
                          },
                          {
                            "type":"photo",
                            "media_url_https":"https://pbs.twimg.com/media/HG0awhibYAAw6Je2.png?name=large"
                          }
                        ]
                      }
                    }
                  }
                }
              }
            };
            </script>
            </body>
            </html>
        """.trimIndent()

        val parsed = invokeBuildXInfoFromInitialState(gateway, "https://x.com/i/status/2048322830174167104", html)

        assertNotNull(parsed)
        assertEquals(2, parsed!!.formats.size)
        assertEquals("jpg", parsed.formats[0].ext)
        assertEquals("png", parsed.formats[1].ext)
        assertEquals("https://pbs.twimg.com/media/HG0awhibYAAw6Je.jpg?name=orig", parsed.coverUrl)
    }

    @Test
    fun buildXInfoFromInitialState_prefersVideoVariants_whenPresent() {
        val gateway = WebParserGateway()
        val html = """
            <html>
            <body>
            <script>
            window.__INITIAL_STATE__={
              "entities":{
                "tweets":{
                  "entities":{
                    "1234567890123456789":{
                      "full_text":"视频帖子",
                      "extended_entities":{
                        "media":[
                          {
                            "type":"video",
                            "media_url_https":"https://pbs.twimg.com/ext_tw_video_thumb/123/pu/img/cover.jpg",
                            "video_info":{
                              "duration_millis":12345,
                              "variants":[
                                {
                                  "bitrate":832000,
                                  "url":"https://video.twimg.com/ext_tw_video/123/pu/vid/720x720/test.mp4"
                                },
                                {
                                  "content_type":"application/x-mpegURL",
                                  "url":"https://video.twimg.com/ext_tw_video/123/pu/pl/test.m3u8"
                                }
                              ]
                            }
                          }
                        ]
                      }
                    }
                  }
                }
              }
            };
            </script>
            </body>
            </html>
        """.trimIndent()

        val parsed = invokeBuildXInfoFromInitialState(gateway, "https://x.com/test/status/1234567890123456789", html)

        assertNotNull(parsed)
        assertEquals(1, parsed!!.formats.size)
        assertEquals("mp4", parsed.formats[0].ext)
        assertTrue(parsed.formats[0].downloadUrl.contains("video.twimg.com/ext_tw_video"))
        assertEquals("https://pbs.twimg.com/ext_tw_video_thumb/123/pu/img/cover.jpg", parsed.coverUrl)
    }

    @Test
    fun parseFxTwitterApiResponse_returnsPhotoFormats() {
        val gateway = WebParserGateway()
        val body = """
            {
              "code":200,
              "message":"OK",
              "tweet":{
                "id":"2048307881314009398",
                "text":"密码都快忘了",
                "media":{
                  "all":[
                    {
                      "type":"photo",
                      "id":"2048307870819856384",
                      "url":"https://pbs.twimg.com/media/HG0NJ_basAAaQUU.jpg?name=orig",
                      "width":1536,
                      "height":2048
                    }
                  ],
                  "photos":[
                    {
                      "type":"photo",
                      "id":"2048307870819856384",
                      "url":"https://pbs.twimg.com/media/HG0NJ_basAAaQUU.jpg?name=orig",
                      "width":1536,
                      "height":2048
                    }
                  ]
                }
              }
            }
        """.trimIndent()

        val parsed = invokeParseFxTwitterApiResponse(gateway, body)

        assertNotNull(parsed)
        assertEquals(1, parsed!!.formats.size)
        assertEquals("jpg", parsed.formats[0].ext)
        assertEquals("https://pbs.twimg.com/media/HG0NJ_basAAaQUU.jpg?name=orig", parsed.formats[0].downloadUrl)
        assertEquals("https://pbs.twimg.com/media/HG0NJ_basAAaQUU.jpg?name=orig", parsed.coverUrl)
    }

    @Test
    fun parseFxTwitterApiResponse_supportsVxTwitterSchema() {
        val gateway = WebParserGateway()
        val body = """
            {
              "tweetID":"2048164849574658385",
              "text":"BULTOS INTERNACIONALES II",
              "hasMedia":true,
              "media_extended":[
                {
                  "type":"image",
                  "url":"https://pbs.twimg.com/media/HGyLEq4bwAA8yC5.jpg",
                  "thumbnail_url":"https://pbs.twimg.com/media/HGyLEq4bwAA8yC5.jpg"
                },
                {
                  "type":"image",
                  "url":"https://pbs.twimg.com/media/HGyLEqeXsAAp8Bc.jpg",
                  "thumbnail_url":"https://pbs.twimg.com/media/HGyLEqeXsAAp8Bc.jpg"
                }
              ]
            }
        """.trimIndent()

        val parsed = invokeParseFxTwitterApiResponse(gateway, body)

        assertNotNull(parsed)
        assertEquals(2, parsed!!.formats.size)
        assertEquals("jpg", parsed.formats[0].ext)
        assertEquals("https://pbs.twimg.com/media/HGyLEq4bwAA8yC5.jpg", parsed.coverUrl)
    }

    private fun buildDouyinHtml(): String {
        val slash = "\\" + "u002F"
        val videoUrl = "https:${slash}${slash}www.iesdouyin.com${slash}aweme${slash}v1${slash}play${slash}?video_id=v0300abcxyz&ratio=1080p&line=0"
        val imageUrl = "https:${slash}${slash}p3-sign.douyinpic.com${slash}aweme${slash}1080x1080${slash}foo.jpg?sc=image&biz_tag=aweme_images"
        return """
            <html>
            <head><title>douyin video test</title></head>
            <body>
            <script>
            window._ROUTER_DATA = {
              "video":{"play_addr":{"url_list":["$videoUrl"]}},
              "cover":"$imageUrl"
            };
            </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildPseudoPlayUrlHtml(): String {
        val slash = "\\" + "u002F"
        val pseudoVideoUrl = "https:${slash}${slash}aweme.snssdk.com${slash}aweme${slash}v1${slash}playwm${slash}?video_id=https:${slash}${slash}sf5-hl-ali-cdn-tos.douyinstatic.com${slash}obj${slash}ies-music${slash}7419693556019202827.mp3&ratio=720p&line=0"
        return """
            <html>
            <body>
            <script>
            window._ROUTER_DATA = {
              "loaderData":{
                "video_(id)/page":{
                  "videoInfoRes":{
                    "item_list":[
                      {
                        "aweme_type":2,
                        "video":{"play_addr":{"url_list":["$pseudoVideoUrl"]}},
                        "images":[{"url_list":["https:${slash}${slash}p3-sign.douyinpic.com${slash}foo.jpg?sc=image&biz_tag=aweme_images"]}]
                      }
                    ]
                  }
                }
              }
            };
            </script>
            </body>
            </html>
        """.trimIndent()
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeExtractDouyinVideoUrls(gateway: WebParserGateway, html: String): List<String> {
        val method = WebParserGateway::class.java.getDeclaredMethod(
            "extractDouyinVideoUrls",
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(gateway, html) as List<String>
    }

    private fun invokeParseDouyinFromHtml(
        gateway: WebParserGateway,
        htmlCandidates: Collection<String>,
    ): ParsedVideoInfo? {
        val method = WebParserGateway::class.java.getDeclaredMethod(
            "parseDouyinFromHtml",
            Collection::class.java,
        )
        method.isAccessible = true
        return method.invoke(gateway, htmlCandidates) as ParsedVideoInfo?
    }

    private fun invokeParseDouyinImagesFromHtml(
        gateway: WebParserGateway,
        htmlCandidates: Collection<String>,
    ): ParsedVideoInfo? {
        val method = WebParserGateway::class.java.getDeclaredMethod(
            "parseDouyinImagesFromHtml",
            Collection::class.java,
        )
        method.isAccessible = true
        return method.invoke(gateway, htmlCandidates) as ParsedVideoInfo?
    }

    private fun invokeParseDouyinItem(
        gateway: WebParserGateway,
        item: JSONObject,
    ): ParsedVideoInfo? {
        val method = WebParserGateway::class.java.getDeclaredMethod(
            "parseDouyinItem",
            JSONObject::class.java,
        )
        method.isAccessible = true
        return method.invoke(gateway, item) as ParsedVideoInfo?
    }

    private fun invokeExtractDouyinVideoId(
        gateway: WebParserGateway,
        text: String,
    ): String? {
        val method = WebParserGateway::class.java.getDeclaredMethod(
            "extractDouyinVideoId",
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(gateway, text) as String?
    }

    @Suppress("UNCHECKED_CAST")
    private fun invokeParseMediaDetails(
        gateway: WebParserGateway,
        array: org.json.JSONArray,
        durationSec: Double?,
    ): List<com.example.videodownloader.domain.model.VideoFormat> {
        val method = WebParserGateway::class.java.getDeclaredMethod(
            "parseMediaDetails",
            org.json.JSONArray::class.java,
            java.lang.Double::class.java,
        )
        method.isAccessible = true
        return method.invoke(gateway, array, durationSec) as List<com.example.videodownloader.domain.model.VideoFormat>
    }

    private fun invokeExtractFirstXMediaCoverUrl(
        gateway: WebParserGateway,
        array: org.json.JSONArray,
    ): String? {
        val method = WebParserGateway::class.java.getDeclaredMethod(
            "extractFirstXMediaCoverUrl",
            org.json.JSONArray::class.java,
        )
        method.isAccessible = true
        return method.invoke(gateway, array) as String?
    }

    private fun invokeBuildXImageInfoFromMeta(
        gateway: WebParserGateway,
        url: String,
        html: String,
    ): ParsedVideoInfo? {
        val method = WebParserGateway::class.java.getDeclaredMethod(
            "buildXImageInfoFromMeta",
            String::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(gateway, url, html) as ParsedVideoInfo?
    }

    private fun invokeBuildXInfoFromInitialState(
        gateway: WebParserGateway,
        url: String,
        html: String,
    ): ParsedVideoInfo? {
        val method = WebParserGateway::class.java.getDeclaredMethod(
            "buildXInfoFromInitialState",
            String::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(gateway, url, html) as ParsedVideoInfo?
    }

    private fun invokeParseFxTwitterApiResponse(
        gateway: WebParserGateway,
        body: String,
    ): ParsedVideoInfo? {
        val method = WebParserGateway::class.java.getDeclaredMethod(
            "parseFxTwitterApiResponse",
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(gateway, body) as ParsedVideoInfo?
    }

    private fun invokeNormalizeVideoUrl(
        gateway: WebParserGateway,
        raw: String,
    ): String {
        val method = WebParserGateway::class.java.getDeclaredMethod(
            "normalizeVideoUrl",
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(gateway, raw) as String
    }
}
