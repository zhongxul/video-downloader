package com.example.videodownloader.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ParseRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ParseRecordEntity)

    @Update
    suspend fun update(entity: ParseRecordEntity)

    @Query("SELECT * FROM parse_records WHERE id = :recordId LIMIT 1")
    suspend fun getById(recordId: String): ParseRecordEntity?

    @Query("SELECT * FROM parse_records ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ParseRecordEntity>>

    @Query("DELETE FROM parse_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)
}

