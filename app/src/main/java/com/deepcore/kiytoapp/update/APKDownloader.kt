package com.deepcore.kiytoapp.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import com.deepcore.kiytoapp.util.LogUtils

class APKDownloader(private val context: Context) {

    fun downloadAPK(url: String, fileName: String): Long {
        try {
            LogUtils.debug(this, "Starte APK-Download: $url")
            
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("Fokus-Kiyto Update")
                .setDescription("Herunterladen der neuesten Version...")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
            
            // Verwende den System-DownloadManager
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)
            
            LogUtils.debug(this, "Download in Warteschlange eingereiht. ID: $downloadId")
            return downloadId
            
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Starten des APK-Downloads", e)
            return -1L
        }
    }
    
    fun getDownloadedFileUri(downloadId: Long): Uri? {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)
        
        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            if (cursor.getInt(statusIndex) == DownloadManager.STATUS_SUCCESSFUL) {
                val uriString = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                cursor.close()
                return Uri.parse(uriString)
            }
        }
        cursor.close()
        return null
    }
}
