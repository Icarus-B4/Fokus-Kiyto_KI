package com.deepcore.kiytoapp.update

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.util.LogUtils
import kotlinx.coroutines.launch

class UpdateDownloadFragment : Fragment() {
    private lateinit var updateManager: UpdateManager
    private lateinit var updateAnimation: LottieAnimationView

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
} 