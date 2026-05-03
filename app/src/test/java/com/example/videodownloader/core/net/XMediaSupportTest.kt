package com.example.videodownloader.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XMediaSupportTest {
    @Test
    fun isXMediaUrl_recognizesXMediaHosts() {
        assertTrue(isXMediaUrl("https://x.com/user/status/1"))
        assertTrue(isXMediaUrl("https://twitter.com/user/status/1"))
        assertTrue(isXMediaUrl("https://pbs.twimg.com/media/abc?format=jpg&name=orig"))
        assertTrue(isXMediaUrl("https://video.twimg.com/ext_tw_video/123/pu/vid/720x720/foo.mp4"))
        assertFalse(isXMediaUrl("https://www.douyin.com/video/123"))
    }

    @Test
    fun buildXMediaHeaderMap_addsRefererAndOptionalCookie() {
        val headers = buildXMediaHeaderMap(
            url = "https://pbs.twimg.com/media/abc?format=jpg&name=orig",
            cookie = "auth_token=1; ct0=2",
        )

        assertEquals("https://x.com/", headers["Referer"])
        assertEquals("auth_token=1; ct0=2", headers["Cookie"])
    }
}
