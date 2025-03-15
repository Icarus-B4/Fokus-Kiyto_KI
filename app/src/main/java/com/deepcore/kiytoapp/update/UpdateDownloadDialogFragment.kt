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
            // Prüfe, ob die URL gültig ist
            if (!url.startsWith("http")) {
                Log.e("UpdateDownloadDialog", "Ungültige URL: $url")
                Toast.makeText(
                    requireContext(),
                    "Fehler: Ungültige Download-URL",
                    Toast.LENGTH_LONG
                ).show()
                return
            }
            
            // Öffne direkt den Browser mit der Download-URL
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(browserIntent)
            
            // Zeige einen Hinweis
            Toast.makeText(
                requireContext(),
                "Download der neuen Version gestartet. Bitte installieren Sie die neue Version nach dem Download.",
                Toast.LENGTH_LONG
            ).show()
            
            // Dialog schließen
            dismiss()
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