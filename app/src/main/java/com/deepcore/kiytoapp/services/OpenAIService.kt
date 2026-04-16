package com.deepcore.kiytoapp.services

import android.content.Context
import android.util.Log
import com.aallam.openai.api.chat.ChatCompletion
import com.aallam.openai.api.chat.ChatCompletionRequest
import com.aallam.openai.api.chat.ChatMessage
import com.aallam.openai.api.chat.ChatRole
import com.aallam.openai.api.model.ModelId
import com.aallam.openai.client.OpenAI
import com.aallam.openai.client.OpenAIConfig
import com.deepcore.kiytoapp.utils.ApiKeys
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

class OpenAIService(private val context: Context) {
    companion object {
        private const val TAG = "OpenAIService"
        private const val TIMEOUT_SECONDS = 30L
    }

    private fun validateApiKey(apiKey: String?): String {
        if (apiKey == null) {
            Log.e(TAG, "API-Schlüssel ist null")
            throw IllegalStateException("OpenAI API-Schlüssel nicht gefunden")
        }

        // Entferne alle möglicherweise problematischen Zeichen
        val cleanKey = apiKey.trim()
            .replace("\"", "") // Standard-Anführungszeichen
            .replace(""", "") // Typografische Anführungszeichen
            .replace(""", "") // Typografische Anführungszeichen
            .replace("'", "") // Standard-Apostroph
            .replace("'", "") // Typografisches Apostroph
            .replace("'", "") // Typografisches Apostroph
            .replace(Regex("[\\s\\n\\r]+"), "") // Entferne Whitespace

        Log.d(TAG, "API-Schlüssel Länge nach Bereinigung: ${cleanKey.length}")

        if (cleanKey.length < 20) {
            Log.e(TAG, "API-Schlüssel ist zu kurz: ${cleanKey.length} Zeichen")
            throw IllegalStateException("OpenAI API-Schlüssel ist ungültig")
        }

        // Stelle sicher, dass der Bearer-Token korrekt formatiert ist
        return if (!cleanKey.startsWith("Bearer ")) {
            "Bearer $cleanKey"
        } else {
            cleanKey
        }
    }

    private val openAI by lazy {
        Log.d(TAG, "Initialisiere OpenAI Client...")
        val apiKey = validateApiKey(ApiKeys.getOpenAIApiKey(context))
        Log.d(TAG, "OpenAI API-Schlüssel validiert, erstelle Client...")
        
        val config = OpenAIConfig(
            token = apiKey.removePrefix("Bearer "),
            httpClientConfig = {
                install(HttpTimeout) {
                    requestTimeoutMillis = TIMEOUT_SECONDS * 1000
                    connectTimeoutMillis = 15000  // 15 Sekunden Connection Timeout
                    socketTimeoutMillis = TIMEOUT_SECONDS * 1000
                }
            }
        )
        OpenAI(config)
    }

    suspend fun generateVideoSummary(videoInfo: YouTubeService.VideoInfo): String {
        Log.d(TAG, "Generiere Zusammenfassung für Video: ${videoInfo.title}")
        
        val prompt = """
            Fasse das folgende YouTube-Video zusammen:
            
            Titel: ${videoInfo.title}
            Beschreibung: ${videoInfo.description}
            Dauer: ${videoInfo.duration}
            
            Bitte erstelle eine kurze, prägnante Zusammenfassung der wichtigsten Punkte.
            Die Zusammenfassung sollte folgende Aspekte enthalten:
            1. Hauptthema
            2. Wichtigste Erkenntnisse
            3. Kernaussagen
            
            Formatiere die Zusammenfassung in leicht lesbaren Absätzen.
        """.trimIndent()

        val chatCompletionRequest = ChatCompletionRequest(
            model = ModelId("gpt-3.5-turbo"),
            messages = listOf(
                ChatMessage(
                    role = ChatRole.System,
                    content = "Du bist ein hilfreicher Assistent, der YouTube-Videos zusammenfasst."
                ),
                ChatMessage(
                    role = ChatRole.User,
                    content = prompt
                )
            )
        )

        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Sende Anfrage an OpenAI API...")
                withTimeout(TIMEOUT_SECONDS.seconds) {
                    val completion: ChatCompletion = openAI.chatCompletion(chatCompletionRequest)
                    val result = completion.choices.first().message.content
                    Log.d(TAG, "Zusammenfassung erfolgreich generiert")
                    result
                }
            } catch (e: Exception) {
                val errorMessage = when (e) {
                    is kotlinx.coroutines.TimeoutCancellationException -> "Zeitüberschreitung bei der API-Anfrage"
                    else -> "Fehler bei der API-Anfrage: ${e.message}"
                }
                Log.e(TAG, errorMessage, e)
                Log.e(TAG, "Stack Trace:", e)
                throw IllegalStateException(errorMessage)
            }.toString()
        }
    }
} 