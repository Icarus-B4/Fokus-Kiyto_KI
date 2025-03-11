package com.deepcore.kiytoapp.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.SystemClock
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.data.Task
import com.deepcore.kiytoapp.data.TaskRepository
import com.deepcore.kiytoapp.settings.TaskNotificationManager
import com.deepcore.kiytoapp.util.LogUtils
import com.deepcore.kiytoapp.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/**
 * Service für die Verwaltung von Aufgaben-Benachrichtigungen
 * Überwacht Aufgaben und sendet Benachrichtigungen bei Fälligkeit oder Abschluss
 */
class TaskNotificationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var taskRepository: TaskRepository
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var taskNotificationManager: TaskNotificationManager
    private lateinit var alarmManager: AlarmManager
    
    private val taskCompletedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_TASK_COMPLETED) {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
                val taskTitle = intent.getStringExtra(EXTRA_TASK_TITLE) ?: ""
                
                if (taskId != -1L && taskTitle.isNotEmpty()) {
                    showTaskCompletedNotification(taskId, taskTitle)
                }
            }
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        LogUtils.debug(this, "TaskNotificationService erstellt")
        
        taskRepository = TaskRepository(applicationContext)
        notificationHelper = NotificationHelper(applicationContext)
        taskNotificationManager = TaskNotificationManager(applicationContext)
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        // Registriere den Broadcast-Receiver für abgeschlossene Aufgaben
        registerReceiver(taskCompletedReceiver, IntentFilter(ACTION_TASK_COMPLETED))
        
        // Starte den Überprüfungszyklus für fällige Aufgaben
        scheduleTaskDueCheck()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        LogUtils.debug(this, "TaskNotificationService gestartet")
        
        // Wenn der Service neu gestartet wird, plane die Überprüfung erneut
        if (intent?.action == ACTION_RESTART) {
            scheduleTaskDueCheck()
        }
        
        return START_STICKY
    }
    
    override fun onDestroy() {
        super.onDestroy()
        LogUtils.debug(this, "TaskNotificationService beendet")
        
        try {
            unregisterReceiver(taskCompletedReceiver)
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Abmelden des Receivers", e)
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    /**
     * Plant die regelmäßige Überprüfung auf fällige Aufgaben
     */
    private fun scheduleTaskDueCheck() {
        if (!taskNotificationManager.taskDueNotificationsEnabled) {
            LogUtils.debug(this, "Benachrichtigungen für fällige Aufgaben sind deaktiviert")
            return
        }
        
        val intent = Intent(this, TaskDueCheckReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Überprüfe alle 15 Minuten
        val interval = 15 * 60 * 1000L // 15 Minuten in Millisekunden
        
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + interval,
            interval,
            pendingIntent
        )
        
        LogUtils.debug(this, "Überprüfung auf fällige Aufgaben geplant (Intervall: $interval ms)")
    }
    
    /**
     * Zeigt eine Benachrichtigung für eine abgeschlossene Aufgabe an
     */
    private fun showTaskCompletedNotification(taskId: Long, taskTitle: String) {
        if (!taskNotificationManager.taskCompleteNotificationsEnabled) {
            LogUtils.debug(this, "Benachrichtigungen für abgeschlossene Aufgaben sind deaktiviert")
            return
        }
        
        val title = getString(R.string.notification_task_complete)
        val message = getString(R.string.notification_task_complete_message, taskTitle)
        
        notificationHelper.showTimerCompleteNotification(title, message)
        LogUtils.debug(this, "Benachrichtigung für abgeschlossene Aufgabe gesendet: $taskTitle")
    }
    
    /**
     * Überprüft, ob Aufgaben fällig sind und sendet Benachrichtigungen
     */
    fun checkDueTasks() {
        if (!taskNotificationManager.taskDueNotificationsEnabled) {
            LogUtils.debug(this, "Benachrichtigungen für fällige Aufgaben sind deaktiviert")
            return
        }
        
        serviceScope.launch {
            try {
                val tasks = taskRepository.getAllTasks()
                val now = Date()
                val leadTimeMillis = taskNotificationManager.taskDueNotificationLeadTime * 60 * 1000L
                
                // Filtere Aufgaben, die in Kürze fällig sind oder gerade fällig geworden sind
                val dueTasks = tasks.filter { task ->
                    task.dueDate != null && 
                    !task.completed &&
                    task.dueDate!!.time > now.time &&
                    task.dueDate!!.time - now.time <= leadTimeMillis
                }
                
                for (task in dueTasks) {
                    showTaskDueNotification(task)
                }
                
                LogUtils.debug(this@TaskNotificationService, "Überprüfung auf fällige Aufgaben abgeschlossen: ${dueTasks.size} fällige Aufgaben gefunden")
            } catch (e: Exception) {
                LogUtils.error(this@TaskNotificationService, "Fehler bei der Überprüfung auf fällige Aufgaben", e)
            }
        }
    }
    
    /**
     * Zeigt eine Benachrichtigung für eine fällige Aufgabe an
     */
    private fun showTaskDueNotification(task: Task) {
        val title = getString(R.string.notification_task_due)
        val message = getString(R.string.notification_task_due_message, task.title)
        
        notificationHelper.showTimerCompleteNotification(title, message)
        LogUtils.debug(this, "Benachrichtigung für fällige Aufgabe gesendet: ${task.title}")
    }
    
    companion object {
        const val ACTION_TASK_COMPLETED = "com.deepcore.kiytoapp.ACTION_TASK_COMPLETED"
        const val ACTION_RESTART = "com.deepcore.kiytoapp.ACTION_RESTART_TASK_NOTIFICATION_SERVICE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TASK_TITLE = "task_title"
        
        /**
         * Sendet einen Broadcast für eine abgeschlossene Aufgabe
         */
        fun notifyTaskCompleted(context: Context, taskId: Long, taskTitle: String) {
            val intent = Intent(ACTION_TASK_COMPLETED).apply {
                putExtra(EXTRA_TASK_ID, taskId)
                putExtra(EXTRA_TASK_TITLE, taskTitle)
            }
            context.sendBroadcast(intent)
        }
    }
}

/**
 * BroadcastReceiver für die Überprüfung auf fällige Aufgaben
 */
class TaskDueCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        LogUtils.debug(this, "TaskDueCheckReceiver: Überprüfung auf fällige Aufgaben")
        
        val serviceIntent = Intent(context, TaskNotificationService::class.java)
        context.startService(serviceIntent)
        
        // Starte den Service und führe die Überprüfung durch
        val service = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val isRunning = service.getRunningServices(Integer.MAX_VALUE)
            .any { it.service.className == TaskNotificationService::class.java.name }
        
        if (isRunning) {
            // Wenn der Service läuft, führe die Überprüfung direkt durch
            val notificationService = TaskNotificationService()
            notificationService.checkDueTasks()
        } else {
            // Wenn der Service nicht läuft, starte ihn neu
            val restartIntent = Intent(context, TaskNotificationService::class.java).apply {
                action = TaskNotificationService.ACTION_RESTART
            }
            context.startService(restartIntent)
        }
    }
} 