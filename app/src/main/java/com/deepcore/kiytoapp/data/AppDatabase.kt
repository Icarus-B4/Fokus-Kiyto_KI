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
    version = 8,  // Version auf 8 erhöht, um mit TaskDatabase übereinzustimmen
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
        
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Keine strukturellen Änderungen, nur Verbesserung der Converters
                // Keine SQL-Anweisungen erforderlich
            }
        }
        
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Keine strukturellen Änderungen, nur Verbesserung der Converters
                // Keine SQL-Anweisungen erforderlich
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
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)  // Migrationen hinzugefügt
                    .fallbackToDestructiveMigration() // Destruktive Migration bei Versionskonflikten
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