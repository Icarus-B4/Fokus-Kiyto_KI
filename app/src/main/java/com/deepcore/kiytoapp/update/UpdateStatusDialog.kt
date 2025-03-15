package com.deepcore.kiytoapp.update

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.deepcore.kiytoapp.BuildConfig
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.util.LogUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class UpdateStatusDialog : DialogFragment() {
    companion object {
        private const val ARG_CURRENT_VERSION = "current_version"
        private const val ARG_LATEST_VERSION = "latest_version"
        private const val ARG_UPDATE_AVAILABLE = "update_available"
        private const val ARG_UPDATE_DESCRIPTION = "update_description"
        private const val ARG_VERSION_COMPARE_RESULT = "version_compare_result"

        fun newInstance(
            currentVersion: String,
            latestVersion: String,
            updateAvailable: Boolean,
            updateDescription: String?,
            versionCompareResult: Int = 0
        ): UpdateStatusDialog {
            return UpdateStatusDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_CURRENT_VERSION, currentVersion)
                    putString(ARG_LATEST_VERSION, latestVersion)
                    putBoolean(ARG_UPDATE_AVAILABLE, updateAvailable)
                    putString(ARG_UPDATE_DESCRIPTION, updateDescription)
                    putInt(ARG_VERSION_COMPARE_RESULT, versionCompareResult)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentVersion = requireArguments().getString(ARG_CURRENT_VERSION)
        val latestVersion = requireArguments().getString(ARG_LATEST_VERSION)
        val updateAvailable = requireArguments().getBoolean(ARG_UPDATE_AVAILABLE)
        val updateDescription = requireArguments().getString(ARG_UPDATE_DESCRIPTION)
        val versionCompareResult = requireArguments().getInt(ARG_VERSION_COMPARE_RESULT, 0)

        LogUtils.debug(this, "Erstelle Update-Status Dialog: " +
                "currentVersion=$currentVersion, latestVersion=$latestVersion, " +
                "updateAvailable=$updateAvailable, versionCompareResult=$versionCompareResult")

        val message = StringBuilder().apply {
            append(getString(R.string.current_version, currentVersion))
            append("\n\n")
            append(getString(R.string.latest_version, latestVersion))
            append("\n\n")
            
            if (updateAvailable) {
                append(getString(R.string.update_available_message))
                append("\n\n")
                append(getString(R.string.update_changes_title))
                append("\n")
                
                // Extrahiere die wichtigsten Änderungen aus der Beschreibung
                updateDescription?.let { desc ->
                    val changes = desc.lines()
                        .filter { it.trim().startsWith("-") }
                        .take(5)  // Zeige maximal 5 Änderungen
                        .joinToString("\n") { it.trim() }
                    
                    if (changes.isNotEmpty()) {
                        append(changes)
                    } else {
                        append(getString(R.string.no_changes_available))
                    }
                } ?: append(getString(R.string.no_changes_available))
            } else {
                // Unterscheide zwischen "aktuell" und "neuere lokale Version"
                if (versionCompareResult < 0) {
                    // Lokale Version ist neuer als Remote-Version
                    append(getString(R.string.newer_local_version))
                } else {
                    // Versionen sind gleich
                    append(getString(R.string.no_update_available))
                }
            }
        }.toString()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.update_status)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .create()
    }
} 