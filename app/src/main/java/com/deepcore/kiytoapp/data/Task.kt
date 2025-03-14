package com.deepcore.kiytoapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters
import java.util.Date

@Entity(tableName = "tasks")
@TypeConverters(Converters::class)
data class Task(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val priority: Priority = Priority.MEDIUM,
    val completed: Boolean = false,
    
    @TypeConverters(Converters::class)
    val dueDate: Date? = null,
    
    @TypeConverters(Converters::class)
    val tags: List<String> = emptyList(),
    
    @TypeConverters(Converters::class)
    val created: Date = Date(),
    
    val calendarEventId: Long? = null,
    
    @TypeConverters(Converters::class)
    val startTime: Date? = null,
    
    @TypeConverters(Converters::class)
    val endTime: Date? = null,
    
    @TypeConverters(Converters::class)
    val completedDate: Date? = null
)

enum class Priority {
    LOW, MEDIUM, HIGH
} 