package com.deepcore.kiytoapp.update

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.deepcore.kiytoapp.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class UpdateStatusDialog : DialogFragment() {
    companion object {
        private const val ARG_CURRENT_VERSION = "current_version"
        private const val ARG_LATEST_VERSION = "latest_version"
        private const val ARG_UPDATE_AVAILABLE = "update_available"
        private const val ARG_UPDATE_DESCRIPTION = "update_description"

        fun newInstance(
            currentVersion: String,
            latestVersion: String,
            updateAvailable: Boolean,
            updateDescription: String?
        ): UpdateStatusDialog {
            return UpdateStatusDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_CURRENT_VERSION, currentVersion)
                    putString(ARG_LATEST_VERSION, latestVersion)
                    putBoolean(ARG_UPDATE_AVAILABLE, updateAvailable)
                    putString(ARG_UPDATE_DESCRIPTION, updateDescription)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val currentVersion = requireArguments().getString(ARG_CURRENT_VERSION)
        val latestVersion = requireArguments().getString(ARG_LATEST_VERSION)
        val updateAvailable = requireArguments().getBoolean(ARG_UPDATE_AVAILABLE)
        val updateDescription = requireArguments().getString(ARG_UPDATE_DESCRIPTION)

        val message = StringBuilder().apply {
            append(getString(R.string.current_version, currentVersion))
            append("\n\n")
            append(getString(R.string.latest_version, latestVersion))
            append("\n\n")
            append(
                if (updateAvailable) {
                    getString(R.string.update_available_message)
                } else {
                    getString(R.string.no_update_available_message)
                }
            )
            updateDescription?.let {
                append("\n\n")
                append(getString(R.string.update_description))
                append("\n")
                append(it)
            }
        }.toString()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.update_status)
            .setMessage(message)
            .setPositiveButton(R.string.ok, null)
            .create()
    }
} 