package com.deepcore.kiytoapp.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.Flow
import java.util.Date

class TaskManager(private val context: Context) {
    private val TAG = "TaskManager"
    private val database: AppDatabase
    private val taskDao: TaskDao

    init {
        try {
            Log.d(TAG, "Initialisiere TaskManager...")
            database = AppDatabase.getInstance(context)
            taskDao = database.taskDao()
            Log.d(TAG, "TaskManager erfolgreich initialisiert")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der TaskManager-Initialisierung: ${e.message}", e)
            throw e
        }
    }

    suspend fun createTask(task: Task): Long {
        return try {
            Log.d(TAG, "Erstelle neue Aufgabe: $task")
            val taskWithZeroId = task.copy(id = 0)
            val id = taskDao.insert(taskWithZeroId)
            Log.d(TAG, "Aufgabe erfolgreich erstellt mit ID: $id")
            id
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Erstellen der Aufgabe: ${e.message}", e)
            throw e
        }
    }

    suspend fun updateTask(task: Task) {
        try {
            Log.d(TAG, "Aktualisiere Aufgabe: $task")
            taskDao.update(task)
            Log.d(TAG, "Aufgabe erfolgreich aktualisiert")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Aktualisieren der Aufgabe: ${e.message}", e)
            throw e
        }
    }

    suspend fun deleteTask(taskId: Long) {
        try {
            Log.d(TAG, "Lösche Aufgabe: $taskId")
            taskDao.delete(taskId)
            Log.d(TAG, "Aufgabe erfolgreich gelöscht")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Löschen der Aufgabe: ${e.message}", e)
            throw e
        }
    }

    fun getAllTasks(): Flow<List<Task>> {
        Log.d(TAG, "Lade alle Aufgaben")
        return taskDao.getAllTasks()
    }

    fun getTasksByPriority(priority: Priority): Flow<List<Task>> {
        Log.d(TAG, "Lade Aufgaben mit Priorität: $priority")
        return taskDao.getTasksByPriority(priority)
    }

    fun searchTasks(query: String): Flow<List<Task>> {
        Log.d(TAG, "Suche nach Aufgaben mit Query: $query")
        return taskDao.searchTasks("%$query%")
    }

    suspend fun toggleTaskCompletion(taskId: Long) {
        try {
            val task = taskDao.getTaskById(taskId)
            task?.let {
                val newCompletedState = !it.completed
                val completedDate = if (newCompletedState) Date() else null
                
                val updatedTask = it.copy(
                    completed = newCompletedState,
                    completedDate = completedDate
                )
                taskDao.update(updatedTask)
                Log.d(TAG, "Task ${taskId} Status geändert: ${updatedTask.completed}, Abschlussdatum: ${updatedTask.completedDate}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Ändern des Task-Status: ${e.message}", e)
            throw e
        }
    }

    fun getTasksByDueDate(includeCompleted: Boolean = false): Flow<List<Task>> {
        Log.d(TAG, "Lade Aufgaben nach Fälligkeitsdatum (includeCompleted: $includeCompleted)")
        return if (includeCompleted) {
            taskDao.getTasksByDueDate()
        } else {
            taskDao.getActiveTasksByDueDate()
        }
    }

    fun getCompletedTasks(): Flow<List<Task>> {
        Log.d(TAG, "Lade abgeschlossene Aufgaben")
        return taskDao.getCompletedTasks()
    }

    fun getTasksSortedByPriority(): Flow<List<Task>> {
        Log.d(TAG, "Lade Aufgaben sortiert nach Priorität")
        return taskDao.getTasksSortedByPriority()
    }

    /**
     * Ruft alle abgeschlossenen Aufgaben in einem bestimmten Zeitraum ab.
     * 
     * @param startTime Startzeit des Zeitraums in Millisekunden
     * @param endTime Endzeit des Zeitraums in Millisekunden
     * @return Liste der abgeschlossenen Aufgaben im angegebenen Zeitraum
     */
    suspend fun getCompletedTasksInTimeRange(startTime: Long, endTime: Long): List<Task> {
        try {
            Log.d(TAG, "Lade abgeschlossene Aufgaben im Zeitraum von $startTime bis $endTime")
            
            // Hole alle abgeschlossenen Aufgaben
            val allCompletedTasks = taskDao.getCompletedTasksAsList()
            
            // Filtere nach dem angegebenen Zeitraum
            // Wir prüfen, ob die Aufgabe ein completedDate hat und ob es im Zeitraum liegt
            return allCompletedTasks.filter { task ->
                task.completedDate != null && 
                task.completedDate!!.time >= startTime && 
                task.completedDate!!.time <= endTime
            }.also {
                Log.d(TAG, "Gefundene abgeschlossene Aufgaben im Zeitraum: ${it.size}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden der abgeschlossenen Aufgaben im Zeitraum", e)
            return emptyList()
        }
    }
} 