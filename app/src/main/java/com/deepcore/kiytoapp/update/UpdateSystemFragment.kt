package com.deepcore.kiytoapp.update

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.deepcore.kiytoapp.BuildConfig
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.util.LogUtils
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class UpdateSystemFragment : Fragment() {
    private lateinit var lastCheckedText: TextView
    private lateinit var currentVersionText: TextView
    private lateinit var downloadCard: MaterialCardView
    private lateinit var statusCard: MaterialCardView
    private lateinit var debugLogsCard: MaterialCardView
    private lateinit var updateManager: UpdateManager
    private lateinit var updateAnimation: LottieAnimationView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_update_system, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        updateManager = UpdateManager(requireContext())
        
        // Initialisiere Views
        lastCheckedText = view.findViewById(R.id.lastCheckedText)
        currentVersionText = view.findViewById(R.id.currentVersionText)
        downloadCard = view.findViewById(R.id.downloadCard)
        statusCard = view.findViewById(R.id.statusCard)
        debugLogsCard = view.findViewById(R.id.debugLogsCard)
        updateAnimation = view.findViewById(R.id.updateAnimation)

        // Setze Click-Listener
        downloadCard.setOnClickListener {
            checkForUpdates()
        }

        statusCard.setOnClickListener {
            showUpdateStatus()
        }

        debugLogsCard.setOnClickListener {
            showDebugLogs()
        }

        // Lade initiale Daten
        loadUpdateInfo()
        
        // Prüfe automatisch auf Updates
        checkForUpdates()
    }

    private fun loadUpdateInfo() {
        try {
            // Zeige aktuelle Version
            currentVersionText.text = getString(R.string.current_version, BuildConfig.VERSION_NAME)
            
            // Zeige letztes Prüfdatum
            updateManager.getLastCheckDate()?.let { lastCheck ->
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                lastCheckedText.text = getString(R.string.last_checked, dateFormat.format(lastCheck))
            } ?: run {
                lastCheckedText.text = getString(R.string.never_checked)
            }
            
            // Zeige Update-Status Animation
            updateAnimation.setAnimation(
                if (updateManager.getUpdateStatus()) {
                    R.raw.update_available
                } else {
                    R.raw.no_update
                }
            )
            updateAnimation.playAnimation()
            
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Laden der Update-Informationen", e)
        }
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
                
                // Aktualisiere Informationen
                loadUpdateInfo()
                
                // Zeige Update-Dialog wenn verfügbar
                if (updateAvailable) {
                    showUpdateDialog()
                }
                
            } catch (e: Exception) {
                LogUtils.error(this, "Fehler beim Update-Check", e)
                // Zeige Fehler-Animation
                updateAnimation.setAnimation(R.raw.error)
                updateAnimation.playAnimation()
            }
        }
    }

    private fun showUpdateStatus() {
        val currentVersion = BuildConfig.VERSION_NAME
        val latestVersion = updateManager.latestVersion ?: currentVersion
        
        UpdateStatusDialog.newInstance(
            currentVersion = currentVersion,
            latestVersion = latestVersion,
            updateAvailable = updateManager.updateAvailable,
            updateDescription = updateManager.updateDescription
        ).show(parentFragmentManager, "update_status")
    }

    private fun showUpdateDialog() {
        updateManager.updateDescription?.let { description ->
            updateManager.updateUrl?.let { url ->
                UpdateDialog.newInstance(description, url)
                    .show(parentFragmentManager, "update_dialog")
            }
        }
    }

    private fun showDebugLogs() {
        val logs = StringBuilder()
            .append("App Version: ${BuildConfig.VERSION_NAME}\n")
            .append("Letzte Prüfung: ${updateManager.getLastCheckDate()}\n")
            .append("Update verfügbar: ${updateManager.updateAvailable}\n")
            .append("Neueste Version: ${updateManager.latestVersion}\n")
            .append("Update URL: ${updateManager.updateUrl}\n")
            .toString()
            
        DebugLogDialog.newInstance(logs)
            .show(parentFragmentManager, "debug_logs")
    }
} 