package com.example.videodownloader.data.repository

import com.example.videodownloader.domain.model.ParseRecord
import kotlinx.coroutines.flow.Flow

interface ParseRecordRepository {
    suspend fun insertRecord(record: ParseRecord)
    suspend fun updateRecord(record: ParseRecord)
    suspend fun getRecord(recordId: String): ParseRecord?
    suspend fun deleteRecords(recordIds: List<String>)
    fun observeRecords(): Flow<List<ParseRecord>>
}

