package com.deepcore.kiytoapp.debug

import android.os.Bundle
import android.view.MenuItem
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.settings.NotificationSettingsManager
import com.deepcore.kiytoapp.util.LogUtils
import com.deepcore.kiytoapp.util.NotificationHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Debug-Aktivität zum Anzeigen von Logs
 */
class DebugActivity : AppCompatActivity() {
    
    private lateinit var logTextView: TextView
    private lateinit var filterEditText: EditText
    private lateinit var refreshButton: Button
    private lateinit var clearButton: Button
    private lateinit var testNotificationButton: Button
    private lateinit var resetSettingsButton: Button
    
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var notificationSettingsManager: NotificationSettingsManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_debug)
        
        // Initialisiere Helfer
        notificationHelper = NotificationHelper(this)
        notificationSettingsManager = NotificationSettingsManager(this)
        
        // Aktiviere den Zurück-Button in der ActionBar
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Debug Logs"
        
        // Initialisiere Views
        logTextView = findViewById(R.id.logTextView)
        filterEditText = findViewById(R.id.filterEditText)
        refreshButton = findViewById(R.id.refreshButton)
        clearButton = findViewById(R.id.clearButton)
        testNotificationButton = findViewById(R.id.testNotificationButton)
        resetSettingsButton = findViewById(R.id.resetSettingsButton)
        
        // Setze Click-Listener
        refreshButton.setOnClickListener {
            refreshLogs()
        }
        
        clearButton.setOnClickListener {
            LogUtils.clearInternalLogs()
            refreshLogs()
        }
        
        testNotificationButton.setOnClickListener {
            testNotification()
        }
        
        resetSettingsButton.setOnClickListener {
            resetNotificationSettings()
        }
        
        // Zeige Logs beim Start
        refreshLogs()
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
    
    private fun refreshLogs() {
        val filter = filterEditText.text.toString().trim()
        val logs = if (filter.isEmpty()) {
            LogUtils.getInternalLogs()
        } else {
            LogUtils.getInternalLogs().filter { 
                it.tag.contains(filter, ignoreCase = true) || 
                it.message.contains(filter, ignoreCase = true) 
            }
        }
        
        val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val formattedLogs = logs.joinToString("\n\n") { entry ->
            val time = dateFormat.format(Date(entry.timestamp))
            val level = entry.level.name
            val tag = entry.tag
            val message = entry.message
            
            "[$time] [$level] $tag:\n$message"
        }
        
        logTextView.text = formattedLogs
    }
    
    private fun testNotification() {
        try {
            LogUtils.debug(this, "Teste Benachrichtigung mit aktuellen Einstellungen")
            
            // Zeige aktuelle Einstellungen
            val soundUri = notificationSettingsManager.notificationSound
            val backgroundPath = notificationSettingsManager.backgroundImagePath
            
            LogUtils.info(this, """
                Teste Benachrichtigung mit:
                - Sound URI: $soundUri
                - Hintergrundbild: $backgroundPath
            """.trimIndent())
            
            // Sende Test-Benachrichtigung
            notificationHelper.showTimerCompleteNotification(
                "Test-Benachrichtigung",
                "Dies ist eine Test-Benachrichtigung mit den aktuellen Einstellungen"
            )
            
            LogUtils.info(this, "Test-Benachrichtigung gesendet")
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Testen der Benachrichtigung", e)
        }
    }
    
    private fun resetNotificationSettings() {
        try {
            LogUtils.debug(this, "Setze Benachrichtigungseinstellungen zurück")
            
            // Zeige aktuelle Einstellungen vor dem Zurücksetzen
            val soundUriBefore = notificationSettingsManager.notificationSound
            val backgroundPathBefore = notificationSettingsManager.backgroundImagePath
            
            LogUtils.info(this, """
                Einstellungen vor dem Zurücksetzen:
                - Sound URI: $soundUriBefore
                - Hintergrundbild: $backgroundPathBefore
            """.trimIndent())
            
            // Setze Einstellungen zurück
            notificationSettingsManager.resetToDefaults()
            
            // Aktualisiere Benachrichtigungskanal
            notificationHelper.createNotificationChannel()
            
            // Zeige neue Einstellungen
            val soundUriAfter = notificationSettingsManager.notificationSound
            val backgroundPathAfter = notificationSettingsManager.backgroundImagePath
            
            LogUtils.info(this, """
                Einstellungen nach dem Zurücksetzen:
                - Sound URI: $soundUriAfter
                - Hintergrundbild: $backgroundPathAfter
            """.trimIndent())
            
            // Zeige Bestätigung
            android.widget.Toast.makeText(
                this,
                "Benachrichtigungseinstellungen wurden zurückgesetzt",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            
            // Aktualisiere Logs
            refreshLogs()
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Zurücksetzen der Benachrichtigungseinstellungen", e)
        }
    }
} 