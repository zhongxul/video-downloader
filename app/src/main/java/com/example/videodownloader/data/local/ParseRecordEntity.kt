package com.example.videodownloader.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.videodownloader.domain.model.ParseRecord
import com.example.videodownloader.domain.model.ParseRecordStatus

@Entity(tableName = "parse_records")
data class ParseRecordEntity(
    @PrimaryKey val id: String,
    val rawInput: String,
    val resolvedUrl: String?,
    val title: String?,
    val coverUrl: String?,
    val status: String,
    val message: String?,
    val selectedFormatLabel: String?,
    val selectedExt: String?,
    val taskId: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

fun ParseRecordEntity.toDomain(): ParseRecord {
    return ParseRecord(
        id = id,
        rawInput = rawInput,
        resolvedUrl = resolvedUrl,
        title = title,
        coverUrl = coverUrl,
        status = ParseRecordStatus.valueOf(status),
        message = message,
        selectedFormatLabel = selectedFormatLabel,
        selectedExt = selectedExt,
        taskId = taskId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

fun ParseRecord.toEntity(): ParseRecordEntity {
    return ParseRecordEntity(
        id = id,
        rawInput = rawInput,
        resolvedUrl = resolvedUrl,
        title = title,
        coverUrl = coverUrl,
        status = status.name,
        message = message,
        selectedFormatLabel = selectedFormatLabel,
        selectedExt = selectedExt,
        taskId = taskId,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}

