package com.deepcore.kiytoapp.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Task::class], 
    version = 2,  // Version auf 2 erhöht
    exportSchema = true // Schema-Export aktivieren
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        private const val TAG = "AppDatabase"
        private const val DATABASE_NAME = "task_database"
        
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE tasks ADD COLUMN calendarEventId INTEGER DEFAULT NULL")
            }
        }
        
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                try {
                    Log.d(TAG, "Initialisiere Datenbank...")
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        DATABASE_NAME
                    )
                    .addMigrations(MIGRATION_1_2)  // Migration hinzugefügt
                    .fallbackToDestructiveMigration()
                    .allowMainThreadQueries() // Nur für Debugging
                    .build()
                    
                    INSTANCE = instance
                    Log.d(TAG, "Datenbank erfolgreich initialisiert")
                    instance
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler bei der Datenbankinitialisierung: ${e.message}", e)
                    throw e
                }
            }
        }
    }
} 