package com.deepcore.kiytoapp.update

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.deepcore.kiytoapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class DebugLogDialog : DialogFragment() {
    companion object {
        private const val ARG_LOGS = "logs"

        fun newInstance(logs: String): DebugLogDialog {
            return DebugLogDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_LOGS, logs)
                }
            }
        }
    }
    
    private var testModeListener: (() -> Unit)? = null
    
    fun setTestModeListener(listener: () -> Unit) {
        testModeListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val logs = requireArguments().getString(ARG_LOGS)

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.debug_logs)
            .setMessage(logs)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton("Test-Modus") { _, _ ->
                // Rufe den Test-Modus-Listener auf
                testModeListener?.invoke()
            }
            .create()
    }
} 