package com.deepcore.kiytoapp.data

import androidx.room.TypeConverter
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromString(value: String?): List<String> {
        if (value.isNullOrEmpty()) {
            return emptyList()
        }
        
        return try {
            // Versuche zuerst, es als kommagetrennte Liste zu behandeln
            value.split(",").filter { it.isNotEmpty() }
        } catch (e: Exception) {
            // Fallback: Behandle es als einzelnen String-Eintrag
            listOf(value)
        }
    }

    @TypeConverter
    fun toString(list: List<String>?): String {
        return list?.joinToString(",") ?: ""
    }
} 