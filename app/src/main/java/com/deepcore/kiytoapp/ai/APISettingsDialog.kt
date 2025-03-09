package com.deepcore.kiytoapp.ai

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.deepcore.kiytoapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class APISettingsDialog : DialogFragment() {
    
    interface OnApiKeySetListener {
        fun onApiKeySet(apiKey: String)
    }
    
    private var listener: OnApiKeySetListener? = null
    private lateinit var apiSettingsManager: APISettingsManager
    
    override fun onAttach(context: Context) {
        super.onAttach(context)
        apiSettingsManager = APISettingsManager(context)
        try {
            // Versuche zuerst das übergeordnete Fragment als Listener zu finden
            val parentFragment = parentFragment
            if (parentFragment is OnApiKeySetListener) {
                listener = parentFragment
            } else {
                // Fallback zur Activity
                try {
                    listener = context as OnApiKeySetListener
                } catch (e: ClassCastException) {
                    Log.w(TAG, "Weder Fragment noch Activity implementieren OnApiKeySetListener")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Setzen des Listeners", e)
        }
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_api_settings, null)
        
        val apiKeyInput = view.findViewById<EditText>(R.id.apiKeyInput)
        
        // Lade den aktuellen API-Key
        val currentApiKey = apiSettingsManager.getApiKey()
        
        // Zeige den aktuellen API-Key Status
        if (!currentApiKey.isNullOrEmpty()) {
            apiKeyInput.hint = "API-Key ist gesetzt"
        } else {
            apiKeyInput.hint = "Bitte API-Key eingeben"
        }

        builder.setView(view)
            .setTitle("API-Einstellungen")
            .setPositiveButton("Speichern") { _, _ ->
                val apiKey = apiKeyInput.text.toString().trim()
                if (apiKey.isNotEmpty() && apiSettingsManager.validateApiKey(apiKey)) {
                    // Speichere den API-Key
                    apiSettingsManager.saveApiKey(apiKey)
                    
                    // Benachrichtige den Listener
                    listener?.onApiKeySet(apiKey)
                } else {
                    // Zeige Fehlermeldung wenn der API-Key ungültig ist
                    com.google.android.material.snackbar.Snackbar.make(
                        requireActivity().findViewById(android.R.id.content),
                        "Ungültiger API-Key",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show()
                }
            }
            .setNegativeButton("Abbrechen", null)

        return builder.create()
    }

    companion object {
        private const val TAG = "APISettingsDialog"
        fun newInstance() = APISettingsDialog()
    }
} 