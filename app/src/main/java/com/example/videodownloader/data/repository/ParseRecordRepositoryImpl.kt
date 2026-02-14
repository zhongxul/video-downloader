package com.example.videodownloader.data.repository

import com.example.videodownloader.data.local.ParseRecordDao
import com.example.videodownloader.data.local.toDomain
import com.example.videodownloader.data.local.toEntity
import com.example.videodownloader.domain.model.ParseRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ParseRecordRepositoryImpl(
    private val dao: ParseRecordDao,
) : ParseRecordRepository {
    override suspend fun insertRecord(record: ParseRecord) {
        dao.insert(record.toEntity())
    }

    override suspend fun updateRecord(record: ParseRecord) {
        dao.update(record.toEntity())
    }

    override suspend fun getRecord(recordId: String): ParseRecord? {
        return dao.getById(recordId)?.toDomain()
    }

    override suspend fun deleteRecords(recordIds: List<String>) {
        if (recordIds.isEmpty()) return
        dao.deleteByIds(recordIds)
    }

    override fun observeRecords(): Flow<List<ParseRecord>> {
        return dao.observeAll().map { list -> list.map { it.toDomain() } }
    }
}

