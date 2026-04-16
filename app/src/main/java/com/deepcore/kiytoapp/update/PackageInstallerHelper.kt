package com.deepcore.kiytoapp.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import com.deepcore.kiytoapp.util.LogUtils
import java.io.File

class PackageInstallerHelper(private val context: Context) {

    fun installAPK(uri: Uri) {
        try {
            LogUtils.debug(this, "Starte APK-Installation für: $uri")
            
            // Auf Android 8.0 (API 26) und höher prüfen wir die Berechtigung zum Installieren unbekannter Apps
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    LogUtils.debug(this, "Keine Berechtigung zum Installieren unbekannter Apps. Öffne Einstellungen.")
                    val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(intent)
                    return
                }
            }

            val installIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                putExtra(Intent.EXTRA_INSTALLER_PACKAGE_NAME, context.packageName)
            }
            
            // Fallback für ACTION_INSTALL_PACKAGE (da deprecated ab API 29, aber oft zuverlässiger)
            // Wenn ACTION_INSTALL_PACKAGE nicht geht, nutzen wir ACTION_VIEW
            try {
                context.startActivity(installIntent)
            } catch (e: Exception) {
                LogUtils.debug(this, "Fallback auf ACTION_VIEW für Installation")
                val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
                }
                context.startActivity(viewIntent)
            }
            
            LogUtils.debug(this, "Installations-Intent gesendet")
            
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler bei der APK-Installation", e)
        }
    }
    
    fun installAPKFromFile(file: File) {
        try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            installAPK(uri)
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Erstellen der FileProvider-URI", e)
        }
    }
}
