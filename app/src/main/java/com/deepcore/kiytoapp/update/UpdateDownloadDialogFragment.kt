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
            // Extrahiere die Release-URL (falls es eine GitHub-URL ist)
            val releaseUrl = if (url.contains("/releases/download/")) {
                url.split("/releases/download/").first() + "/releases/latest"
            } else {
                url
            }
            
            // Zeige Deinstallations-Dialog
            showDeinstallationDialog(releaseUrl)
        } catch (e: Exception) {
            Log.e("UpdateDownloadDialog", "Fehler beim Starten des Downloads", e)
            Toast.makeText(
                requireContext(),
                "Fehler beim Verarbeiten der Update-Anfrage: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showDeinstallationDialog(releaseUrl: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Update verfügbar")
            .setMessage("Um das Update zu installieren, müssen Sie:\n\n" +
                    "1. Die aktuelle Version deinstallieren\n" +
                    "2. Die neue Version von GitHub herunterladen und installieren\n\n" +
                    "Möchten Sie jetzt mit der Deinstallation beginnen?")
            .setPositiveButton("Jetzt deinstallieren") { _, _ ->
                try {
                    // Speichere die URL für den Browser-Start
                    val prefs = requireContext().getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                    prefs.edit().putString("pending_update_url", releaseUrl).apply()
                    
                    // Starte Deinstallation
                    val packageName = requireContext().packageName
                    val uninstallIntent = Intent(Intent.ACTION_DELETE)
                    uninstallIntent.data = Uri.parse("package:$packageName")
                    uninstallIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    
                    // Zeige Hinweis
                    Toast.makeText(
                        requireContext(),
                        "Nach der Deinstallation wird die GitHub-Seite geöffnet.",
                        Toast.LENGTH_LONG
                    ).show()
                    
                    // Beende den Dialog und starte die Deinstallation
                    dismiss()
                    requireActivity().finish()
                    startActivity(uninstallIntent)
                    
                } catch (e: Exception) {
                    Log.e("UpdateDownloadDialog", "Fehler beim Deinstallieren", e)
                    Toast.makeText(
                        requireContext(),
                        "Fehler beim Deinstallieren: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("GitHub öffnen") { _, _ ->
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl))
                    startActivity(browserIntent)
                    Toast.makeText(
                        requireContext(),
                        "Denken Sie daran, die App zu deinstallieren, bevor Sie die neue Version installieren!",
                        Toast.LENGTH_LONG
                    ).show()
                    dismiss()
                } catch (e: Exception) {
                    Log.e("UpdateDownloadDialog", "Fehler beim Öffnen des Browsers", e)
                    Toast.makeText(
                        requireContext(),
                        "Fehler beim Öffnen des Browsers: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNeutralButton("Später", null)
            .show()
    }
} 