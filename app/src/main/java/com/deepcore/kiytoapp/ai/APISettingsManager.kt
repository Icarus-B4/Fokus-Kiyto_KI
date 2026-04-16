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
        private const val KEY_API_KEY = "openai_api_key"
        private const val KEY_USE_CUSTOM_KEY = "use_custom_key"
    }

    fun saveApiKey(apiKey: String) {
        try {
            encryptedPrefs.edit()
                .putString(KEY_API_KEY, apiKey)
                .putBoolean(KEY_USE_CUSTOM_KEY, true)
                .apply()
            Log.d(TAG, "API-Key erfolgreich gespeichert")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Speichern des API-Keys", e)
            throw e
        }
    }

    fun getApiKey(): String? {
        return try {
            encryptedPrefs.getString(KEY_API_KEY, null)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Abrufen des API-Keys", e)
            null
        }
    }

    fun useCustomKey(): Boolean {
        return encryptedPrefs.getBoolean(KEY_USE_CUSTOM_KEY, false)
    }

    fun resetToDefaultKey() {
        try {
            encryptedPrefs.edit()
                .remove(KEY_API_KEY)
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
        
        // Überprüfe die bekannten Präfixe, aber mache es nicht zur Pflicht
        val hasValidPrefix = trimmedKey.startsWith("sk-") || 
                           trimmedKey.startsWith("sk-proj-") ||
                           trimmedKey.startsWith("sk_test_")
        
        if (!hasValidPrefix) {
            Log.d(TAG, "API-Key hat kein bekanntes Präfix, wird aber akzeptiert")
        }
        
        return true
    }
} 