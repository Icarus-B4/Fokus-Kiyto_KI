package com.deepcore.kiytoapp.ai

import android.content.Context
import android.util.Log
import com.deepcore.kiytoapp.data.Quote
import com.deepcore.kiytoapp.utils.ApiKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.concurrent.TimeUnit

data class Message(
    val role: String,
    val content: String
)

object OpenAIService {
    private const val TAG = "OpenAIService"
    private const val BASE_URL = "https://api.openai.com/v1/"
    
    private val messageHistory = mutableListOf<Message>()
    private var lastTimerMinutes = 0
    private var apiKey: String? = null
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Spezieller Client für TTS mit längeren Timeouts
    private val ttsClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private var customApiKey: String? = null

    init {
        messageHistory.add(Message("system", """
            Du bist ein hilfreicher KI-Assistent für eine Produktivitäts-App. Kurze Antworten:
            1. Max. 2-3 Sätze pro Antwort
            2. Fokus auf konkrete Aktionen
            3. Direkte Befehlsausführung
            
            Antworte auf Deutsch und professionell.
            Halte Antworten kurz und prägnant.
        """.trimIndent()))
    }

    fun initialize(context: Context) {
        try {
            // Versuche den Key über den zentralen ApiKeys-Manager zu laden
            // (behandelt BuildConfig, SharedPreferences und Verschlüsselung)
            apiKey = ApiKeys.getOpenAIApiKey(context)
            
            if (apiKey.isNullOrEmpty()) {
                Log.e(TAG, "Kein API-Key gefunden. Bitte in den Einstellungen konfigurieren.")
            } else {
                Log.d(TAG, "OpenAI API-Key erfolgreich geladen")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Laden des API-Keys", e)
        }
    }

    private fun validateApiKey(key: String?): Boolean {
        if (key.isNullOrEmpty()) return false
        val trimmedKey = key.trim()
        
        // Überprüfe zuerst die Mindestlänge
        if (trimmedKey.length < 32) {
            Log.d(TAG, "API-Key zu kurz: ${trimmedKey.length} Zeichen")
            return false
        }
        
        // Überprüfe das Format
        val validFormat = trimmedKey.matches(Regex("^[A-Za-z0-9_-]+$"))
        if (!validFormat) {
            Log.d(TAG, "API-Key enthält ungültige Zeichen")
            return false
        }
        
        return true
    }

    private fun extractMinutes(input: String): Int {
        // Suche nach Zahlen gefolgt von "min" oder "minuten"
        val regex = Regex("(\\d+)\\s*(?:min(?:uten)?)")
        val match = regex.find(input.lowercase())
        return match?.groupValues?.get(1)?.toIntOrNull() ?: 25
    }

    suspend fun chat(input: String): ChatMessage {
        try {
            // Lokale Antworten basierend auf Schlüsselwörtern
            val localResponse = when {
                input.equals("ja", ignoreCase = true) && lastTimerMinutes > 0 -> {
                    ChatMessage(
                        content = "Ich starte den Timer für $lastTimerMinutes Minuten.",
                        isUser = false,
                        action = ChatAction.SetTimer(lastTimerMinutes)
                    )
                }
                
                // Wenn die Eingabe die Timer-Frage enthält, behandeln wir sie als "ja"
                input.contains("möchten sie einen timer für", ignoreCase = true) -> {
                    val minutes = lastTimerMinutes
                    ChatMessage(
                        content = "Ich starte den Timer für $minutes Minuten.",
                        isUser = false,
                        action = ChatAction.SetTimer(minutes)
                    )
                }
                
                input.contains("timer", ignoreCase = true) || 
                input.contains("fokus", ignoreCase = true) -> {
                    val minutes = extractMinutes(input)
                    lastTimerMinutes = minutes
                    ChatMessage(
                        content = "Möchten Sie einen Timer für $minutes Minuten starten?",
                        isUser = false,
                        action = ChatAction.SetTimer(minutes)
                    )
                }
                
                input.contains("aufgabe", ignoreCase = true) && 
                (input.contains("erstellen", ignoreCase = true) || 
                 input.contains("anlegen", ignoreCase = true) || 
                 input.contains("neue", ignoreCase = true)) -> {
                    ChatMessage(
                        content = "Ich kann Ihnen helfen, eine neue Aufgabe zu erstellen. Was möchten Sie erledigen?",
                        isUser = false
                    )
                }
                
                input.contains("hilfe", ignoreCase = true) -> {
                    ChatMessage(
                        content = "Ich kann Ihnen bei der Aufgabenverwaltung, Zeitplanung und Fokussierung helfen. Was möchten Sie wissen?",
                        isUser = false
                    )
                }
                
                input.contains("hallo", ignoreCase = true) || 
                input.contains("hi", ignoreCase = true) || 
                input.contains("hey", ignoreCase = true) || 
                input.contains("hei", ignoreCase = true) -> {
                    ChatMessage(
                        content = "Hallo! Wie kann ich Ihnen heute helfen? Ich kann Sie bei der Aufgabenverwaltung und Zeitplanung unterstützen.",
                        isUser = false
                    )
                }
                
                else -> null
            }

            if (localResponse != null) {
                Log.d(TAG, "Lokale Antwort: ${localResponse.content}")
                return localResponse
            }

            // Wenn keine lokale Antwort und kein API-Key verfügbar
            if (apiKey.isNullOrEmpty()) {
                Log.w(TAG, "Kein API-Key verfügbar, verwende Fallback-Antwort")
                return getFallbackResponse(input)
            }

            // Wenn API-Key verfügbar, versuche OpenAI-Anfrage
            try {
                return callOpenAI(input)
            } catch (e: Exception) {
                Log.e(TAG, "OpenAI API-Aufruf fehlgeschlagen, verwende Fallback-Antwort", e)
                return getFallbackResponse(input)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Chat fehlgeschlagen", e)
            return ChatMessage(
                content = "Entschuldigung, es gab ein Problem. Ich verwende vorerst lokale Antworten. " +
                          "Sie können in den Einstellungen einen gültigen API-Key eingeben.",
                isUser = false
            )
        }
    }

    private suspend fun callOpenAI(input: String): ChatMessage = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrEmpty()) {
            Log.e(TAG, "API key is not set")
            return@withContext ChatMessage(
                content = "Entschuldigung, der API-Schlüssel ist nicht konfiguriert.",
                isUser = false
            )
        }

        try {
            // Füge die Benutzernachricht zum Verlauf hinzu
            messageHistory.add(Message("user", input))

            // Bereite den Request-Body vor
            val requestBody = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().apply {
                    messageHistory.forEach { message ->
                        put(JSONObject().apply {
                            put("role", message.role)
                            put("content", message.content)
                        })
                    }
                })
                put("temperature", 0.7)
                put("max_tokens", 500)
            }

