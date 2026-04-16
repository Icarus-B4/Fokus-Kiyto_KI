package com.deepcore.kiytoapp.utils

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.deepcore.kiytoapp.BuildConfig

object ApiKeys {
    private const val TAG = "ApiKeys"
    private const val PREFS_NAME = "encrypted_api_keys"
    private const val YOUTUBE_KEY = "youtube_api_key"
    private const val OPENAI_KEY = "openai_api_key"

    private fun getEncryptedPrefs(context: Context) = EncryptedSharedPreferences.create(
        PREFS_NAME,
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveYouTubeApiKey(context: Context, apiKey: String) {
        getEncryptedPrefs(context).edit().putString(YOUTUBE_KEY, apiKey).apply()
    }

    fun getYouTubeApiKey(context: Context): String? {
        return getEncryptedPrefs(context).getString(YOUTUBE_KEY, null)
    }

    private fun sanitizeApiKey(apiKey: String): String {
        return apiKey
            .trim()
            .replace(Regex("[\"\")]"), "") // Alle Arten von Anführungszeichen
            .replace(Regex("[''']"), "") // Alle Arten von Apostrophen
            .replace("Bearer ", "") // Entferne Bearer-Prefix falls vorhanden
            .replace(Regex("[\\s\\n\\r]+"), "") // Entfernt alle Whitespace-Zeichen
            .filter { it.code < 128 } // Nur ASCII-Zeichen zulassen
            .let { key ->
                // Protokolliere die Bytes für Debugging
                val bytes = key.toByteArray()
                Log.d(TAG, "API-Schlüssel Bytes: ${bytes.joinToString(", ") { it.toString(16) }}")
                key
            }
    }

    fun saveOpenAIApiKey(context: Context, apiKey: String) {
        val sanitizedKey = sanitizeApiKey(apiKey)
        Log.d(TAG, "Speichere OpenAI API-Schlüssel: ${sanitizedKey.take(5)}...")
        Log.d(TAG, "API-Schlüssel Länge: ${sanitizedKey.length}")
        getEncryptedPrefs(context).edit().putString(OPENAI_KEY, sanitizedKey).apply()
    }

    fun getOpenAIApiKey(context: Context): String? {
        // Versuche zuerst den gespeicherten Schlüssel zu laden
        val savedKey = getEncryptedPrefs(context).getString(OPENAI_KEY, null)
        Log.d(TAG, "Gespeicherter OpenAI API-Schlüssel: ${if (savedKey != null) "gefunden" else "nicht gefunden"}")
        
        if (!savedKey.isNullOrEmpty()) {
            val sanitizedKey = sanitizeApiKey(savedKey)
            Log.d(TAG, "Gespeicherter API-Schlüssel Länge: ${sanitizedKey.length}")
            if (sanitizedKey.length >= 20) {
                Log.d(TAG, "Gespeicherter API-Schlüssel akzeptiert")
                return sanitizedKey
            } else {
                Log.w(TAG, "Gespeicherter API-Schlüssel zu kurz (${sanitizedKey.length} Zeichen)")
            }
        }

        // Wenn kein Schlüssel gespeichert ist, versuche den BuildConfig-Schlüssel
        val buildConfigKey = sanitizeApiKey(BuildConfig.OPENAI_API_KEY)
        Log.d(TAG, "BuildConfig OpenAI API-Schlüssel: ${if (buildConfigKey.isNotEmpty()) "gefunden" else "leer"}")
        
        if (buildConfigKey.isNotEmpty()) {
            Log.d(TAG, "BuildConfig API-Schlüssel Länge: ${buildConfigKey.length}")
            if (buildConfigKey.length >= 20) {
                Log.d(TAG, "BuildConfig API-Schlüssel akzeptiert")
                saveOpenAIApiKey(context, buildConfigKey)
                return buildConfigKey
            } else {
                Log.w(TAG, "BuildConfig API-Schlüssel zu kurz (${buildConfigKey.length} Zeichen)")
            }
        }

        Log.w(TAG, "Kein gültiger OpenAI API-Schlüssel gefunden!")
        return null
    }

    fun initializeApiKeys(context: Context) {
        Log.d(TAG, "Initialisiere API-Schlüssel...")
        try {
            // Initialisiere OpenAI API-Schlüssel aus BuildConfig, wenn noch nicht gespeichert
            val buildConfigKey = BuildConfig.OPENAI_API_KEY
            Log.d(TAG, "BuildConfig OpenAI API-Schlüssel vorhanden: ${buildConfigKey.isNotEmpty()}")
            
            val existingKey = getOpenAIApiKey(context)
            Log.d(TAG, "Existierender OpenAI API-Schlüssel: ${if (existingKey != null) "vorhanden" else "nicht vorhanden"}")
            
            if (buildConfigKey.isNotEmpty() && existingKey == null) {
                Log.d(TAG, "Speichere BuildConfig OpenAI API-Schlüssel...")
                saveOpenAIApiKey(context, buildConfigKey)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Initialisieren der API-Schlüssel", e)
        }
    }
} 