package com.deepcore.kiytoapp.ai

import android.content.Context
import android.util.Log
import com.deepcore.kiytoapp.data.Priority
import com.deepcore.kiytoapp.data.Task
import com.deepcore.kiytoapp.data.TaskManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

class TaskAIService(private val context: Context) {
    private val TAG = "TaskAIService"
    private val taskManager = TaskManager(context)

    private val systemPrompt = """
        Du bist ein hilfreicher KI-Assistent für eine Produktivitäts-App. Du hilfst Benutzern bei:
        1. Aufgabenverwaltung und Priorisierung
        2. Fokus-Timer und Pomodoro-Technik
        3. Produktivitäts Tipps und Best Practices
        
        Antworte immer auf Deutsch und in einem freundlichen, professionellen Ton.
        
        Wenn der Benutzer eine neue Aufgabe erstellen möchte, extrahiere den Titel und erstelle eine CreateTask-Aktion.
        Wenn der Benutzer einen Timer starten möchte, extrahiere die Minuten und erstelle eine SetTimer-Aktion.
        Wenn der Benutzer Spracheingabe nutzen möchte, erstelle eine StartVoiceInput-Aktion.
        
        Halte deine Antworten kurz und prägnant.
    """.trimIndent()

    init {
        try {
            Log.d(TAG, "Initialisiere TaskAIService")
            OpenAIService.initialize(context)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Initialisierung des TaskAIService", e)
            throw e
        }
    }

    private fun isCommand(message: String): Boolean {
        val normalizedMessage = message.lowercase()
        return normalizedMessage.contains("timer") ||
               normalizedMessage.contains("fokus") ||
               normalizedMessage.contains("öffne") ||
               normalizedMessage.contains("starte") ||
               normalizedMessage.contains("aufgabe") ||
               normalizedMessage.contains("task") ||
               normalizedMessage.contains("todo")
    }

    suspend fun generateResponse(message: String): ChatMessage {
        Log.d(TAG, "Generating response for: $message")
        
        try {
            // Direkte Befehlserkennung
            val normalizedMessage = message.lowercase()
            
            // Timer-Befehl
            if (isTimerCommand(message)) {
                val minutes = extractMinutes(message)
                Log.d(TAG, "Timer-Befehl erkannt: $minutes Minuten")
                return ChatMessage(
                    content = "Ich starte einen Timer für $minutes Minuten.",
                    isUser = false,
                    action = ChatAction.SetTimer(minutes)
                )
            }
            
            // Kalender öffnen
            if (normalizedMessage.contains("öffne") && normalizedMessage.contains("kalender")) {
                return ChatMessage(
                    content = "Ich öffne den Kalender.",
                    isUser = false,
                    action = ChatAction.OpenCalendar()
                )
            }
            
            // Musik öffnen
            if ((normalizedMessage.contains("öffne") || normalizedMessage.contains("starte")) && 
                (normalizedMessage.contains("musik") || normalizedMessage.contains("player"))) {
                return ChatMessage(
                    content = "Ich öffne den Musik-Player.",
                    isUser = false,
                    action = ChatAction.PlaySpotify()
                )
            }

            // Wenn es ein Befehl ist, verwende GPT-3.5
            if (isCommand(message)) {
                val response = OpenAIService.chat(message)
                return response
            }
            
            // Für normale Chats GPT-4 verwenden
            val response = OpenAIService.chat(message)
            return response
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Nachrichtenverarbeitung", e)
            return ChatMessage(
                content = "Entschuldigung, es gab einen Fehler bei der Verarbeitung Ihrer Anfrage.",
                isUser = false
            )
        }
    }

    private fun isTaskCreationIntent(message: String): Boolean {
        val taskIndicators = listOf(
            "erstell", "neue", "aufgabe", "task", "todo", "to-do",
            "hinzufüg", "eintrag", "notier", "merk", "füge", "schreib"
        )
        
        val normalizedMessage = message.lowercase()
        return taskIndicators.any { normalizedMessage.contains(it) }
    }

    private fun isProductivityIntent(message: String): Boolean {
        val productivityIndicators = listOf(
            "tipp", "rat", "hilfe", "produktiv", "effizient",
            "fokus", "konzentration", "zeitmanagement", "planen"
        )
        
        val normalizedMessage = message.lowercase()
        return productivityIndicators.any { normalizedMessage.contains(it) }
    }

