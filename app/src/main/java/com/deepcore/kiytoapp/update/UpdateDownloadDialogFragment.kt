package com.deepcore.kiytoapp.update

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.deepcore.kiytoapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class UpdateDownloadDialogFragment : DialogFragment() {
    
    companion object {
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_URL = "url"

        fun newInstance(description: String, url: String): UpdateDownloadDialogFragment {
            return UpdateDownloadDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DESCRIPTION, description)
                    putString(ARG_URL, url)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val description = requireArguments().getString(ARG_DESCRIPTION)
        val url = requireArguments().getString(ARG_URL)

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.fragment_update_download, null)

        // Initialisiere Views
        val releaseNotesText = view.findViewById<TextView>(R.id.releaseNotesText)
        val downloadButton = view.findViewById<MaterialButton>(R.id.downloadButton)
        val remindButton = view.findViewById<MaterialButton>(R.id.remindButton)

        // Setze Release-Notes
        releaseNotesText.text = description

        // Setze Click-Listener
        downloadButton.setOnClickListener {
            url?.let { urlString ->
                startDownload(urlString)
            }
        }

        remindButton.setOnClickListener {
            dismiss()
        }

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
                
                // Setze die Größe des Dialogs
                window?.setLayout(
                    android.view.WindowManager.LayoutParams.MATCH_PARENT,
                    android.view.WindowManager.LayoutParams.WRAP_CONTENT
                )
            }
        
        return dialog
    }

    private fun startDownload(url: String) {
        try {
            val updateManager = UpdateManager(requireContext())
            
            // Bevorzuge die übergebene URL, falls sie valide ist (endet auf .apk oder enthält /releases/download/)
            val downloadUrl = if (url.endsWith(".apk") || url.contains("/releases/download/")) {
                url
            } else {
                updateManager.downloadUrl ?: url
            }
            
            // Wenn immer noch keine direkte APK-URL vorliegt, Browser als Fallback
            if (!downloadUrl.endsWith(".apk") && !downloadUrl.contains("/releases/download/")) {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                startActivity(browserIntent)
                dismiss()
                return
            }
 
            Toast.makeText(requireContext(), "Download gestartet. Installation folgt automatisch...", Toast.LENGTH_LONG).show()
            
            val downloader = APKDownloader(requireContext())
            val fileName = "Fokus-Kiyto-v${updateManager.latestVersion ?: "update"}.apk"
            val downloadId = downloader.downloadAPK(downloadUrl, fileName)
            
            if (downloadId != -1L) {
                // Hier könnten wir einen BroadcastReceiver registrieren, um die Installation sofort nach Abschluss zu starten.
                // Für dieses MVP verlassen wir uns auf die Systembenachrichtigung des DownloadManagers, 
                // oder man klickt dort auf die abgeschlossene Datei.
                
                // OPTIONAL: Direkte Installation anstoßen (erfordert BroadcastReceiver)
                dismiss()
            } else {
                Toast.makeText(requireContext(), "Fehler beim Starten des Downloads", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            Log.e("UpdateDownloadDialog", "Fehler beim Starten des Downloads", e)
            Toast.makeText(
                requireContext(),
                "Fehler beim Verarbeiten der Update-Anfrage: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }
} 