package com.deepcore.kiytoapp.update

import android.content.Context
import com.deepcore.kiytoapp.BuildConfig
import com.deepcore.kiytoapp.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
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
            LogUtils.debug(this@UpdateManager, "Aktuelle Version: ${BuildConfig.VERSION_NAME}")
            LogUtils.debug(this@UpdateManager, "Aktueller VersionCode: ${BuildConfig.VERSION_CODE}")
            
            // GitHub API aufrufen für Releases
            val url = URL("$GITHUB_API_BASE/releases")
            val connection = url.openConnection()
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            connection.setRequestProperty("Authorization", "token ${BuildConfig.GITHUB_TOKEN}")
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
            connection.setRequestProperty("Pragma", "no-cache")
            connection.setRequestProperty("Expires", "0")
            
            LogUtils.debug(this@UpdateManager, "API Request URL: $url")
            LogUtils.debug(this@UpdateManager, "Token (erste 4 Zeichen): ${BuildConfig.GITHUB_TOKEN.take(4)}...")
            
            connection.useCaches = false
            
            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            LogUtils.debug(this@UpdateManager, "GitHub API Antwort erhalten: $response")
            
            val jsonArray = JSONObject("{\"releases\":$response}").getJSONArray("releases")
            LogUtils.debug(this@UpdateManager, "Anzahl gefundener Releases: ${jsonArray.length()}")
            
            if (jsonArray.length() > 0) {
                val latestRelease = jsonArray.getJSONObject(0)
                
                latestVersion = latestRelease.getString("tag_name").removePrefix("v")
                updateDescription = latestRelease.getString("body")
                updateUrl = latestRelease.getString("html_url")
                
                val currentVersion = BuildConfig.VERSION_NAME
                
                LogUtils.debug(this@UpdateManager, "Vergleiche Versionen:")
                LogUtils.debug(this@UpdateManager, "Aktuelle Version: $currentVersion")
                LogUtils.debug(this@UpdateManager, "Neueste Version: $latestVersion")
                
                // Versions-Vergleich
                updateAvailable = compareVersions(currentVersion, latestVersion ?: "0.0.0") < 0
                
                LogUtils.debug(this@UpdateManager, "Update verfügbar: $updateAvailable")
                LogUtils.debug(this@UpdateManager, "Update URL: $updateUrl")
                
                // Cache löschen und Status speichern
                resetUpdateStatus()
                saveUpdateStatus(Date())
                
                updateAvailable
            } else {
                LogUtils.debug(this@UpdateManager, "Keine Releases gefunden")
                false
            }
            
        } catch (e: Exception) {
            LogUtils.error(this@UpdateManager, "Fehler beim Prüfen auf Updates: ${e.message}", e)
            LogUtils.error(this@UpdateManager, "Stack trace: ${e.stackTraceToString()}")
            false
        }
    }

    private fun compareVersions(version1: String, version2: String): Int {
        try {
            LogUtils.debug(this, "Vergleiche Version1: $version1 mit Version2: $version2")
            
            val v1Parts = version1.split(".")
            val v2Parts = version2.split(".")
            
            LogUtils.debug(this, "Version1 Teile: $v1Parts")
            LogUtils.debug(this, "Version2 Teile: $v2Parts")
            
            val v1Numbers = v1Parts.map { it.toIntOrNull() ?: 0 }
            val v2Numbers = v2Parts.map { it.toIntOrNull() ?: 0 }
            
            LogUtils.debug(this, "Version1 Zahlen: $v1Numbers")
            LogUtils.debug(this, "Version2 Zahlen: $v2Numbers")
            
            val maxLength = maxOf(v1Numbers.size, v2Numbers.size)
            val paddedV1 = v1Numbers.padEnd(maxLength)
            val paddedV2 = v2Numbers.padEnd(maxLength)
            
            LogUtils.debug(this, "Aufgefüllte Version1: $paddedV1")
            LogUtils.debug(this, "Aufgefüllte Version2: $paddedV2")
            
            for (i in 0 until maxLength) {
                val comparison = paddedV1[i].compareTo(paddedV2[i])
                if (comparison != 0) {
                    LogUtils.debug(this, "Vergleichsergebnis an Position $i: $comparison")
                    return comparison
                }
            }
            
            LogUtils.debug(this, "Versionen sind identisch")
            return 0
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Vergleichen der Versionen", e)
            return 0
        }
    }

    private fun List<Int>.padEnd(length: Int): List<Int> {
        return if (size >= length) this else this + List(length - size) { 0 }
    }

    private fun saveUpdateStatus(checkDate: Date) {
        prefs.edit().apply {
            putLong(LAST_CHECK_KEY, checkDate.time)
            putBoolean(UPDATE_STATUS_KEY, updateAvailable)
            apply()
        }
        LogUtils.debug(this, "Update-Status gespeichert: $updateAvailable, Datum: $checkDate")
    }

    fun getLastCheckDate(): Date? {
        val timestamp = prefs.getLong(LAST_CHECK_KEY, 0)
        return if (timestamp > 0) Date(timestamp) else null
    }

    fun getUpdateStatus(): Boolean {
        val status = prefs.getBoolean(UPDATE_STATUS_KEY, false)
        LogUtils.debug(this, "Gespeicherter Update-Status abgerufen: $status")
        return status
    }

    fun resetUpdateStatus() {
        prefs.edit().apply {
            remove(LAST_CHECK_KEY)
            remove(UPDATE_STATUS_KEY)
            apply()
        }
        updateAvailable = false
        latestVersion = null
        updateDescription = null
        updateUrl = null
        LogUtils.debug(this, "Update-Status zurückgesetzt")
    }
} 