    private fun getProductivityTip(): String {
        val tips = listOf(
            "Tipp: Nutze die 2-Minuten-Regel - Wenn eine Aufgabe weniger als 2 Minuten dauert, erledige sie sofort.",
            "Tipp: Plane deinen Tag am Vorabend - So startest du fokussiert in den Tag.",
            "Tipp: Nutze die Pomodoro-Technik - 25 Minuten Arbeit, 5 Minuten Pause.",
            "Tipp: Priorisiere deine Aufgaben nach der Eisenhower-Matrix - Wichtig vs. Dringend.",
            "Tipp: Lege Handy und andere Ablenkungen während der Arbeitszeit beiseite.",
            "Tipp: Mache regelmäßige Pausen - Sie steigern deine Produktivität.",
            "Tipp: Setze dir SMART-Ziele - Spezifisch, Messbar, Attraktiv, Realistisch, Terminiert.",
            "Tipp: Nutze die 1-3-5-Regel: Plane täglich 1 große, 3 mittlere und 5 kleine Aufgaben."
        )
        return tips.random()
    }

    private fun extractTaskTitle(message: String): String {
        val input = message.trim()
        
        // Entferne Befehlswörter am Anfang
        val cleanedInput = input
            .lowercase()
            .replace(Regex("^(erstelle|neue|eine|aufgabe|task|todo|to-do|hinzufügen|bitte|notiere|notieren|merke|merken|füge|schreibe)\\s+"), "")
            .trim()
            .replaceFirstChar { it.uppercase() }
        
        // Wenn der bereinigte Input leer ist oder nur aus Befehlswörtern besteht
        if (cleanedInput.isBlank() || 
            cleanedInput.lowercase() in listOf("aufgabe", "task", "todo", "to-do")) {
            return ""
        }
        
        Log.d(TAG, "Extrahierter Titel nach Bereinigung: '$cleanedInput'")
        return cleanedInput
    }

    private fun extractMinutes(message: String): Int {
        val normalizedMessage = message.lowercase()
        
        // Wortbasierte Zahlen
        val wordNumbers = mapOf(
            "eine" to 1,
            "ein" to 1,
            "zwei" to 2,
            "drei" to 3,
            "vier" to 4,
            "fünf" to 5,
            "sechs" to 6,
            "sieben" to 7,
            "acht" to 8,
            "neun" to 9,
            "zehn" to 10,
            "fünfzehn" to 15,
            "zwanzig" to 20,
            "dreißig" to 30
        )
        
        // Prüfe zuerst auf Wortzahlen
        for ((word, number) in wordNumbers) {
            if (normalizedMessage.contains(word)) {
                Log.d(TAG, "Wortzahl erkannt: $word = $number Minuten")
                return number
            }
        }
        
        // Suche nach numerischen Zahlen
        val numbers = Regex("\\d+").findAll(message)
            .map { it.value.toIntOrNull() ?: 0 }
            .filter { it in 1..120 } // Nur sinnvolle Werte
            .toList()
            
        val minutes = numbers.firstOrNull() ?: 25 // Standard: 25 Minuten
        Log.d(TAG, "Extrahierte Minuten: $minutes")
        return minutes
    }

    fun prioritizeTasks(tasks: List<Task>): List<Task> {
        // Einfacher Algorithmus für die Aufgabenpriorisierung
        return tasks.sortedWith(compareBy(
            { !it.completed }, // Nicht abgeschlossene Aufgaben zuerst
            { 
                when (it.priority) {
                    Priority.HIGH -> 0
                    Priority.MEDIUM -> 1
                    Priority.LOW -> 2
                }
            },
            { it.dueDate }, // Nach Fälligkeitsdatum sortieren
            { it.title } // Alphabetisch nach Titel sortieren
        ))
    }

    fun suggestPriority(task: Task): Priority {
        val text = "${task.title} ${task.description}".lowercase()
        val priority = when {
            text.contains("präsentation") || text.contains("meeting") || 
            text.contains("dringend") || text.contains("wichtig") || 
            text.contains("sofort") || text.contains("kritisch") -> Priority.HIGH
            
            text.contains("bald") || text.contains("zeitnah") || 
            text.contains("diese woche") || text.contains("termin") -> Priority.MEDIUM
            
            else -> Priority.MEDIUM  // Standard: MEDIUM statt LOW
        }
        Log.d(TAG, "Priorität für '${task.title}': $priority")
        return priority
    }

