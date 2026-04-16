package com.deepcore.kiytoapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.deepcore.kiytoapp.data.dao.SummaryDao
import com.deepcore.kiytoapp.data.entity.Summary
import com.deepcore.kiytoapp.data.util.Converters

@Database(entities = [Summary::class], version = 1)
@TypeConverters(Converters::class)
abstract class SummaryDatabase : RoomDatabase() {
    abstract fun summaryDao(): SummaryDao

    companion object {
        @Volatile
        private var INSTANCE: SummaryDatabase? = null

        fun getDatabase(context: Context): SummaryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SummaryDatabase::class.java,
                    "summary_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
} 