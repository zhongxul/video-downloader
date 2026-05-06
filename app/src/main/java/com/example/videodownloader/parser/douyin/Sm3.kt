package com.example.videodownloader.parser.douyin

internal object Sm3 {
    private val initialVector = intArrayOf(
        0x7380166f,
        0x4914b2b9,
        0x172442d7,
        0xda8a0600.toInt(),
        0xa96f30bc.toInt(),
        0x163138aa,
        0xe38dee4d.toInt(),
        0xb0fb0e4e.toInt(),
    )

    fun digest(data: ByteArray): IntArray {
        val padded = pad(data)
        var state = initialVector.copyOf()
        for (offset in padded.indices step 64) {
            state = compress(state, padded.copyOfRange(offset, offset + 64))
        }
        return state.flatMap { word ->
            listOf(
                (word ushr 24) and 0xff,
                (word ushr 16) and 0xff,
                (word ushr 8) and 0xff,
                word and 0xff,
            )
        }.toIntArray()
    }

    private fun pad(data: ByteArray): ByteArray {
        val bitLength = data.size.toLong() * 8L
        var paddedLength = data.size + 1 + 8
        while (paddedLength % 64 != 0) paddedLength++

        val output = ByteArray(paddedLength)
        data.copyInto(output)
        output[data.size] = 0x80.toByte()
        for (index in 0 until 8) {
            output[paddedLength - 1 - index] = ((bitLength ushr (8 * index)) and 0xff).toByte()
        }
        return output
    }

    private fun compress(vector: IntArray, block: ByteArray): IntArray {
        val w = IntArray(68)
        val wPrime = IntArray(64)
        for (i in 0 until 16) {
            val base = i * 4
            w[i] = ((block[base].toInt() and 0xff) shl 24) or
                ((block[base + 1].toInt() and 0xff) shl 16) or
                ((block[base + 2].toInt() and 0xff) shl 8) or
                (block[base + 3].toInt() and 0xff)
        }
        for (i in 16 until 68) {
            w[i] = p1(w[i - 16] xor w[i - 9] xor w[i - 3].rotateLeft(15)) xor
                w[i - 13].rotateLeft(7) xor
                w[i - 6]
        }
        for (i in 0 until 64) {
            wPrime[i] = w[i] xor w[i + 4]
        }

        var a = vector[0]
        var b = vector[1]
        var c = vector[2]
        var d = vector[3]
        var e = vector[4]
        var f = vector[5]
        var g = vector[6]
        var h = vector[7]

        for (j in 0 until 64) {
            val ss1 = (a.rotateLeft(12) + e + tj(j).rotateLeft(j)).rotateLeft(7)
            val ss2 = ss1 xor a.rotateLeft(12)
            val tt1 = ff(j, a, b, c) + d + ss2 + wPrime[j]
            val tt2 = gg(j, e, f, g) + h + ss1 + w[j]
            d = c
            c = b.rotateLeft(9)
            b = a
            a = tt1
            h = g
            g = f.rotateLeft(19)
            f = e
            e = p0(tt2)
        }

        return intArrayOf(
            vector[0] xor a,
            vector[1] xor b,
            vector[2] xor c,
            vector[3] xor d,
            vector[4] xor e,
            vector[5] xor f,
            vector[6] xor g,
            vector[7] xor h,
        )
    }

    private fun p0(x: Int): Int = x xor x.rotateLeft(9) xor x.rotateLeft(17)

    private fun p1(x: Int): Int = x xor x.rotateLeft(15) xor x.rotateLeft(23)

    private fun ff(j: Int, x: Int, y: Int, z: Int): Int {
        return if (j < 16) x xor y xor z else (x and y) or (x and z) or (y and z)
    }

    private fun gg(j: Int, x: Int, y: Int, z: Int): Int {
        return if (j < 16) x xor y xor z else (x and y) or (x.inv() and z)
    }

    private fun tj(j: Int): Int = if (j < 16) 0x79cc4519 else 0x7a879d8a
}
