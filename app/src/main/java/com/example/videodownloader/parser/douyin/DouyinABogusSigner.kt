package com.example.videodownloader.parser.douyin

import kotlin.random.Random

class DouyinABogusSigner(
    private val requestBuilder: DouyinDetailRequestBuilder = DouyinDetailRequestBuilder(),
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
    private val randomInt: (bound: Int, index: Int) -> Int = { bound, _ -> Random.nextInt(bound) },
    private val durationMillis: () -> Int = { Random.nextInt(4, 9) },
) {
    fun sign(params: Map<String, String>): String {
        val start = clockMillis()
        val end = start + durationMillis()
        val query = requestBuilder.buildQuery(params)
        val prefix = generateString1(
            randomInt(10_000, 0),
            randomInt(10_000, 1),
            randomInt(10_000, 2),
        )
        val encrypted = generateString2(query, start, end)
        return base64Like(prefix + encrypted)
    }

    private fun generateString1(first: Int, second: Int, third: Int): String {
        return chars(list1(first) + list2(second) + list3(third))
    }

    private fun list1(value: Int): List<Int> = randomList(value, 170, 85, 1, 2, 5, 45 and 170)

    private fun list2(value: Int): List<Int> = randomList(value, 170, 85, 1, 0, 0, 0)

    private fun list3(value: Int): List<Int> = randomList(value, 170, 85, 1, 0, 5, 0)

    private fun randomList(value: Int, b: Int, c: Int, d: Int, e: Int, f: Int, g: Int): List<Int> {
        val low = value and 255
        val high = value shr 8
        return listOf(
            (low and b) or d,
            (low and c) or e,
            (high and b) or f,
            (high and c) or g,
        )
    }

    private fun generateString2(query: String, start: Long, end: Long): String {
        val body = generateString2List(query, start, end).toMutableList()
        val check = body.fold(0) { acc, item -> acc xor item }
        body += browserCode
        body += check
        return rc4(chars(body), "y")
    }

    private fun generateString2List(query: String, start: Long, end: Long): List<Int> {
        val params = sm3(sm3((query + END_STRING).encodeToByteArray()))
        val method = sm3(sm3(("GET$END_STRING").encodeToByteArray()))
        return list4(
            ((end shr 24) and 255).toInt(),
            params[21],
            uaCode[23],
            ((end shr 16) and 255).toInt(),
            params[22],
            uaCode[24],
            ((end shr 8) and 255).toInt(),
            (end and 255).toInt(),
            ((start shr 24) and 255).toInt(),
            ((start shr 16) and 255).toInt(),
            ((start shr 8) and 255).toInt(),
            (start and 255).toInt(),
            method[21],
            method[22],
            (end / 256 / 256 / 256 / 256).toInt(),
            (start / 256 / 256 / 256 / 256).toInt(),
            BROWSER.length,
        )
    }

    private fun list4(
        a: Int,
        b: Int,
        c: Int,
        d: Int,
        e: Int,
        f: Int,
        g: Int,
        h: Int,
        i: Int,
        j: Int,
        k: Int,
        m: Int,
        n: Int,
        o: Int,
        p: Int,
        q: Int,
        r: Int,
    ): List<Int> {
        return listOf(
            44, a, 0, 0, 0, 0, 24, b, n, 0, c, d, 0, 0, 0, 1, 0, 239, e, o, f, g,
            0, 0, 0, 0, h, 0, 0, 14, i, j, 0, k, m, 3, p, 1, q, 1, r, 0, 0, 0,
        )
    }

    private fun sm3(data: ByteArray): IntArray = Sm3.digest(data)

    private fun sm3(data: IntArray): IntArray = Sm3.digest(ByteArray(data.size) { data[it].toByte() })

    private fun rc4(text: String, key: String): String {
        val state = MutableList(256) { it }
        var j = 0
        for (i in 0 until 256) {
            j = (j + state[i] + key[i % key.length].code) % 256
            val tmp = state[i]
            state[i] = state[j]
            state[j] = tmp
        }

        var i = 0
        j = 0
        val output = StringBuilder(text.length)
        for (char in text) {
            i = (i + 1) % 256
            j = (j + state[i]) % 256
            val tmp = state[i]
            state[i] = state[j]
            state[j] = tmp
            val t = (state[i] + state[j]) % 256
            output.append((state[t] xor char.code).toChar())
        }
        return output.toString()
    }

    private fun base64Like(value: String): String {
        val output = StringBuilder()
        var index = 0
        while (index < value.length) {
            val first = value[index].code
            val second = value.getOrNull(index + 1)?.code
            val third = value.getOrNull(index + 2)?.code
            val n = (first shl 16) or ((second ?: 0) shl 8) or (third ?: 0)
            output.append(ALPHABET[(n and 0xfc0000) shr 18])
            output.append(ALPHABET[(n and 0x03f000) shr 12])
            if (second != null) output.append(ALPHABET[(n and 0x0fc0) shr 6])
            if (third != null) output.append(ALPHABET[n and 0x3f])
            index += 3
        }
        repeat((4 - output.length % 4) % 4) {
            output.append('=')
        }
        return output.toString()
    }

    private fun chars(values: List<Int>): String = values.joinToString("") { (it and 255).toChar().toString() }

    private companion object {
        private const val END_STRING = "cus"
        private const val BROWSER = "1536|742|1536|864|0|0|0|0|1536|864|1536|864|1536|742|24|24|MacIntel"
        private const val ALPHABET = "Dkdpgh2ZmsQB80/MfvV36XI1R45-WUAlEixNLwoqYTOPuzKFjJnry79HbGcaStCe"

        private val browserCode = BROWSER.map { it.code }
        private val uaCode = intArrayOf(
            76, 98, 15, 131, 97, 245, 224, 133, 122, 199, 241, 166, 79, 34, 90, 191,
            128, 126, 122, 98, 66, 11, 14, 40, 49, 110, 110, 173, 67, 96, 138, 252,
        )
    }
}
