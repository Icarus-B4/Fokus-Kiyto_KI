package com.deepcore.kiytoapp.update

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.airbnb.lottie.LottieAnimationView
import com.deepcore.kiytoapp.BuildConfig
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.util.LogUtils
import java.text.SimpleDateFormat
import java.util.*

class UpdateStatusFragment : Fragment() {
    private lateinit var updateManager: UpdateManager
    private lateinit var lastCheckedText: TextView
    private lateinit var versionText: TextView
    private lateinit var statusText: TextView
    private lateinit var statusAnimation: LottieAnimationView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_update_status, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        updateManager = UpdateManager(requireContext())
        
        // Initialisiere Views
        lastCheckedText = view.findViewById(R.id.lastCheckedText)
        versionText = view.findViewById(R.id.versionText)
        statusText = view.findViewById(R.id.statusText)
        statusAnimation = view.findViewById(R.id.statusAnimation)
        
        // Initialisiere Animation
        setupAnimation()
        
        // Zeige Update-Status
        updateStatusDisplay()
    }

    private fun setupAnimation() {
        statusAnimation.apply {
            // Setze die loading-Animation als Standard
            setAnimation(R.raw.loading)
            
            // Setze die Geschwindigkeit
            speed = 1.0f
            
            // Starte die Animation
            playAnimation()
        }
    }

    private fun updateStatusDisplay() {
        try {
            // Zeige aktuelle Version
            val currentVersion = BuildConfig.VERSION_NAME
            versionText.text = getString(R.string.current_version, currentVersion)
            
            // Zeige letztes Prüfdatum
            updateManager.getLastCheckDate()?.let { lastCheck ->
                val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                lastCheckedText.text = getString(R.string.last_checked, dateFormat.format(lastCheck))
            } ?: run {
                lastCheckedText.text = getString(R.string.never_checked)
            }
            
            // Zeige Update-Status
            val latestVersion = updateManager.latestVersion ?: currentVersion
            val statusMessage = if (updateManager.updateAvailable) {
                statusAnimation.apply {
                    cancelAnimation()
                    setAnimation(R.raw.update_available)
                    playAnimation()
                }
                getString(R.string.update_available_message) + "\n" +
                getString(R.string.latest_version, latestVersion)
            } else {
                statusAnimation.apply {
                    cancelAnimation()
                    setAnimation(R.raw.no_update)
                    playAnimation()
                }
                getString(R.string.no_update_available_message)
            }
            statusText.text = statusMessage
            
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Aktualisieren des Update-Status", e)
            statusAnimation.apply {
                cancelAnimation()
                setAnimation(R.raw.error)
                playAnimation()
            }
        }
    }
} 