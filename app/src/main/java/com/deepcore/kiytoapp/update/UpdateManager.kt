package com.deepcore.kiytoapp.update

import android.content.Context
import com.deepcore.kiytoapp.BuildConfig
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.util.*

class UpdateManager(private val context: Context) {
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com/repos/Icarus-B4/Fokus-Kiyto_KI"
        private const val PREFS_NAME = "update_prefs"
        private const val LAST_CHECK_KEY = "last_update_check"
        private const val UPDATE_STATUS_KEY = "update_status"
    }

    var latestVersion: String? = null
        private set
    var updateDescription: String? = null
        private set
    var updateUrl: String? = null
        private set
    var updateAvailable: Boolean = false
        private set

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun checkForUpdates(): Boolean = withContext(Dispatchers.IO) {
        try {
            LogUtils.debug(this@UpdateManager, "Prüfe auf Updates...")
            val url = URL("$GITHUB_API_BASE/releases/latest")
            val connection = url.openConnection()
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            
            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            val release = JSONObject(response)
            
            latestVersion = release.getString("tag_name").removePrefix("v")
            updateDescription = release.getString("body")
            updateUrl = release.getString("html_url")
            
            updateAvailable = compareVersions(BuildConfig.VERSION_NAME, latestVersion ?: "") < 0
            saveUpdateStatus(Date())
            
            LogUtils.debug(this@UpdateManager, "Update-Check abgeschlossen. Verfügbar: $updateAvailable")
            return@withContext updateAvailable
            
        } catch (e: Exception) {
            LogUtils.error(this@UpdateManager, "Fehler beim Prüfen auf Updates", e)
            throw e
        }
    }

    private fun compareVersions(version1: String, version2: String): Int {
        val v1Parts = version1.split(".")
        val v2Parts = version2.split(".")
        
        for (i in 0 until maxOf(v1Parts.size, v2Parts.size)) {
            val v1 = v1Parts.getOrNull(i)?.toIntOrNull() ?: 0
            val v2 = v2Parts.getOrNull(i)?.toIntOrNull() ?: 0
            
            when {
                v1 < v2 -> return -1
                v1 > v2 -> return 1
            }
        }
        return 0
    }

    private fun saveUpdateStatus(checkDate: Date) {
        prefs.edit().apply {
            putLong(LAST_CHECK_KEY, checkDate.time)
            putBoolean(UPDATE_STATUS_KEY, updateAvailable)
            apply()
        }
    }

    fun getLastCheckDate(): Date? {
        val timestamp = prefs.getLong(LAST_CHECK_KEY, 0)
        return if (timestamp > 0) Date(timestamp) else null
    }

    fun getUpdateStatus(): Boolean {
        return prefs.getBoolean(UPDATE_STATUS_KEY, false)
    }
} 