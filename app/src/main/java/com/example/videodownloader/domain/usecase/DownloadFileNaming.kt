package com.example.videodownloader.domain.usecase

internal fun buildOutputFileName(
    title: String,
    ext: String,
    formatId: String? = null,
    resolution: String? = null,
    isImage: Boolean = false,
    totalImageCount: Int? = null,
): String {
    val normalizedExt = normalizeOutputExt(ext)
    val safeTitle = sanitizeDownloadTitle(
        title = title,
        fallback = if (isImage) "image" else "video",
    )
    val baseName = if (isImage) {
        buildImageBaseName(
            safeTitle = safeTitle,
            formatId = formatId,
            resolution = resolution,
            totalImageCount = totalImageCount,
        )
    } else {
        safeTitle
    }
    return "$baseName.$normalizedExt"
}

internal fun isImageExt(ext: String): Boolean {
    return when (normalizeOutputExt(ext)) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> true
        else -> false
    }
}

internal fun normalizeOutputExt(ext: String): String {
    val value = ext.trim().lowercase().trimStart('.')
    return when (value) {
        "mp4", "m3u8", "webm", "mov", "mkv", "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> value
        else -> "mp4"
    }
}

private fun buildImageBaseName(
    safeTitle: String,
    formatId: String?,
    resolution: String?,
    totalImageCount: Int?,
): String {
    val shouldAppendIndex = (totalImageCount ?: 0) > 1
    if (!shouldAppendIndex) return safeTitle

    val sequence = resolveImageSequenceNumber(formatId, resolution) ?: 1
    return "$safeTitle$sequence"
}

private fun resolveImageSequenceNumber(
    formatId: String?,
    resolution: String?,
): Int? {
    val formatIndex = Regex("""(?:^|_)(\d+)$""")
        .find(formatId.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.let { it + 1 }
    if (formatIndex != null && formatIndex > 0) {
        return formatIndex
    }

    return Regex("""(\d+)""")
        .find(resolution.orEmpty())
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.takeIf { it > 0 }
}

private fun sanitizeDownloadTitle(
    title: String,
    fallback: String,
): String {
    return title
        .replace(Regex("[\\\\/:*?\"<>|#]"), "_")
        .trim()
        .trimEnd('.')
        .ifBlank { fallback }
        .take(80)
}
