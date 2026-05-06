package com.example.videodownloader.parser.douyin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinDetailMediaExtractorTest {
    @Test
    fun extract_returnsImagesAndVideos_andFiltersAudioAndLogoCandidates() {
        val json = JSONObject(
            """
            {
              "aweme_detail": {
                "desc": "图文视频测试",
                "images": [
                  {
                    "url_list": [
                      "https://p3-sign.douyinpic.com/foo.webp?sc=image&biz_tag=aweme_images"
                    ],
                    "video": {
                      "play_addr": {
                        "url_list": [
                          "https://v26-web.douyinvod.com/foo/?mime_type=video_mp4&feature_id=clean",
                          "https://v11-weba.douyinvod.com/foo/?mime_type=video_mp4&feature_id=clean",
                          "https://v26-web.douyinvod.com/foo/mp4/main.mp4?mime_type=video_mp4&logo_type=aweme_search_suffix"
                        ]
                      }
                    }
                  }
                ],
                "music": {
                  "play_url": {
                    "url_list": [
                      "https://sf5-hl-ali-cdn-tos.douyinstatic.com/obj/ies-music/foo.mp3"
                    ]
                  }
                }
              }
            }
            """.trimIndent(),
        )

        val parsed = DouyinDetailMediaExtractor().extract(json)

        assertEquals("图文视频测试", parsed.title)
        assertEquals(1, parsed.formats.size)
        assertEquals("mp4", parsed.formats[0].ext)
        assertTrue(parsed.formats[0].downloadUrl.contains("douyinvod.com"))
        assertFalse(parsed.formats.any { it.downloadUrl.contains("ies-music") })
        assertFalse(parsed.formats.any { it.downloadUrl.contains("logo_type") })
    }

    @Test
    fun extract_ignoresAuthorAndShareImages_andUsesWorkImageAsCover() {
        val json = JSONObject(
            """
            {
              "aweme_detail": {
                "desc": "动图测试",
                "author": {
                  "avatar_thumb": {
                    "url_list": [
                      "https://p3-sign.douyinpic.com/avatar.webp"
                    ]
                  }
                },
                "share_info": {
                  "share_image_url": "https://p3-sign.douyinpic.com/share.webp"
                },
                "images": [
                  {
                    "url_list": [
                      "https://p3-sign.douyinpic.com/work.webp?sc=image&biz_tag=aweme_images"
                    ],
                    "video": {
                      "play_addr": {
                        "url_list": [
                          "https://v26-web.douyinvod.com/live-photo/?mime_type=video_mp4&feature_id=clean"
                        ]
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val parsed = DouyinDetailMediaExtractor().extract(json)

        assertEquals("https://p3-sign.douyinpic.com/work.webp?sc=image&biz_tag=aweme_images", parsed.coverUrl)
        assertEquals(1, parsed.formats.size)
        assertEquals("mp4", parsed.formats.single().ext)
        assertTrue(parsed.formats.any { it.downloadUrl.contains("live-photo") })
        assertEquals("https://p3-sign.douyinpic.com/work.webp?sc=image&biz_tag=aweme_images", parsed.formats.single().thumbnailUrl)
        assertFalse(parsed.formats.any { it.downloadUrl.contains("avatar") })
        assertFalse(parsed.formats.any { it.downloadUrl.contains("share") })
    }

    @Test
    fun extract_returnsOneVideoPerAnimatedImage_andDoesNotExposeStillVariants() {
        val json = JSONObject(
            """
            {
              "aweme_detail": {
                "desc": "四张动图",
                "images": [
                  {
                    "url_list": [
                      "https://p3-sign.douyinpic.com/cover-a.webp?sc=image",
                      "https://p3-sign.douyinpic.com/cover-a.jpeg?sc=image"
                    ],
                    "download_url_list": [
                      "https://p3-sign.douyinpic.com/cover-a-origin.jpeg?sc=image"
                    ],
                    "video": {
                      "play_addr": {
                        "url_list": [
                          "https://v26-web.douyinvod.com/a/?mime_type=video_mp4&feature_id=clean",
                          "https://v11-weba.douyinvod.com/a/?mime_type=video_mp4&feature_id=clean"
                        ]
                      }
                    }
                  },
                  {
                    "url_list": [
                      "https://p3-sign.douyinpic.com/cover-b.webp?sc=image",
                      "https://p3-sign.douyinpic.com/cover-b.jpeg?sc=image"
                    ],
                    "video": {
                      "play_addr": {
                        "url_list": [
                          "https://v26-web.douyinvod.com/b/?mime_type=video_mp4&feature_id=clean",
                          "https://v11-weba.douyinvod.com/b/?mime_type=video_mp4&feature_id=clean"
                        ]
                      }
                    }
                  },
                  {
                    "url_list": ["https://p3-sign.douyinpic.com/cover-c.webp?sc=image"],
                    "video": {
                      "play_addr": {
                        "url_list": ["https://v26-web.douyinvod.com/c/?mime_type=video_mp4&feature_id=clean"]
                      }
                    }
                  },
                  {
                    "url_list": ["https://p3-sign.douyinpic.com/cover-d.webp?sc=image"],
                    "video": {
                      "play_addr": {
                        "url_list": ["https://v26-web.douyinvod.com/d/?mime_type=video_mp4&feature_id=clean"]
                      }
                    }
                  }
                ]
              }
            }
            """.trimIndent(),
        )

        val parsed = DouyinDetailMediaExtractor().extract(json)

        assertEquals("https://p3-sign.douyinpic.com/cover-a.webp?sc=image", parsed.coverUrl)
        assertEquals(4, parsed.formats.size)
        assertTrue(parsed.formats.all { it.ext == "mp4" })
        assertEquals("https://v11-weba.douyinvod.com/a/?mime_type=video_mp4&feature_id=clean", parsed.formats[0].downloadUrl)
        assertEquals("https://v11-weba.douyinvod.com/b/?mime_type=video_mp4&feature_id=clean", parsed.formats[1].downloadUrl)
        assertEquals("https://p3-sign.douyinpic.com/cover-a-origin.jpeg?sc=image", parsed.formats[0].thumbnailUrl)
        assertEquals("https://p3-sign.douyinpic.com/cover-b.jpeg?sc=image", parsed.formats[1].thumbnailUrl)
        assertEquals("https://p3-sign.douyinpic.com/cover-a-origin.jpeg?sc=image", parsed.formats[0].pairedImageUrl)
        assertEquals("https://p3-sign.douyinpic.com/cover-b.jpeg?sc=image", parsed.formats[1].pairedImageUrl)
        assertFalse(parsed.formats.any { it.downloadUrl.contains("douyinpic.com") })
    }
}
