package com.deepcore.kiytoapp.util

import android.content.Context
import android.util.Log
import android.widget.Toast
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Hilfsklasse für einheitliches Logging in der App
 */
object LogUtils {
    private const val APP_TAG = "KiytoApp"
    private var isDebugEnabled = true
    
    // In-Memory Log-Speicher
    private val logBuffer = ConcurrentLinkedQueue<LogEntry>()
    private const val MAX_LOG_ENTRIES = 1000

    /**
     * Debug-Logging aktivieren oder deaktivieren
     */
    fun setDebugEnabled(enabled: Boolean) {
        isDebugEnabled = enabled
    }

    /**
     * Debug-Log mit Klassenname als Tag
     */
    fun debug(clazz: Any, message: String) {
        if (isDebugEnabled) {
            val tag = "${APP_TAG}_${clazz::class.java.simpleName}"
            Log.d(tag, message)
            addToBuffer(LogLevel.DEBUG, tag, message)
        }
    }

    /**
     * Info-Log mit Klassenname als Tag
     */
    fun info(clazz: Any, message: String) {
        val tag = "${APP_TAG}_${clazz::class.java.simpleName}"
        Log.i(tag, message)
        addToBuffer(LogLevel.INFO, tag, message)
    }

    /**
     * Fehler-Log mit Klassenname als Tag
     */
    fun error(clazz: Any, message: String, throwable: Throwable? = null) {
        val tag = "${APP_TAG}_${clazz::class.java.simpleName}"
        Log.e(tag, message, throwable)
        addToBuffer(LogLevel.ERROR, tag, message + (throwable?.let { "\n${it.message}\n${it.stackTraceToString()}" } ?: ""))
    }

    /**
     * Warnung-Log mit Klassenname als Tag
     */
    fun warn(clazz: Any, message: String) {
        val tag = "${APP_TAG}_${clazz::class.java.simpleName}"
        Log.w(tag, message)
        addToBuffer(LogLevel.WARN, tag, message)
    }
    
    /**
     * Fügt einen Log-Eintrag zum internen Puffer hinzu
     */
    private fun addToBuffer(level: LogLevel, tag: String, message: String) {
        logBuffer.add(LogEntry(System.currentTimeMillis(), level, tag, message))
        
        // Begrenze die Größe des Puffers
        while (logBuffer.size > MAX_LOG_ENTRIES) {
            logBuffer.poll()
        }
    }
    
    /**
     * Gibt alle Logs aus dem internen Puffer zurück
     */
    fun getInternalLogs(): List<LogEntry> {
        return logBuffer.toList()
    }
    
    /**
     * Gibt alle Logs aus dem internen Puffer zurück, die mit dem angegebenen Tag beginnen
     */
    fun getInternalLogsByTag(tagPrefix: String): List<LogEntry> {
        return logBuffer.filter { it.tag.startsWith(tagPrefix) }
    }
    
    /**
     * Gibt alle Logs aus dem internen Puffer zurück, die mit dem angegebenen Level übereinstimmen
     */
    fun getInternalLogsByLevel(level: LogLevel): List<LogEntry> {
        return logBuffer.filter { it.level == level }
    }
    
    /**
     * Löscht alle Logs aus dem internen Puffer
     */
    fun clearInternalLogs() {
        logBuffer.clear()
    }
    
    /**
     * Liest die Logcat-Logs für die App
     */
    fun readLogcat(context: Context, packageName: String = context.packageName, lines: Int = 1000): String {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d -v threadtime | grep $packageName")
            val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
            val log = StringBuilder()
            var line: String?
            var count = 0
            
            while (bufferedReader.readLine().also { line = it } != null && count < lines) {
                log.append(line).append("\n")
                count++
            }
            
            bufferedReader.close()
            log.toString()
        } catch (e: Exception) {
            "Fehler beim Lesen der Logs: ${e.message}"
        }
    }
    
    /**
     * Zeigt die Logs in einem Toast an (für Debugging)
     */
    fun showLogsToast(context: Context, filter: String = "") {
        val logs = if (filter.isEmpty()) {
            getInternalLogs().takeLast(5).joinToString("\n") { "${it.tag}: ${it.message}" }
        } else {
            getInternalLogs().filter { it.tag.contains(filter) || it.message.contains(filter) }
                .takeLast(5).joinToString("\n") { "${it.tag}: ${it.message}" }
        }
        
        Toast.makeText(context, logs, Toast.LENGTH_LONG).show()
    }
    
    /**
     * Log-Level Enum
     */
    enum class LogLevel {
        DEBUG, INFO, WARN, ERROR
    }
    
    /**
     * Log-Eintrag Datenklasse
     */
    data class LogEntry(
        val timestamp: Long,
        val level: LogLevel,
        val tag: String,
        val message: String
    )
} 