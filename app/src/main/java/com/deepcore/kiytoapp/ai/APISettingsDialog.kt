package com.deepcore.kiytoapp.ai

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.deepcore.kiytoapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

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
        
        val geminiTestButton = view.findViewById<Button>(R.id.geminiTestButton)
        
        // Lade den aktuellen Gemini-Key
        val currentGeminiKey = apiSettingsManager.getGeminiApiKey()
        
        // Test-Button Logik
        geminiTestButton.setOnClickListener {
            val inputKey = geminiApiKeyInput.text.toString().trim()
            val keyToTest = if (inputKey.isNotEmpty()) inputKey else currentGeminiKey
            
            if (keyToTest.isNullOrEmpty()) {
                Toast.makeText(requireContext(), "Bitte zuerst einen API-Key eingeben", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Temporär initialisieren für den Test
            GeminiService.initialize(requireContext()) 
            
            lifecycleScope.launch {
                geminiTestButton.isEnabled = false
                geminiTestButton.text = "Teste..."
                
                val (success, message) = GeminiService.testApiConnection()
                
                geminiTestButton.isEnabled = true
                geminiTestButton.text = "Verbindung testen"
                
                com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle(if (success) "Verbindung OK" else "Verbindungsfehler")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        
        // Zeige den aktuellen Status
        if (!currentGeminiKey.isNullOrEmpty()) {
            geminiApiKeyInput.hint = "Gemini API-Key ist gesetzt"
        }

        builder.setView(view)
            .setTitle("KI-Einstellungen")
            .setPositiveButton("Speichern") { _, _ ->
                val geminiKey = geminiApiKeyInput.text.toString().trim()
                
                var success = true
                
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
                        view,
                        getString(R.string.api_key_invalid),
                        com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(context, getString(R.string.api_settings_updated), Toast.LENGTH_SHORT).show()
                    dismiss()
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