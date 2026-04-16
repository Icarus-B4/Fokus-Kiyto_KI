package com.deepcore.kiytoapp.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.deepcore.kiytoapp.util.LogUtils

class UpdateCompletionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
                val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (downloadId != -1L) {
                    LogUtils.debug(this, "Download abgeschlossen für ID: $downloadId")
                    
                    val downloader = APKDownloader(context)
                    val uri = downloader.getDownloadedFileUri(downloadId)
                    
                    if (uri != null) {
                        LogUtils.debug(this, "Starte automatische Installation für: $uri")
                        val installer = PackageInstallerHelper(context)
                        installer.installAPK(uri)
                    }
                }
            }
        }
    }
}