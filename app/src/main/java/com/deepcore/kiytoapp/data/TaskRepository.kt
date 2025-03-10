package com.deepcore.kiytoapp.data

import android.content.Context
import com.deepcore.kiytoapp.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.Date

/**
 * Repository-Klasse für den Zugriff auf Aufgaben
 * Dient als Schnittstelle zwischen dem TaskNotificationService und der Datenbank
 */
class TaskRepository(private val context: Context) {
    private val taskManager = TaskManager(context)
    
    /**
     * Gibt alle Aufgaben zurück
     * @return Liste aller Aufgaben
     */
    suspend fun getAllTasks(): List<Task> = withContext(Dispatchers.IO) {
        try {
            LogUtils.debug(this, "Lade alle Aufgaben")
            val tasks = taskManager.getAllTasks().first()
            LogUtils.debug(this, "${tasks.size} Aufgaben geladen")
            tasks
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Laden der Aufgaben", e)
            emptyList()
        }
    }
    
    /**
     * Gibt eine Aufgabe anhand ihrer ID zurück
     * @param taskId ID der Aufgabe
     * @return Die Aufgabe oder null, wenn sie nicht gefunden wurde
     */
    suspend fun getTaskById(taskId: Long): Task? = withContext(Dispatchers.IO) {
        try {
            LogUtils.debug(this, "Lade Aufgabe mit ID: $taskId")
            val database = AppDatabase.getInstance(context)
            val task = database.taskDao().getTaskById(taskId)
            if (task != null) {
                LogUtils.debug(this, "Aufgabe gefunden: ${task.title}")
            } else {
                LogUtils.warn(this, "Keine Aufgabe mit ID $taskId gefunden")
            }
            task
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Laden der Aufgabe mit ID $taskId", e)
            null
        }
    }
    
    /**
     * Gibt alle aktiven Aufgaben zurück, die bis zu einem bestimmten Datum fällig sind
     * @param date Datum, bis zu dem die Aufgaben fällig sein müssen
     * @return Liste der fälligen Aufgaben
     */
    suspend fun getTasksDueBefore(date: Date): List<Task> = withContext(Dispatchers.IO) {
        try {
            LogUtils.debug(this, "Lade Aufgaben, die vor $date fällig sind")
            val allTasks = taskManager.getAllTasks().first()
            val dueTasks = allTasks.filter { task ->
                !task.completed && task.dueDate != null && task.dueDate.before(date)
            }
            LogUtils.debug(this, "${dueTasks.size} fällige Aufgaben gefunden")
            dueTasks
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Laden der fälligen Aufgaben", e)
            emptyList()
        }
    }
    
    /**
     * Markiert eine Aufgabe als abgeschlossen
     * @param taskId ID der Aufgabe
     * @return true, wenn die Aufgabe erfolgreich aktualisiert wurde
     */
    suspend fun markTaskAsCompleted(taskId: Long): Boolean = withContext(Dispatchers.IO) {
        try {
            LogUtils.debug(this, "Markiere Aufgabe $taskId als abgeschlossen")
            val task = getTaskById(taskId)
            if (task != null) {
                val updatedTask = task.copy(completed = true)
                taskManager.updateTask(updatedTask)
                LogUtils.debug(this, "Aufgabe erfolgreich als abgeschlossen markiert")
                true
            } else {
                LogUtils.warn(this, "Aufgabe $taskId konnte nicht als abgeschlossen markiert werden (nicht gefunden)")
                false
            }
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Markieren der Aufgabe als abgeschlossen", e)
            false
        }
    }
    
    /**
     * Gibt alle Aufgaben zurück, die für ein bestimmtes Datum geplant sind
     * @param date Das Datum, für das die Aufgaben abgerufen werden sollen
     * @return Liste der Aufgaben für das angegebene Datum
     */
    suspend fun getTasksForDate(date: Date): List<Task> = withContext(Dispatchers.IO) {
        try {
            LogUtils.debug(this, "Lade Aufgaben für Datum: $date")
            val database = AppDatabase.getInstance(context)
            val tasks = database.taskDao().getTasksForDate(date).first()
            LogUtils.debug(this, "${tasks.size} Aufgaben für $date gefunden")
            tasks
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Laden der Aufgaben für Datum $date", e)
            emptyList()
        }
    }
} 