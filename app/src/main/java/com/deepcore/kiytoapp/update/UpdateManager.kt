package com.deepcore.kiytoapp.update

import android.content.Context
import com.deepcore.kiytoapp.BuildConfig
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
            
            // GitHub API aufrufen für Releases
            val url = URL("$GITHUB_API_BASE/releases")
            val connection = url.openConnection()
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
            // Cache-Control Header hinzufügen
            connection.setRequestProperty("Cache-Control", "no-cache, no-store, must-revalidate")
            connection.setRequestProperty("Pragma", "no-cache")
            connection.setRequestProperty("Expires", "0")
            
            // Verbindung öffnen mit Cache-Invalidierung
            connection.useCaches = false
            
            val response = connection.getInputStream().bufferedReader().use { it.readText() }
            val jsonArray = JSONObject("{\"releases\":$response}").getJSONArray("releases")
            
            if (jsonArray.length() > 0) {
                val latestRelease = jsonArray.getJSONObject(0)
                
                // Versionsinformationen extrahieren
                latestVersion = latestRelease.getString("tag_name").removePrefix("v")
                updateDescription = latestRelease.getString("body")
                updateUrl = latestRelease.getString("html_url")
                
                // Versions-Vergleich
                val currentVersion = BuildConfig.VERSION_NAME
                updateAvailable = compareVersions(currentVersion, latestVersion ?: "0.0.0") < 0
                
                // Update-Status speichern und Cache löschen
                resetUpdateStatus()
                saveUpdateStatus(Date())
                
                LogUtils.debug(this@UpdateManager, "Update-Check abgeschlossen. Aktuelle Version: $currentVersion, Neueste Version: $latestVersion, Verfügbar: $updateAvailable")
                updateAvailable
            } else {
                LogUtils.debug(this@UpdateManager, "Keine Releases gefunden")
                false
            }
            
        } catch (e: Exception) {
            LogUtils.error(this@UpdateManager, "Fehler beim Prüfen auf Updates", e)
            // Zeige Fehler-Animation
            false
        }
    }

    private fun compareVersions(version1: String, version2: String): Int {
        try {
            val v1Parts = version1.split(".")
            val v2Parts = version2.split(".")
            
            // Konvertiere Versionsteile in Integer und vergleiche
            val v1Numbers = v1Parts.map { it.toIntOrNull() ?: 0 }
            val v2Numbers = v2Parts.map { it.toIntOrNull() ?: 0 }
            
            // Fülle die kürzere Version mit Nullen auf
            val maxLength = maxOf(v1Numbers.size, v2Numbers.size)
            val paddedV1 = v1Numbers.padEnd(maxLength)
            val paddedV2 = v2Numbers.padEnd(maxLength)
            
            // Vergleiche jede Komponente
            for (i in 0 until maxLength) {
                when {
                    paddedV1[i] < paddedV2[i] -> return -1
                    paddedV1[i] > paddedV2[i] -> return 1
                }
            }
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
    }

    fun getLastCheckDate(): Date? {
        val timestamp = prefs.getLong(LAST_CHECK_KEY, 0)
        return if (timestamp > 0) Date(timestamp) else null
    }

    fun getUpdateStatus(): Boolean {
        return prefs.getBoolean(UPDATE_STATUS_KEY, false)
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
    }
} 