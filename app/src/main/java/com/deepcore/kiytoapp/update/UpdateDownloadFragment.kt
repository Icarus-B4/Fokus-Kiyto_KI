package com.deepcore.kiytoapp.update

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.base.BaseFragment
import com.deepcore.kiytoapp.util.LogUtils
import kotlinx.coroutines.launch

class UpdateDownloadFragment : BaseFragment() {
    private lateinit var updateManager: UpdateManager
    private lateinit var loadingAnimation: LottieAnimationView
    
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
        loadingAnimation = view.findViewById(R.id.loadingAnimation)
        
        checkForUpdates()
    }
    
    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {
                loadingAnimation.setAnimation(R.raw.loading)
                loadingAnimation.playAnimation()
                
                val currentVersion = com.deepcore.kiytoapp.BuildConfig.VERSION_NAME
                val repoUrl = "https://api.github.com/repos/YourUsername/KiytoApp"
                
                if (updateManager.checkForUpdates(currentVersion, repoUrl)) {
                    loadingAnimation.setAnimation(R.raw.update_available)
                    loadingAnimation.playAnimation()
                    
                    updateManager.updateDescription?.let { description ->
                        updateManager.updateUrl?.let { url ->
                            val dialog = UpdateDialog.newInstance(description, url)
                            dialog.show(parentFragmentManager, "update_dialog")
                        }
                    }
                } else {
                    loadingAnimation.setAnimation(R.raw.no_update)
                    loadingAnimation.playAnimation()
                }
            } catch (e: Exception) {
                LogUtils.error(this, "Fehler beim Prüfen auf Updates", e)
                loadingAnimation.setAnimation(R.raw.error)
                loadingAnimation.playAnimation()
            }
        }
    }
} 