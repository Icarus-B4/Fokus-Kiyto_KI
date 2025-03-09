package com.deepcore.kiytoapp.auth

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import java.security.MessageDigest

class AuthManager(context: Context) {
    private val TAG = "AuthManager"
    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    
    private val encryptedPrefs = EncryptedSharedPreferences.create(
        "auth_settings",
        masterKeyAlias,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_USER_DATA = "user_data"
        private const val KEY_IS_LOGGED_IN = "is_logged_in"
    }

    fun register(email: String, password: String): Boolean {
        return try {
            val hashedPassword = hashPassword(password)
            val user = User(email, hashedPassword)
            
            encryptedPrefs.edit()
                .putString(KEY_USER_DATA, Gson().toJson(user))
                .apply()
            
            Log.d(TAG, "Benutzer erfolgreich registriert: $email")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Registrierung", e)
            false
        }
    }

    fun login(email: String, password: String): Boolean {
        return try {
            val userData = encryptedPrefs.getString(KEY_USER_DATA, null)
            if (userData == null) {
                Log.d(TAG, "Keine Benutzerdaten gefunden")
                return false
            }

            val user = Gson().fromJson(userData, User::class.java)
            val hashedPassword = hashPassword(password)

            if (user.email == email && user.passwordHash == hashedPassword) {
                encryptedPrefs.edit()
                    .putBoolean(KEY_IS_LOGGED_IN, true)
                    .apply()
                Log.d(TAG, "Login erfolgreich: $email")
                true
            } else {
                Log.d(TAG, "Ungültige Anmeldedaten")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Login", e)
            false
        }
    }

    fun logout() {
        encryptedPrefs.edit()
            .putBoolean(KEY_IS_LOGGED_IN, false)
            .apply()
        Log.d(TAG, "Benutzer ausgeloggt")
    }

    fun isLoggedIn(): Boolean {
        return encryptedPrefs.getBoolean(KEY_IS_LOGGED_IN, false)
    }

    fun getCurrentUser(): User? {
        val userData = encryptedPrefs.getString(KEY_USER_DATA, null)
        return if (userData != null) {
            Gson().fromJson(userData, User::class.java)
        } else null
    }

    private fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }

    data class User(
        val email: String,
        val passwordHash: String
    )
} 