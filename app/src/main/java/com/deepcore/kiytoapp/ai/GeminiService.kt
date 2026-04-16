package com.deepcore.kiytoapp.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.deepcore.kiytoapp.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.json.JSONArray
import com.google.ai.client.generativeai.type.*
import com.deepcore.kiytoapp.services.YouTubeService

object GeminiService {
    private const val TAG = "GeminiService"
    private const val TEXT_MODEL_ID = "gemini-1.5-flash-latest"
    private const val VOICE_MODEL_ID = "gemini-3.1-flash-tts-preview"
    
    private var model: GenerativeModel? = null
    private var visionModel: GenerativeModel? = null
    private var voiceModel: GenerativeModel? = null // Für Audio-Input/Output
    private var apiKey: String? = null
    private val client = OkHttpClient()

    fun initialize(context: Context) {
        val settingsManager = APISettingsManager(context)
        apiKey = settingsManager.getGeminiApiKey()
        
        if (apiKey.isNullOrEmpty()) {
            Log.w(TAG, "Kein Gemini API-Key gefunden")
            return
        }

        try {
            model = GenerativeModel(
                modelName = TEXT_MODEL_ID,
                apiKey = apiKey!!
            )
            
            visionModel = GenerativeModel(
                modelName = TEXT_MODEL_ID,
                apiKey = apiKey!!
            )

            voiceModel = GenerativeModel(
                modelName = TEXT_MODEL_ID,
                apiKey = apiKey!!
            )
            Log.d(TAG, "GeminiService erfolgreich initialisiert")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Initialisierung von Gemini", e)
        }
    }

    suspend fun chat(input: String): ChatMessage? = withContext(Dispatchers.IO) {
        if (model == null) return@withContext null

        try {
            val response = model?.generateContent(input)
            val text = response?.text ?: return@withContext null
            
            ChatMessage(
                content = text,
                isUser = false
            )
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Chat Fehler", e)
            null
        }
    }

    suspend fun analyzeImage(file: File): String? = withContext(Dispatchers.IO) {
        if (visionModel == null) return@withContext null

        try {
            val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@withContext null
            
            val response = visionModel?.generateContent(
                content {
                    image(bitmap)
                    text("Beschreibe dieses Bild kurz und prägnant auf Deutsch.")
                }
            )
            
            response?.text
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Bildanalyse Fehler", e)
            null
        }
    }

    suspend fun transcribeAudio(file: File): String? = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrEmpty()) return@withContext null
        
        try {
            val audioBytes = file.readBytes()
            val base64Audio = android.util.Base64.encodeToString(audioBytes, android.util.Base64.NO_WRAP)
            
            // Nutze die neue TEXT_MODEL_ID mit -latest Suffix für v1beta
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$TEXT_MODEL_ID:generateContent?key=$apiKey"
            
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("inlineData", JSONObject().apply {
                                    put("mimeType", "audio/wav")
                                    put("data", base64Audio)
                                })
                            })
                            put(JSONObject().apply {
                                put("text", "Transkribiere dieses Audio präzise auf Deutsch. Gib NUR den transkribierten Text zurück, ohne Kommentare oder Formatierungen. Wenn nichts zu hören ist, gib einen leeren String zurück.")
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini Transcribe API Fehler (Code ${response.code}): $responseBody")
                    // Falls wir einen 404 bekommen, loggen wir das spezifisch für den Nutzer
                    if (response.code == 404) {
                        Log.e(TAG, "KRITISCH: Modell nicht gefunden oder Dienst am Endpoint nicht verfügbar. Prüfe API-Key Berechtigungen.")
                    }
                    return@withContext "FEHLER: API-Fehler ${response.code}"
                }

                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                val text = candidates?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")

                text?.trim()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Transkription Fehler", e)
            "FEHLER: ${e.message}"
        }
    }

    suspend fun generateGeminiSpeech(text: String): ByteArray? = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrEmpty()) return@withContext null
        
        try {
            // Wir nutzen hier den direkten REST-Call, um responseModalities: ["audio"] zu setzen
            // Nutze das brandneue gemini-3.1-flash-tts-preview Modell für beste Audio-Qualität
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$VOICE_MODEL_ID:generateContent?key=$apiKey"
            
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", text)
                            })
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("responseModalities", JSONArray().apply { put("audio") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede") // Aoede ist eine sehr natürliche, emotionale Stimme
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini Speech API Fehler (Code ${response.code}): $responseBody")
                    return@withContext null
                }

                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                
                // Wir suchen den Part mit den inlineData (Audio)
                for (i in 0 until (parts?.length() ?: 0)) {
                    val part = parts?.optJSONObject(i)
                    val inlineData = part?.optJSONObject("inlineData")
                    if (inlineData != null) {
                        val base64Audio = inlineData.optString("data")
                        return@withContext android.util.Base64.decode(base64Audio, android.util.Base64.DEFAULT)
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Speech Generierung fehlgeschlagen", e)
            null
        }
    }

    suspend fun testApiConnection(): Pair<Boolean, String> = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrEmpty()) return@withContext false to "API-Key ist leer oder nicht konfiguriert."
        
        try {
            val url = "https://generativelanguage.googleapis.com/v1beta/models/$TEXT_MODEL_ID:generateContent?key=$apiKey"
            val requestBody = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply { put("text", "Ping") })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(url)
                .post(requestBody.toString().toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: ""
                if (response.isSuccessful) {
                    true to "Erfolgreich verbunden! Die KI hat geantwortet."
                } else {
                    false to "Fehler ${response.code}: $body"
                }
            }
        } catch (e: Exception) {
            false to "Verbindungsfehler: ${e.message}"
        }
    }

    suspend fun analyzeFile(fileName: String, content: String): String? = withContext(Dispatchers.IO) {
        if (model == null) return@withContext null
        
        try {
            val prompt = "Analysiere diese Datei ($fileName) und gib mir eine Zusammenfassung oder beantworte Fragen dazu:\n\n$content"
            val response = model?.generateContent(prompt)
            response?.text
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Dateianalyse Fehler", e)
            null
        }
    }

    suspend fun generateVideoSummary(videoInfo: YouTubeService.VideoInfo): String = withContext(Dispatchers.IO) {
        if (model == null) return@withContext "KI-Dienst nicht initialisiert."
        
        try {
            val prompt = """
                Erstelle eine prägnante Zusammenfassung für dieses YouTube-Video auf Deutsch:
                Titel: ${videoInfo.title}
                Dauer: ${videoInfo.duration}
                Beschreibung: ${videoInfo.description}
                
                Die Zusammenfassung sollte die wichtigsten Punkte hervorheben und in einem hilfreichen Ton verfasst sein.
            """.trimIndent()
            
            val response = model?.generateContent(prompt)
            response?.text ?: "Entschuldigung, ich konnte keine Zusammenfassung generieren."
        } catch (e: Exception) {
            Log.e(TAG, "Gemini Video-Zusammenfassung Fehler", e)
            "Fehler bei der Generierung der Video-Zusammenfassung."
        }
    }

    fun isEnabled(): Boolean = apiKey != null && model != null
}
