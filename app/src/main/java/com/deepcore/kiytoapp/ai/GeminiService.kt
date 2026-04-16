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
                modelName = "gemini-1.5-flash",
                apiKey = apiKey!!
            )
            
            visionModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
                apiKey = apiKey!!
            )

            voiceModel = GenerativeModel(
                modelName = "gemini-1.5-flash",
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
            
            // Wir nutzen REST für Transkription, da das SDK bei gRPC oft 404 liefert
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            
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
                                put("text", "Transkribiere dieses Audio präzise auf Deutsch. Gib NUR den transkribierten Text zurück.")
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
                if (!response.isSuccessful) {
                    val err = response.body?.string()
                    Log.e(TAG, "Gemini Transcribe API Fehler: ${response.code} $err")
                    return@withContext null
                }

                val jsonResponse = JSONObject(response.body?.string() ?: "")
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
            null // Keinen hässlichen Fehler-String in den Chat schreiben
        }
    }

    suspend fun generateGeminiSpeech(text: String): ByteArray? = withContext(Dispatchers.IO) {
        if (apiKey.isNullOrEmpty()) return@withContext null
        
        try {
            // Wir nutzen hier den direkten REST-Call, um responseModalities: ["AUDIO"] zu setzen
            // Das ist der Weg, um die "schöne Stimme" nativ aus dem Modell zu bekommen.
            // Nutze gemini-1.5-flash für bessere API-Kompatibilität in v1beta
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=$apiKey"
            
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
                    put("responseModalities", JSONArray().apply { put("AUDIO") })
                    put("speechConfig", JSONObject().apply {
                        put("voiceConfig", JSONObject().apply {
                            put("prebuiltVoiceConfig", JSONObject().apply {
                                put("voiceName", "Aoede") // Aoede ist eine sehr natürliche Stimme
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
                if (!response.isSuccessful) {
                    Log.e(TAG, "Gemini Speech API Fehler: ${response.code} ${response.body?.string()}")
                    return@withContext null
                }

                val jsonResponse = JSONObject(response.body?.string() ?: "")
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
