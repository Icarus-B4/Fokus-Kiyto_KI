package com.deepcore.kiytoapp.update

import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.databinding.DialogUpdateBinding
import com.deepcore.kiytoapp.util.LogUtils

class UpdateDialog : DialogFragment() {
    private var _binding: DialogUpdateBinding? = null
    private val binding get() = _binding!!

    private var updateDescription: String? = null
    private var updateUrl: String? = null

    companion object {
        fun newInstance(description: String, url: String): UpdateDialog {
            return UpdateDialog().apply {
                updateDescription = description
                updateUrl = url
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogUpdateBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.updateDescription.text = updateDescription
        
        binding.downloadButton.setOnClickListener {
            LogUtils.debug(this, "Download-Button geklickt")
            updateUrl?.let { url ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            }
            dismiss()
        }
        
        binding.laterButton.setOnClickListener {
            LogUtils.debug(this, "Später-Button geklickt")
            dismiss()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return super.onCreateDialog(savedInstanceState).apply {
            window?.setBackgroundDrawableResource(R.drawable.rounded_dialog_background)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 