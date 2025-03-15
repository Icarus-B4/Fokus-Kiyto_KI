package com.deepcore.kiytoapp.update

import android.app.Dialog
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.fragment.app.DialogFragment
import com.airbnb.lottie.LottieAnimationView
import com.deepcore.kiytoapp.BuildConfig
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.util.LogUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateDialog : DialogFragment() {
    private var downloadId: Long = -1
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var downloadManager: DownloadManager
    private var downloadReceiver: BroadcastReceiver? = null
    private lateinit var updateManager: UpdateManager

    companion object {
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_URL = "url"

        fun newInstance(description: String, url: String): UpdateDialog {
            return UpdateDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_DESCRIPTION, description)
                    putString(ARG_URL, url)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        updateManager = UpdateManager(requireContext())
        downloadManager = requireContext().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_update, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkForUpdates()
    }

    private fun checkForUpdates() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val updateAvailable = withContext(Dispatchers.IO) {
                    updateManager.checkForUpdates()
                }
                
                if (updateAvailable) {
                    showUpdateAvailableDialog()
                } else {
                    showNoUpdateDialog()
                }
            } catch (e: Exception) {
                LogUtils.error(this@UpdateDialog, "Fehler beim Prüfen auf Updates: ${e.message}", e)
                showErrorDialog(getString(R.string.error_checking_updates))
            }
        }
    }

    private fun showUpdateAvailableDialog() {
        val builder = MaterialAlertDialogBuilder(requireContext())
        builder.setTitle(getString(R.string.update_available))
        
        val message = StringBuilder()
        message.append(getString(R.string.current_version, BuildConfig.VERSION_NAME))
        message.append("\n")
        message.append(getString(R.string.latest_version, updateManager.latestVersion))
        message.append("\n\n")
        
        // Extrahiere die wichtigsten Änderungen
        val changes = updateManager.updateDescription?.lines()
            ?.filter { it.trim().startsWith("-") }
            ?.take(5)
            ?.joinToString("\n")
        
        if (!changes.isNullOrEmpty()) {
            message.append(getString(R.string.update_changes_title))
            message.append("\n")
            message.append(changes)
        } else {
            message.append(getString(R.string.no_changes_available))
        }

        builder.setMessage(message.toString())
        builder.setPositiveButton(getString(R.string.download_update)) { _, _ ->
            updateManager.downloadUrl?.let { url ->
                startDownload(url)
            }
        }
        builder.setNegativeButton(getString(R.string.remind_me_later)) { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun showNoUpdateDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.update_check_complete)
            .setMessage(getString(R.string.no_update_available))
            .setPositiveButton(R.string.ok, null)
            .show()
    }

    private fun startDownload(downloadUrl: String) {
        try {
            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle(getString(R.string.app_name))
                .setDescription(getString(R.string.downloading_update))
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    "KiytoApp-${updateManager.latestVersion}.apk"
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            downloadId = downloadManager.enqueue(request)
            registerDownloadReceiver()
            showDownloadProgressDialog()
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Starten des Downloads: ${e.message}", e)
            showErrorDialog(getString(R.string.error_starting_download))
        }
    }

    private fun registerDownloadReceiver() {
        downloadReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id == downloadId) {
                    val query = DownloadManager.Query().setFilterById(downloadId)
                    val cursor = downloadManager.query(query)
                    
                    if (cursor.moveToFirst()) {
                        val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val uri = downloadManager.getUriForDownloadedFile(downloadId)
                                uri?.let { installUpdate(it) }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                showErrorDialog(getString(R.string.download_failed))
                            }
                        }
                    }
                    cursor.close()
                }
            }
        }
        
        requireContext().registerReceiver(
            downloadReceiver,
            IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        )
    }

    private fun installUpdate(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(intent)
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Installieren des Updates: ${e.message}", e)
            showErrorDialog(getString(R.string.installation_failed))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadReceiver?.let {
            requireContext().unregisterReceiver(it)
        }
    }

    private fun showDownloadProgressDialog() {
        // Implementierung hier...
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.error)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .show()
    }
} 