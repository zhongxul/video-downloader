package com.example.videodownloader.core.io

internal fun resolveFilePathFromUriString(localUri: String?): String? {
    if (localUri.isNullOrBlank()) return null
    if (!localUri.startsWith("file://", ignoreCase = true)) return null

    val rawPath = localUri.removePrefix("file://")
    if (rawPath.isBlank()) return null

    // 不能用 Uri.parse(...).path 回推 file:// 路径，否则文件名里的 # 会被当成 fragment 截断。
    return decodePercentEncodedPath(rawPath)
}

private fun decodePercentEncodedPath(value: String): String {
    val output = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val current = value[index]
        if (current == '%' && index + 2 < value.length) {
            val hex = value.substring(index + 1, index + 3)
            val byteValue = hex.toIntOrNull(16)
            if (byteValue != null) {
                val bytes = mutableListOf<Byte>()
                bytes += byteValue.toByte()
                index += 3
                while (index + 2 < value.length && value[index] == '%') {
                    val nextHex = value.substring(index + 1, index + 3)
                    val nextByte = nextHex.toIntOrNull(16) ?: break
                    bytes += nextByte.toByte()
                    index += 3
                }
                output.append(bytes.toByteArray().toString(Charsets.UTF_8))
                continue
            }
        }
        output.append(current)
        index += 1
    }
    return output.toString()
}
