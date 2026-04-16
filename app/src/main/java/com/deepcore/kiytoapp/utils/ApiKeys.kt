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

    fun initializeApiKeys(context: Context) {
        Log.d(TAG, "Initialisiere API-Schlüssel...")
        // Nur YouTube Initialisierung falls nötig
    }
} 