package com.example.videodownloader.domain.usecase

import com.example.videodownloader.domain.model.VideoFormat

data class PlannedDownloadFormat(
    val title: String,
    val coverUrl: String?,
    val format: VideoFormat,
)

fun planDownloadFormatsForSaving(
    title: String,
    formats: List<VideoFormat>,
): List<PlannedDownloadFormat> {
    return formats.map { format ->
        val pairedImageUrl = format.pairedImageUrl?.takeIf { it.isNotBlank() }
        if (pairedImageUrl == null) {
            PlannedDownloadFormat(
                title = title,
                coverUrl = format.thumbnailUrl,
                format = format,
            )
        } else {
            val pairIndex = resolveFormatSequence(format)
            val pairTitle = "$title$pairIndex"
            val dynamicFormat = format.copy(
                pairedImageUrl = null,
                thumbnailUrl = pairedImageUrl,
            )
            PlannedDownloadFormat(
                title = "${pairTitle}_动态",
                coverUrl = pairedImageUrl,
                format = dynamicFormat,
            )
        }
    }
}

private fun resolveFormatSequence(format: VideoFormat): Int {
    return Regex("""(?:^|_)(\d+)$""")
        .find(format.formatId)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?.let { it + 1 }
        ?: Regex("""(\d+)""")
            .find(format.resolution)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
        ?: 1
}
