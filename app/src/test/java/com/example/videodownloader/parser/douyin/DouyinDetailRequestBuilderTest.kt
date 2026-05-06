package com.example.videodownloader.parser.douyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DouyinDetailRequestBuilderTest {
    @Test
    fun buildParams_includesRequiredPostDetailFieldsInStableOrder() {
        val params = DouyinDetailRequestBuilder().buildParams("7635595401111291002")

        assertEquals("webapp", params["device_platform"])
        assertEquals("6383", params["aid"])
        assertEquals("channel_pc_web", params["channel"])
        assertEquals("290100", params["version_code"])
        assertEquals("Chrome", params["browser_name"])
        assertEquals("7635595401111291002", params["aweme_id"])
        assertEquals("", params["msToken"])

        val keys = params.keys.toList()
        assertTrue(keys.indexOf("device_platform") < keys.indexOf("aweme_id"))
        assertTrue(keys.indexOf("aweme_id") < keys.indexOf("msToken"))
    }

    @Test
    fun buildQuery_encodesParamsWithoutABogus() {
        val query = DouyinDetailRequestBuilder().buildQuery("7635595401111291002")

        assertTrue(query.contains("device_platform=webapp"))
        assertTrue(query.contains("aweme_id=7635595401111291002"))
        assertTrue(query.contains("msToken="))
    }
}
