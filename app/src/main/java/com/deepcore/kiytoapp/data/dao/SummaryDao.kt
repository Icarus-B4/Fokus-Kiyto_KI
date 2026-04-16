package com.deepcore.kiytoapp.data.dao

import androidx.room.*
import com.deepcore.kiytoapp.data.entity.Summary
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Query("SELECT * FROM summaries ORDER BY createdAt DESC")
    fun getAllSummaries(): Flow<List<Summary>>

    @Query("SELECT * FROM summaries WHERE isCompleted = 0")
    fun getActiveSummaries(): Flow<List<Summary>>

    @Query("SELECT * FROM summaries WHERE isCompleted = 1")
    fun getCompletedSummaries(): Flow<List<Summary>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: Summary)

    @Update
    suspend fun update(summary: Summary)

    @Delete
    suspend fun delete(summary: Summary)

    @Query("UPDATE summaries SET isCompleted = :completed WHERE id = :summaryId")
    suspend fun updateCompletionStatus(summaryId: Long, completed: Boolean)
} 