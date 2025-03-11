package com.deepcore.kiytoapp.update

import android.content.Context
import android.content.SharedPreferences
import com.deepcore.kiytoapp.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL

class UpdateManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    companion object {
        private const val PREFS_NAME = "update_settings"
        private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
        private const val KEY_LATEST_VERSION = "latest_version"
        private const val KEY_UPDATE_AVAILABLE = "update_available"
        private const val KEY_UPDATE_DESCRIPTION = "update_description"
        private const val KEY_UPDATE_URL = "update_url"
    }

    var lastUpdateCheck: Long
        get() = prefs.getLong(KEY_LAST_UPDATE_CHECK, 0)
        private set(value) = prefs.edit().putLong(KEY_LAST_UPDATE_CHECK, value).apply()

    var latestVersion: String?
        get() = prefs.getString(KEY_LATEST_VERSION, null)
        private set(value) = prefs.edit().putString(KEY_LATEST_VERSION, value).apply()

    var updateAvailable: Boolean
        get() = prefs.getBoolean(KEY_UPDATE_AVAILABLE, false)
        private set(value) = prefs.edit().putBoolean(KEY_UPDATE_AVAILABLE, value).apply()

    var updateDescription: String?
        get() = prefs.getString(KEY_UPDATE_DESCRIPTION, null)
        private set(value) = prefs.edit().putString(KEY_UPDATE_DESCRIPTION, value).apply()

    var updateUrl: String?
        get() = prefs.getString(KEY_UPDATE_URL, null)
        private set(value) = prefs.edit().putString(KEY_UPDATE_URL, value).apply()

    suspend fun checkForUpdates(currentVersion: String, repoUrl: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("$repoUrl/releases/latest")
                val connection = url.openConnection()
                val response = connection.getInputStream().bufferedReader().use { it.readText() }
                val jsonResponse = JSONObject(response)

                val latestVersion = jsonResponse.getString("tag_name")
                val description = jsonResponse.getString("body")
                val downloadUrl = jsonResponse.getString("html_url")

                this@UpdateManager.latestVersion = latestVersion
                this@UpdateManager.updateDescription = description
                this@UpdateManager.updateUrl = downloadUrl
                this@UpdateManager.lastUpdateCheck = System.currentTimeMillis()

                val hasUpdate = latestVersion != currentVersion
                this@UpdateManager.updateAvailable = hasUpdate

                LogUtils.debug(this, "Update-Check durchgeführt: Version $latestVersion verfügbar")
                hasUpdate
            } catch (e: Exception) {
                LogUtils.error(this, "Fehler beim Prüfen auf Updates", e)
                false
            }
        }
    }

    fun clearUpdateStatus() {
        prefs.edit().apply {
            remove(KEY_LATEST_VERSION)
            remove(KEY_UPDATE_AVAILABLE)
            remove(KEY_UPDATE_DESCRIPTION)
            remove(KEY_UPDATE_URL)
            apply()
        }
        LogUtils.debug(this, "Update-Status zurückgesetzt")
    }
} 