package com.deepcore.kiytoapp.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Date
import java.util.Calendar
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class SessionManager(context: Context) {
    private val TAG = "SessionManager"
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        "session_data",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveFocusSession(session: FocusSession) {
        val sessions = getFocusSessions().toMutableList()
        sessions.add(session)
        
        prefs.edit().putString(KEY_SESSIONS, gson.toJson(sessions)).apply()
    }

    fun getFocusSessions(): List<FocusSession> {
        val json = prefs.getString(KEY_SESSIONS, "[]")
        val type = object : TypeToken<List<FocusSession>>() {}.type
        return gson.fromJson(json, type)
    }

    fun getStatistics(): Statistics {
        val sessions = getFocusSessions()
        val focusTime = getTotalFocusTimeForToday()
        
        return Statistics(
            totalFocusTime = focusTime * 60 * 1000L, // Konvertiere Minuten zu Millisekunden
            completedTasks = sessions.count { it.completed },
            completedPomodoros = focusTime / 25, // Eine Pomodoro = 25 Minuten
            activeDays = getActiveDays(sessions)
        )
    }

    private fun getActiveDays(sessions: List<FocusSession>): Int {
        return sessions.map { session ->
            val cal = Calendar.getInstance().apply { time = session.startTime }
            "${cal.get(Calendar.YEAR)}${cal.get(Calendar.MONTH)}${cal.get(Calendar.DAY_OF_MONTH)}"
        }.distinct().size
    }

    private fun isSameDay(date1: Date, date2: Date): Boolean {
        val cal1 = Calendar.getInstance().apply { time = date1 }
        val cal2 = Calendar.getInstance().apply { time = date2 }
        
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.MONTH) == cal2.get(Calendar.MONTH) &&
               cal1.get(Calendar.DAY_OF_MONTH) == cal2.get(Calendar.DAY_OF_MONTH)
    }

    companion object {
        private const val PREFS_NAME = "focus_sessions"
        private const val KEY_SESSIONS = "sessions"
        private const val KEY_FOCUS_TIME_PREFIX = "focus_time_"
        private const val KEY_LAST_SESSION = "last_session"
    }

    fun addFocusTime(minutes: Int) {
        try {
            val today = getTodayKey()
            val currentTime = getFocusTimeForDate(today)
            Log.d(TAG, "Aktuelle Fokuszeit: $currentTime, Hinzufügen: $minutes Minuten")
            
            encryptedPrefs.edit()
                .putInt(KEY_FOCUS_TIME_PREFIX + today, currentTime + minutes)
                .apply()
            
            // Speichere auch eine neue Session
            val session = FocusSession(
                startTime = Date(System.currentTimeMillis() - minutes * 60 * 1000),
                duration = minutes * 60 * 1000L,
                completed = true,
                interrupted = false
            )
            saveFocusSession(session)
            
            Log.d(TAG, "Fokuszeit hinzugefügt: $minutes Minuten, Neue Gesamtzeit: ${currentTime + minutes}")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Speichern der Fokuszeit", e)
        }
    }

    fun getTotalFocusTimeForToday(): Int {
        val time = getFocusTimeForDate(getTodayKey())
        Log.d(TAG, "Fokuszeit heute abgerufen: $time Minuten")
        return time
    }

    fun getTotalFocusTimeForDate(timestamp: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val dateKey = getDateKey(calendar)
        val time = getFocusTimeForDate(dateKey)
        Log.d(TAG, "Fokuszeit für ${dateKey}: $time Minuten")
        return time
    }

    /**
     * Gibt die Fokuszeit für eine bestimmte Stunde eines Tages zurück.
     * Da wir aktuell nur tägliche Fokuszeiten speichern, simulieren wir stündliche Daten
     * basierend auf typischen Aktivitätsmustern.
     * 
     * @param timestamp Der Zeitstempel der Stunde, für die die Fokuszeit abgerufen werden soll
     * @return Die Fokuszeit in Minuten (0-60)
     */
    fun getFocusTimeForHour(timestamp: Long): Int {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val dateKey = getDateKey(calendar)
        
        // Hole die Gesamtfokuszeit für den Tag
        val totalDayFocusTime = getFocusTimeForDate(dateKey)
        
        // Wenn keine Fokuszeit für den Tag vorhanden ist, gib 0 zurück
        if (totalDayFocusTime == 0) {
            return 0
        }
        
        // Simuliere eine Verteilung der Fokuszeit über den Tag
        // Produktive Stunden sind typischerweise 9-12 Uhr und 14-17 Uhr
        val productivityFactor = when (hour) {
            in 9..11 -> 0.2f  // Vormittag: hohe Produktivität
            in 14..16 -> 0.15f // Nachmittag: mittlere bis hohe Produktivität
            in 12..13 -> 0.05f // Mittagszeit: niedrige Produktivität
            in 17..19 -> 0.1f  // Früher Abend: mittlere Produktivität
            in 20..22 -> 0.05f // Später Abend: niedrige Produktivität
            else -> 0.01f      // Nacht/früher Morgen: sehr niedrige Produktivität
        }
        
        // Berechne die Fokuszeit für diese Stunde basierend auf dem Produktivitätsfaktor
        // und der Gesamtfokuszeit für den Tag
        val focusTimeForHour = (totalDayFocusTime * productivityFactor).toInt()
        
        // Begrenze die Fokuszeit auf maximal 60 Minuten pro Stunde
        return focusTimeForHour.coerceIn(0, 60)
    }

    private fun getFocusTimeForDate(dateKey: String): Int {
        return try {
            val savedTime = encryptedPrefs.getInt(KEY_FOCUS_TIME_PREFIX + dateKey, 0)
            Log.d(TAG, "Gespeicherte Fokuszeit für $dateKey: $savedTime Minuten")
            savedTime
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Abrufen der Fokuszeit für $dateKey", e)
            0
        }
    }

    private fun getTodayKey(): String {
        return getDateKey(Calendar.getInstance())
    }

    private fun getDateKey(calendar: Calendar): String {
        return String.format(
            "%d-%02d-%02d",
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH) + 1,
            calendar.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun startSession() {
        encryptedPrefs.edit()
            .putLong(KEY_LAST_SESSION, System.currentTimeMillis())
            .apply()
        Log.d(TAG, "Neue Session gestartet")
    }

    fun endSession() {
        val lastSession = encryptedPrefs.getLong(KEY_LAST_SESSION, 0)
        if (lastSession > 0) {
            val sessionLength = (System.currentTimeMillis() - lastSession) / 60000 // Konvertiere zu Minuten
            Log.d(TAG, "Session beendet, Länge: $sessionLength Minuten")
            addFocusTime(sessionLength.toInt())
            encryptedPrefs.edit().remove(KEY_LAST_SESSION).apply()
        }
    }
}

data class Statistics(
    val totalFocusTime: Long,
    val completedTasks: Int,
    val completedPomodoros: Int,
    val activeDays: Int
) 