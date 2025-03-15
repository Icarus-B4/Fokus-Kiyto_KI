package com.deepcore.kiytoapp.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "summaries")
data class Summary(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val content: String,
    val videoUrl: String?,
    val createdAt: Date = Date(),
    val isCompleted: Boolean = false
) 