package com.deepcore.kiytoapp.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.util.LogUtils
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class UpdateDownloadFragment : Fragment() {
    private lateinit var updateManager: UpdateManager
    private lateinit var updateAnimation: LottieAnimationView
    private lateinit var releaseNotesText: TextView
    private lateinit var downloadButton: MaterialButton
    private lateinit var remindButton: MaterialButton

    companion object {
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_URL = "url"

        fun newInstance(description: String, url: String): UpdateDownloadFragment {
            return UpdateDownloadFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_DESCRIPTION, description)
                    putString(ARG_URL, url)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_update_download, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        updateManager = UpdateManager(requireContext())
        updateAnimation = view.findViewById(R.id.updateAnimation)
        releaseNotesText = view.findViewById(R.id.releaseNotesText)
        downloadButton = view.findViewById(R.id.downloadButton)
        remindButton = view.findViewById(R.id.remindButton)
        
        // Hole Argumente
        val description = arguments?.getString(ARG_DESCRIPTION)
        val url = arguments?.getString(ARG_URL)

        // Setze Release-Notes
        releaseNotesText.text = description

        // Setze Click-Listener
        downloadButton.setOnClickListener {
            url?.let { urlString ->
                startDownload(urlString)
            }
        }

        remindButton.setOnClickListener {
            // Schließe das Fragment
            parentFragmentManager.popBackStack()
        }
        
        // Starte die Prüfung auf Updates
        checkForUpdates()
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {
                // Zeige Lade-Animation
                updateAnimation.setAnimation(R.raw.loading)
                updateAnimation.playAnimation()
                
                // Prüfe auf Updates
                val updateAvailable = updateManager.checkForUpdates()
                
                // Zeige entsprechende Animation
                updateAnimation.setAnimation(
                    if (updateAvailable) {
                        R.raw.update_available
                    } else {
                        R.raw.no_update
                    }
                )
                updateAnimation.playAnimation()
                
                // Zeige Update-Dialog wenn verfügbar
                if (updateAvailable) {
                    updateManager.updateDescription?.let { description ->
                        updateManager.updateUrl?.let { url ->
                            UpdateDialog.newInstance(description, url)
                                .show(parentFragmentManager, "update_dialog")
                        }
                    }
                } else {
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.no_update_available_message),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
            } catch (e: Exception) {
                LogUtils.error(this@UpdateDownloadFragment, "Fehler beim Update-Check", e)
                // Zeige Fehler-Animation
                updateAnimation.setAnimation(R.raw.error)
                updateAnimation.playAnimation()
                
                Toast.makeText(
                    requireContext(),
                    "Fehler beim Prüfen auf Updates",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
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
            Log.e("UpdateDownloadFragment", "Fehler beim Starten des Downloads", e)
            Toast.makeText(
                requireContext(),
                "Fehler beim Verarbeiten der Update-Anfrage: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun showDeinstallationDialog(releaseUrl: String) {
        val dialog = MaterialAlertDialogBuilder(requireContext())
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
                    
                    // Starte die Deinstallation
                    requireActivity().finish()
                    startActivity(uninstallIntent)
                    
                } catch (e: Exception) {
                    Log.e("UpdateDownloadFragment", "Fehler beim Deinstallieren", e)
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
                } catch (e: Exception) {
                    Log.e("UpdateDownloadFragment", "Fehler beim Öffnen des Browsers", e)
                    Toast.makeText(
                        requireContext(),
                        "Fehler beim Öffnen des Browsers: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            .setNeutralButton("Später", null)
            .create()
            
        dialog.show()
    }
} 