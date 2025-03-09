package com.deepcore.kiytoapp.base

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.settings.NotificationSettingsManager
import com.deepcore.kiytoapp.util.BlurTransformation

/**
 * Basis-Fragment, das von allen Fragments der App erweitert werden sollte.
 * Implementiert gemeinsame Funktionalitäten wie das Setzen des Hintergrundbilds.
 */
open class BaseFragment : Fragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Setze den Hintergrund sofort, um Flackern zu vermeiden
        updateBackgroundImage(view)
    }

    override fun onResume() {
        super.onResume()
        // Aktualisiere den Hintergrund, falls er sich geändert hat
        view?.let { updateBackgroundImage(it) }
    }

    /**
     * Aktualisiert das Hintergrundbild des Fragments basierend auf den Benutzereinstellungen.
     * Wird automatisch in onViewCreated und onResume aufgerufen, kann aber auch manuell aufgerufen werden.
     */
    protected fun updateBackgroundImage(view: View) {
        try {
            val context = context ?: return
            val notificationSettingsManager = NotificationSettingsManager(context)
            
            // Setze zuerst das Standard-Hintergrundbild, um sicherzustellen, dass immer ein Hintergrund vorhanden ist
            view.setBackgroundResource(R.drawable.default_focus_background)
            
            notificationSettingsManager.backgroundImagePath?.let { path ->
                try {
                    val uri = android.net.Uri.parse(path)
                    
                    Glide.with(context.applicationContext) // Verwende applicationContext, um Lifecycle-Probleme zu vermeiden
                        .load(uri)
                        .transform(BlurTransformation(25, 3))
                        .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                            override fun onResourceReady(
                                resource: android.graphics.drawable.Drawable,
                                transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                            ) {
                                if (isAdded && !isDetached) { // Prüfe, ob das Fragment noch angehängt ist
                                    view.background = resource
                                }
                            }

                            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                if (isAdded && !isDetached) { // Prüfe, ob das Fragment noch angehängt ist
                                    view.setBackgroundResource(R.drawable.default_focus_background)
                                }
                            }
                        })
                } catch (e: Exception) {
                    Log.e("BaseFragment", "Fehler beim Laden des Hintergrundbilds", e)
                }
            }
        } catch (e: Exception) {
            Log.e("BaseFragment", "Fehler beim Aktualisieren des Hintergrundbilds", e)
        }
    }

    override fun onDestroyView() {
        try {
            // Glide-Anfragen bereinigen, um Speicherlecks zu vermeiden
            context?.applicationContext?.let {
                Glide.with(it).clear(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                    ) {
                        // Nichts zu tun
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                        // Nichts zu tun
                    }
                })
            }
        } catch (e: Exception) {
            Log.e("BaseFragment", "Fehler beim Bereinigen von Glide", e)
        }
        
        super.onDestroyView()
    }
} 