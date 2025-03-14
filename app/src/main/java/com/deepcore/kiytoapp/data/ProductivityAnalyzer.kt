package com.deepcore.kiytoapp.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Date

/**
 * Diese Klasse analysiert die Produktivitätsmuster des Benutzers und bietet
 * Einblicke in die produktivsten Tageszeiten, Wochentage und Trends.
 */
class ProductivityAnalyzer(private val context: Context) {
    private val TAG = "ProductivityAnalyzer"
    private val sessionManager = SessionManager(context)
    private val taskManager = TaskManager(context)
    
    // Zeitblöcke für die Analyse
    enum class TimeBlock {
        EARLY_MORNING, // 5-8 Uhr
        MORNING,       // 8-12 Uhr
        AFTERNOON,     // 12-17 Uhr
        EVENING,       // 17-21 Uhr
        NIGHT          // 21-5 Uhr
    }
    
    /**
     * Analysiert die produktivsten Tageszeiten basierend auf Fokuszeit und erledigten Aufgaben
     * der letzten 30 Tage.
     * 
     * @return Map mit Zeitblöcken und ihren Produktivitätswerten (0-100)
     */
    suspend fun getMostProductiveTimeBlocks(): Map<TimeBlock, Float> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Analysiere produktivste Tageszeiten")
            
            val result = mutableMapOf<TimeBlock, Float>()
            val calendar = Calendar.getInstance()
            val today = calendar.timeInMillis
            
            // Setze Kalender auf vor 30 Tagen
            calendar.add(Calendar.DAY_OF_YEAR, -30)
            val thirtyDaysAgo = calendar.timeInMillis
            
            // Hole alle Fokussessions der letzten 30 Tage
            val focusSessions = sessionManager.getFocusSessions().filter { 
                it.startTime.time >= thirtyDaysAgo && it.startTime.time <= today 
            }
            
            // Hole alle erledigten Aufgaben der letzten 30 Tage
            val completedTasks = taskManager.getCompletedTasksInTimeRange(thirtyDaysAgo, today)
            
            // Initialisiere Zähler für jeden Zeitblock
            val focusMinutesByBlock = mutableMapOf<TimeBlock, Int>()
            val tasksCompletedByBlock = mutableMapOf<TimeBlock, Int>()
            val totalSessionsByBlock = mutableMapOf<TimeBlock, Int>()
            
            TimeBlock.values().forEach { block ->
                focusMinutesByBlock[block] = 0
                tasksCompletedByBlock[block] = 0
                totalSessionsByBlock[block] = 0
            }
            
            // Analysiere Fokussessions
            focusSessions.forEach { session ->
                val block = getTimeBlockForTimestamp(session.startTime.time)
                val minutes = (session.duration / 60000).toInt() // Konvertiere ms zu Minuten
                
                focusMinutesByBlock[block] = focusMinutesByBlock[block]!! + minutes
                totalSessionsByBlock[block] = totalSessionsByBlock[block]!! + 1
            }
            
            // Analysiere erledigte Aufgaben
            completedTasks.forEach { task ->
                if (task.completedDate != null) {
                    val block = getTimeBlockForTimestamp(task.completedDate!!.time)
                    tasksCompletedByBlock[block] = tasksCompletedByBlock[block]!! + 1
                }
            }
            
