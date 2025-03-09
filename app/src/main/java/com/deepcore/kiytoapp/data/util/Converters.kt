package com.deepcore.kiytoapp.data.util

import androidx.room.TypeConverter
import com.deepcore.kiytoapp.data.entity.Task
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }

    @TypeConverter
    fun fromPriority(priority: Task.Priority): String {
        return priority.name
    }

    @TypeConverter
    fun toPriority(value: String): Task.Priority {
        return Task.Priority.valueOf(value)
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) {
            return emptyList()
        }
        
        return try {
            val listType = object : TypeToken<List<String>>() {}.type
            gson.fromJson(value, listType) ?: emptyList()
        } catch (e: Exception) {
            // Fallback für den Fall, dass der Wert kein gültiges JSON-Array ist
            // Versuche, den String als einzelnen Eintrag zu behandeln
            if (value.startsWith("[") && value.endsWith("]")) {
                // Es sieht aus wie ein JSON-Array, aber es gab einen Fehler beim Parsen
                emptyList()
            } else {
                // Behandle es als einzelnen String-Eintrag
                listOf(value)
            }
        }
    }
} 