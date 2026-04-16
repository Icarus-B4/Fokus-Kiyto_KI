package com.deepcore.kiytoapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.deepcore.kiytoapp.base.BaseFragment
import com.deepcore.kiytoapp.databinding.FragmentHintsBinding

class HintsFragment : BaseFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_hints, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Die Hinweise sind bereits im Layout definiert
    }

    companion object {
        fun newInstance() = HintsFragment()
    }
} 