            // Berechne Produktivitätswert für jeden Zeitblock
            TimeBlock.values().forEach { block ->
                // Normalisiere Fokusminuten (max 180 Minuten pro Block als 100%)
                val normalizedFocusScore = (focusMinutesByBlock[block]!! / 180f).coerceAtMost(1f) * 50f
                
                // Normalisiere erledigte Aufgaben (max 5 Aufgaben pro Block als 100%)
                val normalizedTaskScore = (tasksCompletedByBlock[block]!! / 5f).coerceAtMost(1f) * 50f
                
                // Kombinierter Score (0-100)
                val productivityScore = normalizedFocusScore + normalizedTaskScore
                
                result[block] = productivityScore
                
                Log.d(TAG, "Zeitblock $block: $productivityScore (Fokus: ${focusMinutesByBlock[block]} min, " +
                        "Aufgaben: ${tasksCompletedByBlock[block]})")
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Analyse der produktivsten Tageszeiten", e)
            // Fallback-Werte zurückgeben
            TimeBlock.values().associateWith { 0f }
        }
    }
    
    /**
     * Bestimmt den optimalen Zeitpunkt für eine neue Fokussession basierend auf
     * historischen Daten und der aktuellen Tageszeit.
     * 
     * @return Empfohlene Startzeit als Date-Objekt
     */
    suspend fun suggestOptimalFocusTime(): Date = withContext(Dispatchers.IO) {
        try {
            val productiveBlocks = getMostProductiveTimeBlocks()
            val mostProductiveBlock = productiveBlocks.maxByOrNull { it.value }?.key ?: TimeBlock.MORNING
            
            val now = Calendar.getInstance()
            val currentHour = now.get(Calendar.HOUR_OF_DAY)
            
            // Bestimme die Startzeit basierend auf dem produktivsten Block und der aktuellen Zeit
            val suggestedHour = when {
                // Wenn wir bereits im produktivsten Block sind, jetzt starten
                isHourInTimeBlock(currentHour, mostProductiveBlock) -> currentHour
                
                // Sonst die Mitte des nächsten produktiven Blocks wählen
                else -> getNextTimeBlockStartHour(currentHour, mostProductiveBlock)
            }
            
            // Setze die vorgeschlagene Zeit
            val suggestedTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, suggestedHour)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                
                // Wenn die Zeit in der Vergangenheit liegt, auf morgen setzen
                if (timeInMillis < System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            
            Log.d(TAG, "Optimale Fokuszeit vorgeschlagen: ${suggestedTime.time}")
            suggestedTime.time
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Vorschlagen der optimalen Fokuszeit", e)
            // Fallback: Jetzt + 1 Stunde
            Date(System.currentTimeMillis() + 3600000)
        }
    }
    
    /**
     * Gibt eine Zusammenfassung der Produktivitätsmuster zurück.
     * 
     * @return Textuelle Zusammenfassung der Produktivitätsmuster
     */
    suspend fun getProductivityInsights(): String = withContext(Dispatchers.IO) {
        try {
            val productiveBlocks = getMostProductiveTimeBlocks()
            val mostProductiveBlock = productiveBlocks.maxByOrNull { it.value }?.key
            val leastProductiveBlock = productiveBlocks.minByOrNull { it.value }?.key
            
            val insights = StringBuilder()
            
            // Füge Erkenntnisse über die produktivsten Zeiten hinzu
            insights.append("Deine produktivste Tageszeit ist ")
            insights.append(when (mostProductiveBlock) {
                TimeBlock.EARLY_MORNING -> "der frühe Morgen (5-8 Uhr)"
                TimeBlock.MORNING -> "der Vormittag (8-12 Uhr)"
                TimeBlock.AFTERNOON -> "der Nachmittag (12-17 Uhr)"
                TimeBlock.EVENING -> "der Abend (17-21 Uhr)"
                TimeBlock.NIGHT -> "die Nacht (21-5 Uhr)"
                else -> "nicht eindeutig bestimmbar"
            })
            insights.append(".\n\n")
            
            // Füge Erkenntnisse über die am wenigsten produktiven Zeiten hinzu
            if (leastProductiveBlock != null && leastProductiveBlock != mostProductiveBlock) {
                insights.append("Am wenigsten produktiv bist du ")
                insights.append(when (leastProductiveBlock) {
                    TimeBlock.EARLY_MORNING -> "am frühen Morgen (5-8 Uhr)"
                    TimeBlock.MORNING -> "am Vormittag (8-12 Uhr)"
                    TimeBlock.AFTERNOON -> "am Nachmittag (12-17 Uhr)"
                    TimeBlock.EVENING -> "am Abend (17-21 Uhr)"
                    TimeBlock.NIGHT -> "in der Nacht (21-5 Uhr)"
                    else -> "zu keiner bestimmten Zeit"
                })
                insights.append(".\n\n")
            }
            
            // Füge Empfehlungen hinzu
            insights.append("Empfehlung: Plane wichtige Aufgaben für deine produktivste Zeit ein und nutze weniger produktive Zeiten für einfachere Tätigkeiten.")
            
            insights.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Generieren der Produktivitätserkenntnisse", e)
            "Leider konnten keine Produktivitätsmuster erkannt werden. Sammle mehr Daten, indem du die App regelmäßig nutzt."
        }
    }
    
    /**
     * Bestimmt den Zeitblock für einen gegebenen Zeitstempel.
     */
    private fun getTimeBlockForTimestamp(timestamp: Long): TimeBlock {
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        
        return when (hour) {
            in 5..7 -> TimeBlock.EARLY_MORNING
            in 8..11 -> TimeBlock.MORNING
            in 12..16 -> TimeBlock.AFTERNOON
            in 17..20 -> TimeBlock.EVENING
            else -> TimeBlock.NIGHT
        }
    }
    
    /**
     * Prüft, ob eine Stunde in einem bestimmten Zeitblock liegt.
     */
    private fun isHourInTimeBlock(hour: Int, block: TimeBlock): Boolean {
        return when (block) {
            TimeBlock.EARLY_MORNING -> hour in 5..7
            TimeBlock.MORNING -> hour in 8..11
            TimeBlock.AFTERNOON -> hour in 12..16
            TimeBlock.EVENING -> hour in 17..20
            TimeBlock.NIGHT -> hour in 21..23 || hour in 0..4
        }
    }
    
    /**
     * Bestimmt die Startstunde des nächsten Zeitblocks.
     */
    private fun getNextTimeBlockStartHour(currentHour: Int, block: TimeBlock): Int {
        return when (block) {
            TimeBlock.EARLY_MORNING -> if (currentHour < 5) 5 else 5 + 24 // Heute oder morgen
            TimeBlock.MORNING -> if (currentHour < 8) 8 else 8 + 24
            TimeBlock.AFTERNOON -> if (currentHour < 12) 12 else 12 + 24
            TimeBlock.EVENING -> if (currentHour < 17) 17 else 17 + 24
            TimeBlock.NIGHT -> if (currentHour < 21) 21 else 21 + 24
        }
    }
} 