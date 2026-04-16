package com.deepcore.kiytoapp.settings

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.deepcore.kiytoapp.MainActivity
import com.deepcore.kiytoapp.R

/**
 * BroadcastReceiver zum Anzeigen von Benachrichtigungen für verschiedene App-Ereignisse,
 * wie z.B. Erinnerungen für optimale Fokuszeiten.
 */
class NotificationReceiver : BroadcastReceiver() {
    private val TAG = "NotificationReceiver"
    
    companion object {
        const val CHANNEL_ID = "kiyto_notifications"
        const val FOCUS_TIME_NOTIFICATION_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Benachrichtigung empfangen: ${intent.action}")
        
        when (intent.action) {
            "FOCUS_TIME_REMINDER" -> {
                val title = intent.getStringExtra("NOTIFICATION_TITLE") ?: "Zeit für Fokus!"
                val message = intent.getStringExtra("NOTIFICATION_MESSAGE") 
                    ?: "Jetzt ist deine optimale Fokuszeit. Starte eine Fokussession!"
                
                showFocusTimeNotification(context, title, message)
            }
            else -> {
                Log.d(TAG, "Unbekannte Aktion: ${intent.action}")
            }
        }
    }
    
    private fun showFocusTimeNotification(context: Context, title: String, message: String) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Erstelle Notification Channel für Android 8.0+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Kiyto Benachrichtigungen",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Benachrichtigungen für Fokuszeiten und andere Ereignisse"
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        // Intent für das Öffnen der App
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("OPEN_FOCUS", true)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            context, 
            0, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Erstelle die Benachrichtigung
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification) // Stelle sicher, dass dieses Icon existiert
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        
        // Zeige die Benachrichtigung an
        notificationManager.notify(FOCUS_TIME_NOTIFICATION_ID, notification)
        
        Log.d(TAG, "Fokuszeit-Benachrichtigung angezeigt: $title")
    }
} 