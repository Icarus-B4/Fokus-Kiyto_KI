package com.deepcore.kiytoapp.services

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deepcore.kiytoapp.util.LogUtils

/**
 * BroadcastReceiver, der beim Gerätestart ausgeführt wird
 * Startet den TaskNotificationService, um Benachrichtigungen für Aufgaben zu ermöglichen
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            LogUtils.debug(this, "Gerät gestartet, starte TaskNotificationService")
            
            // Starte den TaskNotificationService
            val serviceIntent = Intent(context, TaskNotificationService::class.java)
            serviceIntent.action = TaskNotificationService.ACTION_RESTART
            context.startService(serviceIntent)
        }
    }
} 