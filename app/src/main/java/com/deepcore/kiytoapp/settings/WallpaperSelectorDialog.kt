package com.deepcore.kiytoapp.settings

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.util.LogUtils

class WallpaperSelectorDialog : DialogFragment() {
    
    interface WallpaperSelectedListener {
        fun onWallpaperSelected(wallpaperId: Int)
    }
    
    private var listener: WallpaperSelectedListener? = null
    
    fun setWallpaperSelectedListener(listener: WallpaperSelectedListener) {
        this.listener = listener
    }
    
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        return dialog
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        LogUtils.debug(this, "onCreateView aufgerufen")
        return inflater.inflate(R.layout.dialog_wallpaper_selector, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogUtils.debug(this, "onViewCreated aufgerufen")
        
        // Titel setzen
        view.findViewById<TextView>(R.id.wallpaper_title).text = getString(R.string.select_wallpaper)
        
        // Wallpaper-Optionen einrichten
        setupWallpaperOption(view, R.id.wallpaper_dark_red, R.drawable.bg_dark_red, R.id.wallpaper_preview_red)
        setupWallpaperOption(view, R.id.wallpaper_black_yellow, R.drawable.bg_black_yellow, R.id.wallpaper_preview_yellow)
        setupWallpaperOption(view, R.id.wallpaper_black_dark, R.drawable.bg_black_dark, R.id.wallpaper_preview_black)
        setupWallpaperOption(view, R.id.wallpaper_dark_blue, R.drawable.bg_dark_blue, R.id.wallpaper_preview_blue)
        setupWallpaperOption(view, R.id.wallpaper_dark_purple, R.drawable.bg_dark_purple, R.id.wallpaper_preview_purple)
        setupWallpaperOption(view, R.id.wallpaper_standard_theme, R.drawable.standard_them_bg_dark, R.id.wallpaper_preview_standard)
        
        // Abbrechen-Button
        view.findViewById<TextView>(R.id.wallpaper_cancel).setOnClickListener {
            LogUtils.debug(this, "Abbrechen-Button geklickt")
            dismiss()
        }
    }
    
    private fun setupWallpaperOption(view: View, viewId: Int, drawableId: Int, previewId: Int) {
        view.findViewById<LinearLayout>(viewId).apply {
            // Hintergrund setzen
            findViewById<ImageView>(previewId).setBackgroundResource(drawableId)
            
            // Click-Listener
            setOnClickListener {
                LogUtils.debug(this@WallpaperSelectorDialog, "Wallpaper ausgewählt: $drawableId")
                listener?.onWallpaperSelected(drawableId)
                dismiss()
            }
        }
    }
    
    companion object {
        fun newInstance(): WallpaperSelectorDialog {
            return WallpaperSelectorDialog()
        }
    }
} 