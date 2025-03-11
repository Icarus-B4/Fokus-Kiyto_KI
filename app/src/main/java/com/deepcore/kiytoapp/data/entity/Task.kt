package com.deepcore.kiytoapp.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import com.deepcore.kiytoapp.data.util.Converters
import java.util.Date

@Entity(tableName = "tasks")
@TypeConverters(Converters::class)
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String,
    val dueDate: Date? = null,
    val priority: Priority = Priority.MEDIUM,
    val tags: List<String> = emptyList(),
    val completed: Boolean = false,
    val created: Date = Date(),
    val calendarEventId: Long? = null,
    val startTime: Date? = null,
    val endTime: Date? = null
) {
    enum class Priority {
        LOW, MEDIUM, HIGH
    }
} 