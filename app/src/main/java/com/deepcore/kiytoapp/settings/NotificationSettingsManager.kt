package com.deepcore.kiytoapp.settings

import android.content.Context
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import com.deepcore.kiytoapp.util.LogUtils

class NotificationSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val appContext = context.applicationContext

    init {
        LogUtils.debug(this, "NotificationSettingsManager initialisiert")
    }

    var notificationSound: Uri
        get() {
            val savedUri = prefs.getString(KEY_NOTIFICATION_SOUND, null)
            LogUtils.debug(this, "Gespeicherter Benachrichtigungston URI: $savedUri")
            
            return if (savedUri != null && savedUri.isNotEmpty()) {
                try {
                    val uri = Uri.parse(savedUri)
                    LogUtils.debug(this, "Benachrichtigungston URI erfolgreich geparst: $uri")
                    
                    // Überprüfe, ob der URI gültig ist
                    val contentResolver = appContext.contentResolver
                    try {
                        val cursor = contentResolver.query(uri, null, null, null, null)
                        if (cursor != null) {
                            cursor.close()
                            uri
                        } else {
                            LogUtils.warn(this, "URI ist ungültig, verwende Standard-Benachrichtigungston")
                            val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                            // Speichere den Standard-Benachrichtigungston, damit wir ihn nicht jedes Mal neu abrufen müssen
                            prefs.edit().putString(KEY_NOTIFICATION_SOUND, defaultUri.toString()).apply()
                            LogUtils.debug(this, "Verwende Standard-Benachrichtigungston: $defaultUri")
                            defaultUri
                        }
                    } catch (e: Exception) {
                        LogUtils.error(this, "Fehler beim Abfragen des URI: $uri", e)
                        val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        // Speichere den Standard-Benachrichtigungston, damit wir ihn nicht jedes Mal neu abrufen müssen
                        prefs.edit().putString(KEY_NOTIFICATION_SOUND, defaultUri.toString()).apply()
                        LogUtils.debug(this, "Verwende Standard-Benachrichtigungston nach Fehler: $defaultUri")
                        defaultUri
                    }
                } catch (e: Exception) {
                    LogUtils.error(this, "Fehler beim Parsen des Benachrichtigungston URI: $savedUri", e)
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    // Speichere den Standard-Benachrichtigungston, damit wir ihn nicht jedes Mal neu abrufen müssen
                    prefs.edit().putString(KEY_NOTIFICATION_SOUND, defaultUri.toString()).apply()
                    LogUtils.debug(this, "Verwende Standard-Benachrichtigungston nach Parse-Fehler: $defaultUri")
                    defaultUri
                }
            } else {
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                // Speichere den Standard-Benachrichtigungston, damit wir ihn nicht jedes Mal neu abrufen müssen
                prefs.edit().putString(KEY_NOTIFICATION_SOUND, defaultUri.toString()).apply()
                LogUtils.debug(this, "Kein gespeicherter Ton, verwende Standard-Benachrichtigungston: $defaultUri")
                defaultUri
            }
        }
        set(value) {
            if (value == Uri.EMPTY) {
                LogUtils.warn(this, "Versuch, einen leeren URI zu speichern. Verwende Standard-Benachrichtigungston")
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                prefs.edit().putString(KEY_NOTIFICATION_SOUND, defaultUri.toString()).apply()
                return
            }
            
            LogUtils.debug(this, "Speichere neuen Benachrichtigungston: $value")
            
            // Überprüfe, ob der URI gültig ist
            try {
                val contentResolver = appContext.contentResolver
                val cursor = contentResolver.query(value, null, null, null, null)
                if (cursor != null) {
                    cursor.close()
                    // URI ist gültig, speichern
                    prefs.edit().putString(KEY_NOTIFICATION_SOUND, value.toString()).apply()
                    
                    // Überprüfe, ob der Wert korrekt gespeichert wurde
                    val savedValue = prefs.getString(KEY_NOTIFICATION_SOUND, null)
                    LogUtils.debug(this, "Gespeicherter Wert nach dem Setzen: $savedValue")
                    
                    if (savedValue != value.toString()) {
                        LogUtils.error(this, "Fehler beim Speichern des Benachrichtigungstons: Gespeicherter Wert stimmt nicht mit dem gesetzten Wert überein")
                    }
                } else {
                    LogUtils.warn(this, "URI ist ungültig, verwende Standard-Benachrichtigungston")
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    prefs.edit().putString(KEY_NOTIFICATION_SOUND, defaultUri.toString()).apply()
                }
            } catch (e: Exception) {
                LogUtils.error(this, "Fehler beim Überprüfen des URI: $value", e)
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                prefs.edit().putString(KEY_NOTIFICATION_SOUND, defaultUri.toString()).apply()
            }
        }

    var backgroundImagePath: String?
        get() {
            val path = prefs.getString(KEY_BACKGROUND_IMAGE, null)
            LogUtils.debug(this, "Gespeicherter Hintergrundbild-Pfad: $path")
            return path
        }
        set(value) {
            LogUtils.debug(this, "Speichere neuen Hintergrundbild-Pfad: $value")
            prefs.edit().putString(KEY_BACKGROUND_IMAGE, value).apply()
        }

    var notificationScheduleEnabled: Boolean
        get() {
            val enabled = prefs.getBoolean(KEY_SCHEDULE_ENABLED, false)
            LogUtils.debug(this, "Benachrichtigungszeitplan aktiviert: $enabled")
            return enabled
        }
        set(value) {
            LogUtils.debug(this, "Setze Benachrichtigungszeitplan aktiviert: $value")
            prefs.edit().putBoolean(KEY_SCHEDULE_ENABLED, value).apply()
        }

    var scheduleStartTime: String
        get() {
            val time = prefs.getString(KEY_SCHEDULE_START, "09:00") ?: "09:00"
            LogUtils.debug(this, "Zeitplan Startzeit: $time")
            return time
        }
        set(value) {
            LogUtils.debug(this, "Setze Zeitplan Startzeit: $value")
            prefs.edit().putString(KEY_SCHEDULE_START, value).apply()
        }

    var scheduleEndTime: String
        get() {
            val time = prefs.getString(KEY_SCHEDULE_END, "17:00") ?: "17:00"
            LogUtils.debug(this, "Zeitplan Endzeit: $time")
            return time
        }
        set(value) {
            LogUtils.debug(this, "Setze Zeitplan Endzeit: $value")
            prefs.edit().putString(KEY_SCHEDULE_END, value).apply()
        }

    companion object {
        private const val PREFS_NAME = "notification_settings"
        private const val KEY_NOTIFICATION_SOUND = "notification_sound"
        private const val KEY_BACKGROUND_IMAGE = "background_image"
        private const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
        private const val KEY_SCHEDULE_START = "schedule_start"
        private const val KEY_SCHEDULE_END = "schedule_end"
    }
    
    /**
     * Setzt alle Benachrichtigungseinstellungen auf die Standardwerte zurück
     */
    fun resetToDefaults() {
        LogUtils.info(this, "Setze Benachrichtigungseinstellungen auf Standardwerte zurück")
        
        val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        
        prefs.edit().apply {
            putString(KEY_NOTIFICATION_SOUND, defaultSound.toString())
            putString(KEY_BACKGROUND_IMAGE, null)
            putBoolean(KEY_SCHEDULE_ENABLED, false)
            putString(KEY_SCHEDULE_START, "09:00")
            putString(KEY_SCHEDULE_END, "17:00")
            apply()
        }
        
        LogUtils.info(this, "Benachrichtigungseinstellungen wurden zurückgesetzt")
    }
} 