            // Log des Request-Body für Debugging
            Log.d(TAG, "Request body: ${requestBody.toString()}")
            
            // Erstelle den Request mit dem korrekten API-Schlüssel
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${apiKey?.trim()}")
                .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            Log.d(TAG, "Sending request to OpenAI API...")
            
            // Führe den Request aus
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Received response from OpenAI API with code: ${response.code}")
                
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "API request failed with code ${response.code}: $errorBody")
                    
                    // Versuche, die Fehlermeldung aus dem JSON zu extrahieren
                    val errorMessage = try {
                        val jsonError = JSONObject(errorBody ?: "{}")
                        val error = jsonError.optJSONObject("error")
                        error?.optString("message") ?: "Unbekannter Fehler"
                    } catch (e: Exception) {
                        "Fehler bei der API-Anfrage: ${response.code}"
                    }
                    
                    // Wenn der Fehler auf einen ungültigen API-Schlüssel hinweist, gib eine spezifischere Fehlermeldung zurück
                    if (response.code == 401 || errorMessage.contains("key") || errorMessage.contains("auth")) {
                        return@withContext ChatMessage(
                            content = "Entschuldigung, der API-Schlüssel scheint ungültig zu sein. Bitte überprüfen Sie Ihren OpenAI API-Schlüssel.",
                            isUser = false
                        )
                    }
                    
                    return@withContext ChatMessage(
                        content = "Entschuldigung, es gab ein Problem bei der Kommunikation mit der KI: $errorMessage",
                        isUser = false
                    )
                }

                val responseBody = response.body?.string() ?: throw IOException("Empty response")
                Log.d(TAG, "API response: $responseBody")
                
                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.getJSONArray("choices")
                
                if (choices.length() > 0) {
                    val assistantMessage = choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    // Füge die Antwort zum Verlauf hinzu
                    messageHistory.add(Message("assistant", assistantMessage))
                    
                    // Begrenze den Verlauf auf die letzten 10 Nachrichten
                    if (messageHistory.size > 10) {
                        val systemMessage = messageHistory.first()
                        messageHistory.clear()
                        messageHistory.add(systemMessage)
                    }
                    
                    return@withContext ChatMessage(
                        content = assistantMessage,
                        isUser = false
                    )
                } else {
                    throw IOException("No choices in response")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "OpenAI API call failed", e)
            return@withContext ChatMessage(
                content = "Entschuldigung, es gab ein Problem bei der Kommunikation mit der KI. Bitte versuchen Sie es später noch einmal. Fehler: ${e.message}",
                isUser = false
            )
        }
    }

    private fun getFallbackResponse(input: String): ChatMessage {
        // Einfache, kurze Fallback-Antworten
        val fallbackResponses = mapOf(
            "produktivität" to "Setze klare Ziele und priorisiere wichtige Aufgaben.",
            "aufgabe" to "Priorisiere nach Wichtigkeit und Dringlichkeit.",
            "zeit" to "Teile Aufgaben in 25-Minuten-Blöcke ein.",
            "fokus" to "25 Minuten arbeiten, 5 Minuten Pause.",
            "motivation" to "Setze kleine, erreichbare Ziele.",
            "stress" to "Mache regelmäßige Pausen.",
            "planung" to "Plane die wichtigsten Aufgaben für morgen."
        )
        
        // Suche nach passenden Schlüsselwörtern in der Eingabe
        for ((keyword, response) in fallbackResponses) {
            if (input.lowercase().contains(keyword)) {
                return ChatMessage(content = response, isUser = false)
            }
        }
        
        // Standard-Fallback-Antwort, wenn kein Schlüsselwort gefunden wurde
        return ChatMessage(
            content = "Ich verstehe Ihre Anfrage. Wie kann ich Ihnen bei der Aufgabenverwaltung oder Zeitplanung helfen?",
            isUser = false
        )
    }

    suspend fun generateQuoteComment(quote: Quote): String {
        val prompt = """
            Analysiere folgendes Zitat und gib einen kurzen, motivierenden Kommentar dazu ab (max. 2 Sätze):
            "${quote.text}" - ${quote.author}
            
            Wichtige Regeln:
            1. Fokussiere dich auf praktische Anwendungen im Kontext von ${quote.category.displayName}
            2. Der Kommentar sollte inspirierend und handlungsorientiert sein
            3. STRENG VERBOTEN sind folgende Wörter und Themen:
               - Timer, Minuten, Zeit, Uhr
               - Fragen jeglicher Art
               - Aufforderungen zum Starten von Aktivitäten
            4. Antworte auf Deutsch
            5. Nur positive, motivierende Aussagen verwenden
            6. Maximal 2 kurze Sätze
        """.trimIndent()

        return try {
            val response = chat(prompt)
            // Zusätzliche Sicherheitsprüfung
            val forbiddenWords = listOf("timer", "minute", "zeit", "uhr", "?")
            var filteredText = response.content
            
            // Entferne Sätze, die verbotene Wörter enthalten
            forbiddenWords.forEach { word ->
                if (filteredText.lowercase().contains(word)) {
                    filteredText = "Dieser Moment bietet die perfekte Gelegenheit, aktiv zu werden und Ihre Ziele zu verfolgen."
                }
            }
            
            filteredText
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Generieren des Zitat-Kommentars", e)
            "Ein inspirierender Gedanke für mehr Erfolg."
        }
    }

    suspend fun transcribeAudio(audioFile: File): String? = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrEmpty()) {
            Log.e(TAG, "API key is not set")
            return@withContext null
        }

        try {
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file",
                    audioFile.name,
                    audioFile.readBytes().toRequestBody("audio/wav".toMediaTypeOrNull())
                )
                .addFormDataPart("model", "whisper-1")
                .addFormDataPart("language", "de")
                .addFormDataPart("response_format", "json")
                .build()

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/transcriptions")
                .addHeader("Authorization", "Bearer ${apiKey?.trim()}")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "Whisper API request failed: $errorBody")
                    return@withContext null
                }

                val responseBody = response.body?.string()
                val jsonResponse = JSONObject(responseBody)
                return@withContext jsonResponse.getString("text")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Whisper Transkription", e)
            return@withContext null
        }
    }

    suspend fun textToSpeech(text: String): ByteArray? = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrEmpty()) {
            Log.e(TAG, "API key is not set")
            return@withContext null
        }

        try {
            Log.d(TAG, "Starte TTS-Anfrage für Text: ${text.take(50)}...")
            
            val requestBody = JSONObject().apply {
                put("model", "tts-1-hd")
                put("input", text)
                put("voice", "nova")
                put("response_format", "mp3")
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/audio/speech")
                .addHeader("Authorization", "Bearer ${apiKey?.trim()}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            try {
                ttsClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        val errorBody = response.body?.string()
                        Log.e(TAG, "OpenAI TTS request failed: $errorBody")
                        return@withContext null
                    }

                    Log.d(TAG, "TTS-Anfrage erfolgreich")
                    return@withContext response.body?.bytes()
                }
            } catch (e: java.net.SocketTimeoutException) {
                // Bei Timeout versuchen wir es mit einem simpleren Modell
                Log.w(TAG, "TTS-Timeout aufgetreten, versuche alternatives Modell", e)
                
                val fallbackRequestBody = JSONObject().apply {
                    put("model", "tts-1") // Einfacheres Modell
                    put("input", text)
                    put("voice", "alloy") // Alternative Stimme
                    put("response_format", "mp3")
                }
                
                val fallbackRequest = Request.Builder()
                    .url("https://api.openai.com/v1/audio/speech")
                    .addHeader("Authorization", "Bearer ${apiKey?.trim()}")
                    .addHeader("Content-Type", "application/json")
                    .post(fallbackRequestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                    .build()
                
                try {
                    ttsClient.newCall(fallbackRequest).execute().use { response ->
                        if (!response.isSuccessful) {
                            val errorBody = response.body?.string()
                            Log.e(TAG, "Fallback TTS request failed: $errorBody")
                            return@withContext null
                        }
                        
                        Log.d(TAG, "Fallback TTS-Anfrage erfolgreich")
                        return@withContext response.body?.bytes()
                    }
                } catch (e2: Exception) {
                    Log.e(TAG, "Fehler bei Fallback-TTS-Anfrage", e2)
                    return@withContext null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei OpenAI TTS", e)
            return@withContext null
        }
    }

    suspend fun analyzeImage(file: File): String? {
        if (apiKey.isNullOrEmpty()) {
            Log.e(TAG, "API key is not set")
            return null
        }

        return try {
            val base64Image = android.util.Base64.encodeToString(
                file.readBytes(),
                android.util.Base64.NO_WRAP
            )

            val requestBody = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", JSONArray().apply {
                            put(JSONObject().apply {
                                put("type", "text")
                                put("text", "Beschreibe dieses Bild kurz und prägnant auf Deutsch.")
                            })
                            put(JSONObject().apply {
                                put("type", "image_url")
                                put("image_url", JSONObject().apply {
                                    put("url", "data:image/jpeg;base64,$base64Image")
                                })
                            })
                        })
                    })
                })
                put("max_tokens", 300)
                put("temperature", 0.7)
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${apiKey?.trim()}")
                .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            Log.d(TAG, "Sending Vision API request...")
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "OpenAI Vision API request failed: $errorBody")
                    return "Entschuldigung, ich konnte das Bild nicht analysieren. Bitte versuchen Sie es später erneut."
                }

                val responseBody = response.body?.string()
                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.getJSONArray("choices")
                
                if (choices.length() > 0) {
                    choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                } else {
                    "Entschuldigung, ich konnte keine Analyse des Bildes erstellen."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Bildanalyse", e)
            "Es tut mir leid, bei der Bildanalyse ist ein Fehler aufgetreten: ${e.message}"
        }
    }

    suspend fun analyzeFile(fileName: String, content: String): String? {
        if (apiKey.isNullOrEmpty()) {
            Log.e(TAG, "API key is not set")
            return null
        }

        return try {
            val requestBody = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", "Analysiere bitte den folgenden Text aus der Datei '$fileName' und erstelle eine kurze, prägnante Zusammenfassung auf Deutsch:\n\n$content")
                    })
                })
                put("max_tokens", 500)
                put("temperature", 0.7)
            }

            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${apiKey?.trim()}")
                .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "OpenAI API request failed: $errorBody")
                    return "Entschuldigung, ich konnte die Datei nicht analysieren. Bitte versuchen Sie es später erneut."
                }

                val responseBody = response.body?.string()
                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.getJSONArray("choices")
                
                if (choices.length() > 0) {
                    val summary = choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    "📄 PDF-Analyse abgeschlossen (${fileName})\n\n$summary\n\nMöchten Sie:\n1. Diese Zusammenfassung als Aufgabe speichern?\n2. Die Analyse teilen?\n3. Spezifische Fragen zum Inhalt stellen?"
                } else {
                    "Entschuldigung, ich konnte keine Analyse der Datei erstellen."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Dateianalyse", e)
            "Entschuldigung, bei der Analyse der Datei ist ein Fehler aufgetreten: ${e.message}"
        }
    }

    fun setApiKey(key: String) {
        customApiKey = key.takeIf { it.isNotEmpty() }
    }

    fun getApiKey(): String {
        return customApiKey ?: apiKey ?: throw IllegalStateException("Kein API-Key verfügbar")
    }

    fun hasCustomApiKey(): Boolean {
        return customApiKey != null
    }

    // Methode für die Ideensammlung
    suspend fun generateChatResponse(prompt: String): String? = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrEmpty()) {
            Log.e(TAG, "API key is not set")
            return@withContext "Entschuldigung, der API-Schlüssel ist nicht konfiguriert."
        }

        try {
            // Bereite den Request-Body vor
            val requestBody = JSONObject().apply {
                put("model", "gpt-4o-mini")
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system") 
                        put("content", "Du bist ein kreativer Assistent, der innovative und praktische Ideen generiert.")
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.8)
                put("max_tokens", 800)
            }
            
            // Erstelle den Request mit dem korrekten API-Schlüssel
            val request = Request.Builder()
                .url("https://api.openai.com/v1/chat/completions")
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer ${apiKey?.trim()}")
                .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            Log.d(TAG, "Sende Anfrage zur Ideengenerierung an OpenAI API...")
            
            // Führe den Request aus
            client.newCall(request).execute().use { response ->
                Log.d(TAG, "Antwort von OpenAI API erhalten mit Code: ${response.code}")
                
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()
                    Log.e(TAG, "API request failed with code ${response.code}: $errorBody")
                    return@withContext "Entschuldigung, bei der Ideengenerierung ist ein Fehler aufgetreten."
                }

                val responseBody = response.body?.string() ?: throw IOException("Empty response")
                
                val jsonResponse = JSONObject(responseBody)
                val choices = jsonResponse.getJSONArray("choices")
                
                if (choices.length() > 0) {
                    val generatedIdeas = choices.getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    
                    return@withContext generatedIdeas
                } else {
                    throw IOException("No choices in response")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Ideengenerierung", e)
            return@withContext "Entschuldigung, bei der Ideengenerierung ist ein Fehler aufgetreten: ${e.message}"
        }
    }
} 