package com.example.videodownloader.parser.douyin

import org.junit.Assert.assertEquals
import org.junit.Test

class DouyinABogusSignerTest {
    @Test
    fun sign_returnsReferenceABogusForStableInputs() {
        val params = DouyinDetailRequestBuilder().buildParams("7635595401111291002")
        val signer = DouyinABogusSigner(
            clockMillis = { 1_700_000_000_000L },
            randomInt = { _, index -> listOf(1234, 5678, 9012)[index] },
            durationMillis = { 6 },
        )

        val signature = signer.sign(params)

        assertEquals(
            "E7mhBdugDifihdWk56KLfY3q6vDVYmQI0SVkMD2f5-DOqL39HMY29exoIBGvXY8jwG/-IeEjy4hbT3ohrQ2y0Hwf9W0L/25ksDSkKl5Q5xSSs1X9eghgJ04qmkt5SMx2RvB-rOXmqhZHKRbp09oHmhK4b1dzFgf3qJLzUj==",
            signature,
        )
    }
}