    fun suggestTags(task: Task): List<String> {
        // Einfache Tag-Vorschläge basierend auf Titel und Beschreibung
        val text = "${task.title} ${task.description}".lowercase()
        val tags = mutableListOf<String>()

        if (text.contains("arbeit") || text.contains("projekt")) tags.add("arbeit")
        if (text.contains("privat") || text.contains("persönlich")) tags.add("privat")
        if (text.contains("termin") || text.contains("meeting")) tags.add("termin")
        if (text.contains("einkauf") || text.contains("besorgen")) tags.add("einkauf")
        if (text.contains("lernen") || text.contains("studium")) tags.add("bildung")

        return tags
    }

    fun analyzeTasks(tasks: List<Task>): TaskAnalysis {
        return TaskAnalyzer.analyzeTaskPatterns(tasks)
    }

    private fun isTimerCommand(message: String): Boolean {
        val timerIndicators = listOf(
            "timer", "fokus", "pomodoro", 
            "setze timer", "stelle timer", "starte timer",
            "minuten", "konzentration"
        )
        
        // Spezifische Muster für Timer-Befehle
        val timerPatterns = listOf(
            "setze\\s*timer\\s*(?:auf|für)?\\s*\\d+",    // z.B. "setze timer auf 25"
            "stelle\\s*(?:einen)?\\s*timer\\s*(?:auf|für)?\\s*\\d+",  // z.B. "stelle einen timer auf 25"
            "starte\\s*(?:einen)?\\s*timer\\s*(?:für|von)?\\s*\\d+",  // z.B. "starte einen timer für 25"
            "timer\\s*(?:auf|für)?\\s*\\d+\\s*(?:minute[n]?|min)",    // z.B. "timer auf 8 minuten"
            "(?:starte|stelle|setze)\\s*(?:einen)?\\s*timer\\s*(?:auf|für)?\\s*\\d+\\s*(?:minute[n]?|min)"  // z.B. "starte einen timer für 8 minuten"
        )
        
        val normalizedMessage = message.lowercase()
        
        // Prüfe auf spezifische Muster
        for (pattern in timerPatterns) {
            if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(normalizedMessage)) {
                Log.d(TAG, "Timer-Befehl erkannt durch Muster: $pattern")
                return true
            }
        }
        
        // Fallback: Prüfe auf allgemeine Indikatoren
        val containsIndicator = timerIndicators.any { normalizedMessage.contains(it) }
        val containsNumber = extractMinutes(message) > 0
        
        if (containsIndicator && containsNumber) {
            Log.d(TAG, "Timer-Befehl erkannt durch Indikatoren und Zahl")
            return true
        }
        
        return false
    }

    suspend fun analyzeImage(file: File): String {
        return withContext(Dispatchers.IO) {
            try {
                var retryCount = 0
                var lastError: Exception? = null
                
                while (retryCount < 3) {
                    try {
                        val response = OpenAIService.analyzeImage(file)
                        if (!response.isNullOrBlank()) {
                            return@withContext response
                        }
                        retryCount++
                        delay(1000L * retryCount) // Exponentielles Backoff
                    } catch (e: Exception) {
                        lastError = e
                        Log.e(TAG, "Versuch ${retryCount + 1} fehlgeschlagen", e)
                        retryCount++
                        if (retryCount < 3) {
                            delay(1000L * retryCount) // Exponentielles Backoff
                        }
                    }
                }
                
                lastError?.let {
                    Log.e(TAG, "Alle Versuche der Bildanalyse fehlgeschlagen", it)
                }
                
                "Entschuldigung, ich konnte das Bild nicht analysieren. Bitte versuchen Sie es später erneut."
            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei der Bildanalyse", e)
                "Entschuldigung, bei der Analyse des Bildes ist ein Fehler aufgetreten."
            }
        }
    }

    suspend fun analyzeFile(fileName: String, content: String): String {
        return withContext(Dispatchers.IO) {
            try {
                val response = OpenAIService.analyzeFile(fileName, content)
                response ?: "Entschuldigung, ich konnte die Datei nicht analysieren."
            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei der Dateianalyse", e)
                "Entschuldigung, bei der Analyse der Datei ist ein Fehler aufgetreten."
            }
        }
    }
} 