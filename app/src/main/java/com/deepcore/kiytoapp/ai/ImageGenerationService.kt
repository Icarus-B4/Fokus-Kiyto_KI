package com.deepcore.kiytoapp.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.deepcore.kiytoapp.ai.APISettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class ImageGenerationService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    private val apiSettingsManager = APISettingsManager(context)
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateImage(prompt: String, size: String = "1024x1024"): File? {
        return withContext(Dispatchers.IO) {
            try {
                val apiKey = apiSettingsManager.getApiKey()
                if (apiKey.isNullOrEmpty()) {
                    Log.e(TAG, "API Key nicht gefunden")
                    return@withContext null
                }

                val requestBody = JSONObject().apply {
                    put("prompt", prompt)
                    put("n", 1)
                    put("size", size)
                    put("response_format", "url")
                }.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("https://api.openai.com/v1/images/generations")
                    .addHeader("Authorization", "Bearer $apiKey")
                    .post(requestBody)
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Fehler bei der Bildgenerierung: ${response.code}")
                        return@withContext null
                    }

                    val jsonResponse = JSONObject(response.body?.string() ?: "")
                    val imageUrl = jsonResponse.getJSONArray("data")
                        .getJSONObject(0)
                        .getString("url")

                    // Bild herunterladen
                    val imageRequest = Request.Builder()
                        .url(imageUrl)
                        .build()

                    client.newCall(imageRequest).execute().use { imageResponse ->
                        if (!imageResponse.isSuccessful) {
                            Log.e(TAG, "Fehler beim Herunterladen des Bildes: ${imageResponse.code}")
                            return@withContext null
                        }

                        val bitmap = BitmapFactory.decodeStream(imageResponse.body?.byteStream())
                        if (bitmap == null) {
                            Log.e(TAG, "Fehler beim Dekodieren des Bildes")
                            return@withContext null
                        }

                        val file = File(context.cacheDir, "generated_image_${System.currentTimeMillis()}.jpg")
                        try {
                            FileOutputStream(file).use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out)
                                out.flush()
                            }
                            Log.d(TAG, "Bild erfolgreich gespeichert: ${file.absolutePath}")
                            Log.d(TAG, "Bildgröße: ${file.length()} Bytes")
                            Log.d(TAG, "Bild existiert: ${file.exists()}")
                            Log.d(TAG, "Bild URI: ${Uri.fromFile(file)}")
                            
                            return@withContext file
                        } catch (e: Exception) {
                            Log.e(TAG, "Fehler beim Speichern des Bildes", e)
                            return@withContext null
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei der Bildgenerierung", e)
                return@withContext null
            }
        }
    }

    companion object {
        private const val TAG = "ImageGenerationService"
    }
} 