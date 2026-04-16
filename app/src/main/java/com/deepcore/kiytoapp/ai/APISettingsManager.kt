package com.deepcore.kiytoapp.ai

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class APISettingsManager(context: Context) {
    private val TAG = "APISettingsManager"
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        "api_settings",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
        private const val KEY_USE_CUSTOM_KEY = "use_custom_key"
    }

    fun saveGeminiApiKey(apiKey: String) {
        try {
            encryptedPrefs.edit()
                .putString(KEY_GEMINI_API_KEY, apiKey)
                .putBoolean(KEY_USE_CUSTOM_KEY, true)
                .apply()
            Log.d(TAG, "Gemini API-Key erfolgreich gespeichert")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Speichern des Gemini API-Keys", e)
            throw e
        }
    }

    fun getGeminiApiKey(): String? {
        return try {
            encryptedPrefs.getString(KEY_GEMINI_API_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Abrufen des Gemini API-Keys", e)
            null
        }
    }

    fun useCustomKey(): Boolean {
        return encryptedPrefs.getBoolean(KEY_USE_CUSTOM_KEY, false)
    }

    fun resetToDefaultKey() {
        try {
            encryptedPrefs.edit()
                .remove(KEY_GEMINI_API_KEY)
                .putBoolean(KEY_USE_CUSTOM_KEY, false)
                .apply()
            Log.d(TAG, "API-Einstellungen zurückgesetzt")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Zurücksetzen der API-Einstellungen", e)
            throw e
        }
    }

    fun validateApiKey(apiKey: String): Boolean {
        val trimmedKey = apiKey.trim()
        
        // Grundlegende Prüfung auf Mindestlänge (unterstützt nun auch kürzere/temporäre Keys)
        if (trimmedKey.length < 8) {
            Log.d(TAG, "API-Key zu kurz: ${trimmedKey.length} Zeichen")
            return false
        }
        
        // Überprüfe das Format (erlaubt Buchstaben, Zahlen, Unterstriche, Bindestriche und Punkte)
        val validFormat = trimmedKey.matches(Regex("^[A-Za-z0-9_\\-.]+$"))
        if (!validFormat) {
            Log.d(TAG, "API-Key enthält ungültige Zeichen")
            return false
        }
        
        return true
    }
} 