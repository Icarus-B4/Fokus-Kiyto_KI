package com.deepcore.kiytoapp.settings

import android.content.Context
import android.content.SharedPreferences
import com.deepcore.kiytoapp.util.LogUtils

/**
 * Manager-Klasse für Aufgaben-Benachrichtigungen
 * Verwaltet Einstellungen für Benachrichtigungen bei abgeschlossenen und fälligen Aufgaben
 */
class TaskNotificationManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Prüft, ob Benachrichtigungen für abgeschlossene Aufgaben aktiviert sind
     */
    var taskCompleteNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TASK_COMPLETE_NOTIFICATIONS, true)
        set(value) {
            LogUtils.debug(this, "Setze Benachrichtigungen für abgeschlossene Aufgaben: $value")
            prefs.edit().putBoolean(KEY_TASK_COMPLETE_NOTIFICATIONS, value).apply()
        }
    
    /**
     * Prüft, ob Benachrichtigungen für fällige Aufgaben aktiviert sind
     */
    var taskDueNotificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_TASK_DUE_NOTIFICATIONS, true)
        set(value) {
            LogUtils.debug(this, "Setze Benachrichtigungen für fällige Aufgaben: $value")
            prefs.edit().putBoolean(KEY_TASK_DUE_NOTIFICATIONS, value).apply()
        }
    
    /**
     * Gibt die Vorlaufzeit für Benachrichtigungen bei fälligen Aufgaben in Minuten zurück
     * Standardwert ist 15 Minuten
     */
    var taskDueNotificationLeadTime: Int
        get() = prefs.getInt(KEY_TASK_DUE_LEAD_TIME, 15)
        set(value) {
            LogUtils.debug(this, "Setze Vorlaufzeit für fällige Aufgaben: $value Minuten")
            prefs.edit().putInt(KEY_TASK_DUE_LEAD_TIME, value).apply()
        }
    
    /**
     * Setzt alle Benachrichtigungseinstellungen auf die Standardwerte zurück
     */
    fun resetToDefaults() {
        LogUtils.info(this, "Setze Aufgaben-Benachrichtigungseinstellungen auf Standardwerte zurück")
        
        prefs.edit().apply {
            putBoolean(KEY_TASK_COMPLETE_NOTIFICATIONS, true)
            putBoolean(KEY_TASK_DUE_NOTIFICATIONS, true)
            putInt(KEY_TASK_DUE_LEAD_TIME, 15)
            apply()
        }
        
        LogUtils.info(this, "Aufgaben-Benachrichtigungseinstellungen wurden zurückgesetzt")
    }
    
    companion object {
        private const val PREFS_NAME = "task_notification_settings"
        private const val KEY_TASK_COMPLETE_NOTIFICATIONS = "task_complete_notifications"
        private const val KEY_TASK_DUE_NOTIFICATIONS = "task_due_notifications"
        private const val KEY_TASK_DUE_LEAD_TIME = "task_due_lead_time"
    }
} 