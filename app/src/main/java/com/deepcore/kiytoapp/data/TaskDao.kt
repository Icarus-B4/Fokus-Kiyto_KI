package com.deepcore.kiytoapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.util.Date

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE priority = :priority")
    fun getTasksByPriority(priority: Priority): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE title LIKE :searchQuery OR description LIKE :searchQuery")
    fun searchTasks(searchQuery: String): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: Long): Task?

    @Query("SELECT * FROM tasks ORDER BY dueDate ASC")
    fun getTasksByDueDate(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE completed = 0 ORDER BY dueDate ASC")
    fun getActiveTasksByDueDate(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE completed = 1")
    fun getCompletedTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks ORDER BY CASE priority WHEN 'HIGH' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END")
    fun getTasksSortedByPriority(): Flow<List<Task>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(task: Task): Long

    @Update
    suspend fun update(task: Task)

    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun delete(taskId: Long)

    @Query("SELECT * FROM tasks WHERE date(dueDate/1000, 'unixepoch') = date(:date/1000, 'unixepoch')")
    fun getTasksForDate(date: Date): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE date(dueDate/1000, 'unixepoch') = date(:date/1000, 'unixepoch') ORDER BY dueDate ASC")
    fun getTasksForDateSortedByTime(date: Date): Flow<List<Task>>
} 