package com.deepcore.kiytoapp.update

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.airbnb.lottie.LottieAnimationView
import com.deepcore.kiytoapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class UpdateDialog : DialogFragment() {
    companion object {
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_URL = "url"

        fun newInstance(description: String, url: String): UpdateDialog {
            return UpdateDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_DESCRIPTION, description)
                    putString(ARG_URL, url)
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val description = requireArguments().getString(ARG_DESCRIPTION)
        val url = requireArguments().getString(ARG_URL)

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_update, null)

        // Initialisiere Views
        val updateAnimation = view.findViewById<LottieAnimationView>(R.id.updateAnimation)
        val descriptionText = view.findViewById<TextView>(R.id.updateDescriptionText)
        val downloadButton = view.findViewById<MaterialButton>(R.id.downloadButton)
        val remindButton = view.findViewById<MaterialButton>(R.id.remindButton)

        // Setze Beschreibung
        descriptionText.text = description

        // Starte Animation
        updateAnimation.setAnimation(R.raw.update_available)
        updateAnimation.playAnimation()

        // Setze Click-Listener
        downloadButton.setOnClickListener {
            // Öffne den Download-Link
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            startActivity(intent)
            dismiss()
        }

        remindButton.setOnClickListener {
            // Schließe den Dialog
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
            .apply {
                // Setze den Dialog auf nicht abbrechbar
                setCanceledOnTouchOutside(false)
            }
    }
} 