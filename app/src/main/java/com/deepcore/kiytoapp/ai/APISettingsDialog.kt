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
        fun showPermissionDeniedDialog()
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
        val geminiApiKeyInput = view.findViewById<EditText>(R.id.geminiApiKeyInput)
        
        // Lade die aktuellen API-Keys
        val currentOpenAIKey = apiSettingsManager.getApiKey()
        val currentGeminiKey = apiSettingsManager.getGeminiApiKey()
        
        // Zeige den aktuellen Status
        if (!currentOpenAIKey.isNullOrEmpty()) {
            apiKeyInput.hint = "OpenAI API-Key ist gesetzt"
        }
        
        if (!currentGeminiKey.isNullOrEmpty()) {
            geminiApiKeyInput.hint = "Gemini API-Key ist gesetzt"
        }

        builder.setView(view)
            .setTitle("KI-Einstellungen")
            .setPositiveButton("Speichern") { _, _ ->
                val openAIKey = apiKeyInput.text.toString().trim()
                val geminiKey = geminiApiKeyInput.text.toString().trim()
                
                var success = true
                
                // OpenAI Key speichern wenn eingegeben
                if (openAIKey.isNotEmpty()) {
                    if (apiSettingsManager.validateApiKey(openAIKey)) {
                        apiSettingsManager.saveApiKey(openAIKey)
                        listener?.onApiKeySet(openAIKey)
                    } else {
                        success = false
                    }
                }
                
                // Gemini Key speichern wenn eingegeben
                if (geminiKey.isNotEmpty()) {
                    if (apiSettingsManager.validateApiKey(geminiKey)) {
                        apiSettingsManager.saveGeminiApiKey(geminiKey)
                        // Trigger einen Refresh falls nötig
                        listener?.onApiKeySet(geminiKey)
                    } else {
                        success = false
                    }
                }
                
                if (!success) {
                    com.google.android.material.snackbar.Snackbar.make(
                        requireActivity().findViewById(android.R.id.content),
                        "Einige API-Keys waren ungültig",
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, "KI-Einstellungen gespeichert", Toast.LENGTH_SHORT).show()
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