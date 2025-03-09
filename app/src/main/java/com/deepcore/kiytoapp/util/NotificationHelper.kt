package com.deepcore.kiytoapp.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import com.deepcore.kiytoapp.MainActivity
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.settings.NotificationSettingsManager
import android.widget.Toast

class NotificationHelper(private val context: Context) {
    
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private var channelId = "pomodoro_channel"
    private val settingsManager = NotificationSettingsManager(context)
    
    init {
        LogUtils.debug(this, "NotificationHelper initialisiert")
        // Generiere eine eindeutige Kanal-ID basierend auf dem aktuellen Ton
        updateChannelId()
        createNotificationChannel()
    }
    
    private fun updateChannelId() {
        // Generiere eine eindeutige Kanal-ID basierend auf dem aktuellen Ton
        val soundUri = settingsManager.notificationSound
        val soundHash = soundUri.toString().hashCode()
        channelId = "pomodoro_channel_$soundHash"
        LogUtils.debug(this, "Kanal-ID aktualisiert: $channelId für Sound: $soundUri")
    }
    
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                // Hole aktuelle Einstellungen
                var soundUri = settingsManager.notificationSound
                val backgroundPath = settingsManager.backgroundImagePath
                
                // Überprüfe, ob der URI gültig ist
                try {
                    val cursor = context.contentResolver.query(soundUri, null, null, null, null)
                    if (cursor == null) {
                        LogUtils.warn(this, "Sound URI ist ungültig, verwende Standard-Benachrichtigungston")
                        soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    } else {
                        cursor.close()
                    }
                } catch (e: Exception) {
                    LogUtils.error(this, "Fehler beim Überprüfen des Sound URI, verwende Standard-Benachrichtigungston", e)
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
                
                LogUtils.debug(this, "Erstelle Benachrichtigungskanal mit Sound: $soundUri")
                LogUtils.debug(this, "Hintergrundbild-Pfad: $backgroundPath")
                LogUtils.debug(this, "Verwende Kanal-ID: $channelId")

                val audioAttributes = AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build()

                val channel = NotificationChannel(
                    channelId,
                    "Pomodoro Timer",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Benachrichtigungen für den Pomodoro Timer"
                    enableVibration(true)
                    enableLights(true)
                    
                    // Wichtig: Setze den Sound explizit
                    if (soundUri != Uri.EMPTY) {
                        setSound(soundUri, audioAttributes)
                        LogUtils.debug(this@NotificationHelper, "Sound für Kanal gesetzt: $soundUri")
                    } else {
                        LogUtils.warn(this@NotificationHelper, "Kein gültiger Sound-URI für Kanal")
                    }
                }
                
                notificationManager.createNotificationChannel(channel)
                LogUtils.info(this, "Benachrichtigungskanal erfolgreich erstellt")
            } catch (e: Exception) {
                LogUtils.error(this, "Fehler beim Erstellen des Benachrichtigungskanals", e)
            }
        } else {
            LogUtils.debug(this, "Benachrichtigungskanal wird nicht erstellt (Android < O)")
        }
    }
    
    fun showTimerCompleteNotification(title: String, message: String) {
        try {
            LogUtils.debug(this, "Zeige Benachrichtigung: $title - $message")
            
            // Überprüfen des Zeitplans
            if (settingsManager.notificationScheduleEnabled) {
                val currentTime = java.time.LocalTime.now()
                val startTime = java.time.LocalTime.parse(settingsManager.scheduleStartTime)
                val endTime = java.time.LocalTime.parse(settingsManager.scheduleEndTime)
                
                if (currentTime !in startTime..endTime) {
                    LogUtils.debug(this, "Benachrichtigung wird nicht gesendet (außerhalb des Zeitplans)")
                    return // Keine Benachrichtigung außerhalb des Zeitplans
                }
            }
            
            // Intent für die App-Öffnung
            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE
            )

            // Hole den Sound URI direkt vor dem Erstellen der Benachrichtigung
            var soundUri = settingsManager.notificationSound
            
            // Überprüfe, ob der URI gültig ist
            try {
                val cursor = context.contentResolver.query(soundUri, null, null, null, null)
                if (cursor == null) {
                    LogUtils.warn(this, "Sound URI ist ungültig, verwende Standard-Benachrichtigungston")
                    soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                } else {
                    cursor.close()
                }
            } catch (e: Exception) {
                LogUtils.error(this, "Fehler beim Überprüfen des Sound URI, verwende Standard-Benachrichtigungston", e)
                soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            }
            
            LogUtils.debug(this, "Verwende Sound für Benachrichtigung: $soundUri")
            LogUtils.debug(this, "Verwende Kanal-ID: $channelId")

            // Builder für die Benachrichtigung
            val notificationBuilder = NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(longArrayOf(0, 500, 250, 500))
            
            // Setze den Sound explizit für alle Android-Versionen
            if (soundUri != Uri.EMPTY) {
                notificationBuilder.setSound(soundUri)
                LogUtils.debug(this, "Sound für Benachrichtigung explizit gesetzt: $soundUri")
            }
                
            val notification = notificationBuilder.build()
            
            // Wichtig: Setze die Flags für die Benachrichtigung
            notification.flags = notification.flags or android.app.Notification.FLAG_SHOW_LIGHTS
            
            // Setze den Sound direkt in der Benachrichtigung
            notification.sound = soundUri
            LogUtils.debug(this, "Sound direkt in der Benachrichtigung gesetzt: $soundUri")
            
            notificationManager.notify(TIMER_NOTIFICATION_ID, notification)
            LogUtils.info(this, "Benachrichtigung erfolgreich gesendet")
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Anzeigen der Benachrichtigung", e)
        }
    }

    fun updateNotificationSound(soundUri: Uri) {
        try {
            LogUtils.debug(this, "Aktualisiere Benachrichtigungston: $soundUri")
            
            // Überprüfe, ob der URI gültig ist
            try {
                val cursor = context.contentResolver.query(soundUri, null, null, null, null)
                if (cursor == null) {
                    LogUtils.warn(this, "Sound URI ist ungültig, verwende Standard-Benachrichtigungston")
                    val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    settingsManager.notificationSound = defaultUri
                    LogUtils.debug(this, "Standard-Benachrichtigungston gesetzt: $defaultUri")
                    return
                } else {
                    cursor.close()
                }
            } catch (e: Exception) {
                LogUtils.error(this, "Fehler beim Überprüfen des Sound URI, verwende Standard-Benachrichtigungston", e)
                val defaultUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                settingsManager.notificationSound = defaultUri
                LogUtils.debug(this, "Standard-Benachrichtigungston gesetzt nach Fehler: $defaultUri")
                return
            }
            
            // Speichere den neuen Sound
            settingsManager.notificationSound = soundUri
            
            // Überprüfe, ob der Sound korrekt gespeichert wurde
            val savedUri = settingsManager.notificationSound
            LogUtils.debug(this, "Gespeicherter Sound nach dem Setzen: $savedUri")
            
            // Lösche alle vorhandenen Kanäle
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val existingChannels = notificationManager.notificationChannels
                for (channel in existingChannels) {
                    if (channel.id.startsWith("pomodoro_channel")) {
                        LogUtils.debug(this, "Lösche Kanal: ${channel.id}")
                        notificationManager.deleteNotificationChannel(channel.id)
                    }
                }
            }
            
            // Aktualisiere die Kanal-ID und erstelle einen neuen Kanal
            updateChannelId()
            createNotificationChannel()
            
            // Zeige eine Bestätigungsnachricht an
            Toast.makeText(
                context,
                "Benachrichtigungston wurde aktualisiert",
                Toast.LENGTH_SHORT
            ).show()
            
            LogUtils.info(this, "Benachrichtigungston erfolgreich aktualisiert")
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Aktualisieren des Benachrichtigungstons", e)
            throw e
        }
    }
    
    /**
     * Sendet eine Test-Benachrichtigung, um den aktuellen Sound zu testen
     */
    private fun showTestNotification() {
        showTimerCompleteNotification(
            "Benachrichtigungston wurde aktualisiert",
            "Dies ist eine Test-Benachrichtigung"
        )
    }
    
    companion object {
        private const val TIMER_NOTIFICATION_ID = 1001
    }
} 