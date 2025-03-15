package com.deepcore.kiytoapp.update

import android.content.Context
import com.deepcore.kiytoapp.BuildConfig
import com.deepcore.kiytoapp.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class UpdateManager(private val context: Context) {
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com/repos/Icarus-B4/Fokus-Kiyto_KI"
        private const val PREFS_NAME = "update_prefs"
        private const val LAST_CHECK_KEY = "last_update_check"
        private const val UPDATE_STATUS_KEY = "update_status"
        private const val LATEST_VERSION_CODE_KEY = "latest_version_code"
        private const val UPDATE_CHECK_INTERVAL = 24 * 60 * 60 * 1000L // 24 Stunden
    }

    var latestVersion: String? = null
        private set
    var latestVersionCode: Int = 0
        private set
    var updateDescription: String? = null
        private set
    var updateUrl: String? = null
        private set
    var updateAvailable: Boolean = false
        private set
    var downloadUrl: String? = null
        private set
    var versionCompareResult: Int = 0
        private set

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun checkForUpdates(): Boolean = withContext(Dispatchers.IO) {
        try {
            LogUtils.debug(this@UpdateManager, "Prüfe auf Updates...")
            LogUtils.debug(this@UpdateManager, "Aktuelle Version: ${BuildConfig.VERSION_NAME} (Code: ${BuildConfig.VERSION_CODE})")
            
            val url = URL("$GITHUB_API_BASE/releases")
            LogUtils.debug(this@UpdateManager, "Prüfe GitHub API URL: $url")
            val connection = url.openConnection() as HttpURLConnection
            
            try {
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json")
                connection.setRequestProperty("User-Agent", "KiytoApp-UpdateChecker")
                
                if (BuildConfig.GITHUB_TOKEN.isNotEmpty()) {
                    LogUtils.debug(this@UpdateManager, "GitHub Token gefunden, füge Authorization Header hinzu")
                    connection.setRequestProperty("Authorization", "token ${BuildConfig.GITHUB_TOKEN}")
                } else {
                    LogUtils.debug(this@UpdateManager, "Kein GitHub Token gefunden, fahre ohne Authorization fort")
                }

                val responseCode = connection.responseCode
                LogUtils.debug(this@UpdateManager, "API Response Code: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    LogUtils.error(this@UpdateManager, "Repository nicht gefunden")
                    return@withContext false
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    LogUtils.error(this@UpdateManager, "API Fehler: $responseCode")
                    return@withContext false
                }

                val response = connection.inputStream.bufferedReader().use { it.readText() }
                LogUtils.debug(this@UpdateManager, "API Response erhalten: ${response.take(200)}...")
                
                val jsonArray = JSONObject("{\"releases\":$response}").getJSONArray("releases")
                LogUtils.debug(this@UpdateManager, "Anzahl gefundener Releases: ${jsonArray.length()}")

                if (jsonArray.length() > 0) {
                    val latestRelease = jsonArray.getJSONObject(0)
                    val tagName = latestRelease.getString("tag_name")
                    
                    // Extrahiere die Version aus dem Tag-Namen (Format: v1.2.3-20230415-123456)
                    val versionRegex = """v([\d.]+)(?:-\d+)?""".toRegex()
                    val versionMatch = versionRegex.find(tagName)
                    latestVersion = versionMatch?.groupValues?.get(1) ?: tagName.removePrefix("v")
                    
                    updateDescription = latestRelease.getString("body")
                    updateUrl = latestRelease.getString("html_url")

                    LogUtils.debug(this@UpdateManager, "Neueste Version gefunden (Tag): $tagName")
                    LogUtils.debug(this@UpdateManager, "Neueste Version (bereinigt): $latestVersion")
                    LogUtils.debug(this@UpdateManager, "Update URL: $updateUrl")

                    // Download URL aus Assets extrahieren
                    val assets = latestRelease.getJSONArray("assets")
                    var apkFound = false
                    for (i in 0 until assets.length()) {
                        val asset = assets.getJSONObject(i)
                        val assetName = asset.getString("name")
                        if (assetName.endsWith(".apk") && assetName.contains("KiytoApp")) {
                            downloadUrl = asset.getString("browser_download_url")
                            LogUtils.debug(this@UpdateManager, "APK Download URL gefunden: $downloadUrl")
                            apkFound = true
                            break
                        }
                    }
                    
                    // Wenn keine APK gefunden wurde, verwende die Release-URL
                    if (!apkFound) {
                        LogUtils.debug(this@UpdateManager, "Keine APK in Assets gefunden, verwende Release-URL")
                        downloadUrl = updateUrl
                    }

                    // Versionscode aus der Release-Beschreibung extrahieren
                    val versionCodeRegex = """versionCode:\s*(\d+)""".toRegex()
                    val matchResult = versionCodeRegex.find(updateDescription ?: "")
                    latestVersionCode = matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 0

                    if (latestVersionCode == 0) {
                        // Fallback: Berechne aus Version
                        val versionParts = latestVersion?.split(".")?.map { it.toIntOrNull() ?: 0 }
                        latestVersionCode = when (versionParts?.size) {
                            3 -> versionParts[0] * 10000 + versionParts[1] * 100 + versionParts[2]
                            2 -> versionParts[0] * 10000 + versionParts[1] * 100
                            1 -> versionParts[0] * 10000
                            else -> 0
                        }
                        LogUtils.debug(this@UpdateManager, "Versionscode aus Version berechnet: $latestVersionCode")
                    } else {
                        LogUtils.debug(this@UpdateManager, "Versionscode aus Release-Beschreibung: $latestVersionCode")
                    }

                    val currentVersion = BuildConfig.VERSION_NAME
                    val currentVersionCode = BuildConfig.VERSION_CODE

                    LogUtils.debug(this@UpdateManager, "Vergleiche Versionen:")
                    LogUtils.debug(this@UpdateManager, "Aktuelle Version: $currentVersion (Code: $currentVersionCode)")
                    LogUtils.debug(this@UpdateManager, "Neueste Version: $latestVersion (Code: $latestVersionCode)")

                    // Prüfe, ob die neueste Version größer ist als die aktuelle Version
                    val versionCompareResult = compareVersions(latestVersion ?: "0.0.0", currentVersion)
                    LogUtils.debug(this@UpdateManager, "Versionsvergleich Ergebnis: $versionCompareResult")
                    
                    // Speichere den Vergleichswert für die UI
                    this@UpdateManager.versionCompareResult = versionCompareResult
                    
                    // Ein Update ist verfügbar, wenn die semantische Version größer ist
                    // ODER wenn die Versionen gleich sind, aber der versionCode größer ist
                    updateAvailable = versionCompareResult > 0 || 
                                     (versionCompareResult == 0 && latestVersionCode > currentVersionCode)
                    
                    // Debug-Ausgabe für den Versionsvergleich
                    LogUtils.debug(this@UpdateManager, "Vergleichsergebnis: latestVersionCode ($latestVersionCode) > currentVersionCode ($currentVersionCode) = ${latestVersionCode > currentVersionCode}")
                    LogUtils.debug(this@UpdateManager, "Vergleichsergebnis: compareVersions = $versionCompareResult")
                    LogUtils.debug(this@UpdateManager, "Update verfügbar: $updateAvailable")

                    if (updateAvailable) {
                        LogUtils.debug(this@UpdateManager, "Änderungen in der neuen Version:")
                        LogUtils.debug(this@UpdateManager, updateDescription ?: "Keine Beschreibung verfügbar")
                    }

                    saveUpdateStatus(Date())
                    return@withContext updateAvailable
                }
                false
            } finally {
                connection.disconnect()
            }
        } catch (e: Exception) {
            LogUtils.error(this@UpdateManager, "Fehler beim Prüfen auf Updates: ${e.message}", e)
            false
        }
    }

    fun compareVersions(version1: String, version2: String): Int {
        try {
            LogUtils.debug(this, "Vergleiche Version1: $version1 mit Version2: $version2")
            
            // Entferne 'v' Prefix falls vorhanden
            val v1Clean = version1.removePrefix("v")
            val v2Clean = version2.removePrefix("v")
            
            // Teile die Versionen in Hauptversion und Nebenversion auf
            val v1Parts = v1Clean.split(".")
            val v2Parts = v2Clean.split(".")
            
            // Konvertiere zu Int und fülle mit 0 auf wenn nötig
            val v1Numbers = v1Parts.map { it.toIntOrNull() ?: 0 }
            val v2Numbers = v2Parts.map { it.toIntOrNull() ?: 0 }
            
            LogUtils.debug(this, "Aufgeteilte Versionen - v1: $v1Numbers, v2: $v2Numbers")
            
            // Vergleiche jede Komponente
            val minLength = minOf(v1Numbers.size, v2Numbers.size)
            
            for (i in 0 until minLength) {
                val comparison = v1Numbers[i].compareTo(v2Numbers[i])
                LogUtils.debug(this, "Vergleiche Komponente $i: ${v1Numbers[i]} vs ${v2Numbers[i]} = $comparison")
                if (comparison != 0) {
                    return comparison
                }
            }
            
            // Wenn alle gemeinsamen Komponenten gleich sind, ist die längere Version größer
            return v1Numbers.size.compareTo(v2Numbers.size)
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Vergleichen der Versionen: ${e.message}", e)
            return 0
        }
    }

    private fun saveUpdateStatus(checkDate: Date) {
        prefs.edit().apply {
            putLong(LAST_CHECK_KEY, checkDate.time)
            putBoolean(UPDATE_STATUS_KEY, updateAvailable)
            putInt(LATEST_VERSION_CODE_KEY, latestVersionCode)
            apply()
        }
        LogUtils.debug(this, "Update-Status gespeichert: $updateAvailable, Datum: $checkDate, VersionCode: $latestVersionCode")
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
            remove(LATEST_VERSION_CODE_KEY)
            apply()
        }
        updateAvailable = false
        latestVersion = null
        latestVersionCode = 0
        updateDescription = null
        updateUrl = null
        downloadUrl = null
        LogUtils.debug(this, "Update-Status zurückgesetzt")
    }
} 