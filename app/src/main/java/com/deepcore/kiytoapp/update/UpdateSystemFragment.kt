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
import android.widget.ImageView

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

        // Initialisiere Animation
        setupAnimation()

        // Setze Click-Listener
        downloadCard.setOnClickListener {
            // Zeige update_available animation
            updateAnimation.apply {
                cancelAnimation()
                setAnimation(R.raw.update_available)
                playAnimation()
            }
            checkForUpdates()
        }

        statusCard.setOnClickListener {
            // Zeige loading animation
            updateAnimation.apply {
                cancelAnimation() 
                setAnimation(R.raw.loading)
                playAnimation()
            }
            showUpdateStatus()
        }

        debugLogsCard.setOnClickListener {
            showDebugLogs()
        }

        // Teste verschiedene Animationen beim Klick auf die Animation
        updateAnimation.setOnClickListener {
            testNextAnimation()
        }

        // Lade initiale Daten
        loadUpdateInfo()
        
        // Prüfe automatisch auf Updates
        checkForUpdates()
    }

    private fun setupAnimation() {
        updateAnimation.apply {
            // Debug der Animation-Größe
            post {
                android.util.Log.d("LottieDebug", "Animation view size: ${measuredWidth}x${measuredHeight}")
            }
            
            // Setze die Animation
            setAnimation(R.raw.loading)
            
            // Setze die Geschwindigkeit
            speed = 1.0f
            
            // Erhöhe die Sichtbarkeit
            alpha = 1.0f
            
            // Setze die Skalierung
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            
            // Debug-Listener
            addAnimatorListener(object : android.animation.Animator.AnimatorListener {
                override fun onAnimationStart(animation: android.animation.Animator) {
                    android.util.Log.d("LottieDebug", "Animation started")
                }
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    android.util.Log.d("LottieDebug", "Animation ended")
                }
                override fun onAnimationCancel(animation: android.animation.Animator) {
                    android.util.Log.d("LottieDebug", "Animation cancelled")
                }
                override fun onAnimationRepeat(animation: android.animation.Animator) {
                    android.util.Log.d("LottieDebug", "Animation repeated")
                }
            })
            
            // Composition-Listener für detailliertere Informationen
            addLottieOnCompositionLoadedListener { composition ->
                android.util.Log.d("LottieDebug", "Composition loaded: ${composition.bounds}")
                android.util.Log.d("LottieDebug", "Composition size: ${composition.bounds.width()}x${composition.bounds.height()}")
                
                // Setze die Skalierung basierend auf der Composition
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                
                // Stelle sicher, dass die Animation sichtbar ist
                visibility = View.VISIBLE
            }
            
            // Progress-Listener
            addAnimatorUpdateListener { 
                android.util.Log.d("LottieDebug", "Animation progress: ${it.animatedFraction}")
            }
            
            // Fehler-Listener
            setFailureListener { e ->
                android.util.Log.e("LottieDebug", "Animation failed to load", e)
                // Versuche es mit einer anderen Animation
                setAnimation(R.raw.no_update)
                playAnimation()
            }

            // Starte die Animation
            playAnimation()
        }
    }

    private fun updateAnimationState(isLoading: Boolean = false, isError: Boolean = false) {
        try {
            updateAnimation.apply {
                cancelAnimation() // Stoppe aktuelle Animation
                
                val animationRes = when {
                    isLoading -> R.raw.loading
                    isError -> R.raw.error
                    updateManager.getUpdateStatus() -> R.raw.update_available
                    else -> R.raw.ok // OK-Animation anzeigen, wenn alles auf dem neuesten Stand ist
                }
                
                // Debug-Information
                android.util.Log.d("LottieDebug", "Setting animation to: $animationRes")
                
                setAnimation(animationRes)
                speed = 1.0f
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                alpha = 1.0f
                visibility = View.VISIBLE
                playAnimation()
            }
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Aktualisieren der Animation", e)
        }
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
            
            // Aktualisiere Animation-Status
            updateAnimationState()
            
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Laden der Update-Informationen", e)
            updateAnimationState(isError = true)
        }
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {
                // Zeige Lade-Animation
                updateAnimationState(isLoading = true)
                
                val updateAvailable = updateManager.checkForUpdates()
                
                // Zeige Update-Dialog wenn verfügbar
                if (updateAvailable) {
                    showUpdateDialog()
                }
                
                // Aktualisiere Informationen und Animation
                loadUpdateInfo()
                
            } catch (e: Exception) {
                LogUtils.error(this, "Fehler beim Update-Check", e)
                updateAnimationState(isError = true)
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

    // Index für Test-Animationen
    private var testAnimationIndex = 0
    
    // Animationen zum Testen
    private val testAnimations = arrayOf(
        R.raw.loading,
        R.raw.update_available,
        R.raw.ok,
        R.raw.error
    )
    
    // Testet die nächste Animation
    private fun testNextAnimation() {
        testAnimationIndex = (testAnimationIndex + 1) % testAnimations.size
        val animRes = testAnimations[testAnimationIndex]
        
        android.util.Log.d("LottieDebug", "Testing animation: $animRes")
        
        updateAnimation.apply {
            cancelAnimation()
            setAnimation(animRes)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            alpha = 1.0f
            visibility = View.VISIBLE
            playAnimation()
        }
    }
} 