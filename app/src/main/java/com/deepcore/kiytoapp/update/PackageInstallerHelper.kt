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
            
            // Konvertiere URI in FileProvider URI wenn nötig
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            // Auf neueren Android-Versionen müssen wir den FileProvider nutzen, 
            // wenn die Datei aus einem privaten Ordner kommt. 
            // Wenn sie vom DownloadManager in Downloads liegt, ist es oft eine content:// URI.
            
            context.startActivity(installIntent)
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
