package com.yunx.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "resolve_history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val link: String,
    val title: String = "",
    val platform: String = "",
    val result: String = "SUCCESS",
    val createdAt: Long = System.currentTimeMillis()
)
