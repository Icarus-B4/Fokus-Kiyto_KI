package com.deepcore.kiytoapp.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.deepcore.kiytoapp.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object GeminiService {
    private const val TAG = "GeminiService"
    private var model: GenerativeModel? = null
    private var visionModel: GenerativeModel? = null
    private var apiKey: String? = null

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

    fun isEnabled(): Boolean = apiKey != null && model != null
}
