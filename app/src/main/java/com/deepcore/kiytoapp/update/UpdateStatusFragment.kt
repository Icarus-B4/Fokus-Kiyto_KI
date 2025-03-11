package com.deepcore.kiytoapp.update

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.base.BaseFragment
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UpdateStatusFragment : BaseFragment() {
    private lateinit var updateManager: UpdateManager
    
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
        
        // Aktualisiere die Status-Anzeige
        updateStatusDisplay(view)
    }
    
    private fun updateStatusDisplay(view: View) {
        val currentVersion = com.deepcore.kiytoapp.BuildConfig.VERSION_NAME
        val latestVersion = updateManager.latestVersion ?: currentVersion
        val lastCheck = updateManager.lastUpdateCheck
        
        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val lastCheckString = if (lastCheck > 0) dateFormat.format(Date(lastCheck)) else "-"
        
        view.findViewById<TextView>(R.id.currentVersionText)?.text = 
            "Aktuelle Version: $currentVersion"
            
        view.findViewById<TextView>(R.id.latestVersionText)?.text = 
            "Neueste Version: $latestVersion"
            
        view.findViewById<TextView>(R.id.lastCheckText)?.text = 
            "Zuletzt geprüft: $lastCheckString"
            
        view.findViewById<TextView>(R.id.updateStatusText)?.text = 
            if (updateManager.updateAvailable) "Status: Update verfügbar" else "Status: Auf dem neuesten Stand"
    }
} 