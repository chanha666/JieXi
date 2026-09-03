package com.yunx.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {
    @Query("SELECT * FROM resolve_history ORDER BY createdAt DESC") fun observeAll(): Flow<List<HistoryEntity>>
    @Insert suspend fun insert(item: HistoryEntity): Long
    @Query("DELETE FROM resolve_history WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM resolve_history") suspend fun clear()
    @Query("DELETE FROM resolve_history WHERE createdAt < :threshold") suspend fun deleteOlderThan(threshold: Long)
}
