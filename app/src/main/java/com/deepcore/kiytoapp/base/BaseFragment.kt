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
            
            // Aktualisiere die Toolbar mit dem Standard-Hintergrund
            updateToolbarBackground(R.drawable.default_focus_background)
            
            notificationSettingsManager.backgroundImagePath?.let { path ->
                try {
                    // Prüfe, ob es sich um eine Drawable-Ressource handelt
                    if (path.startsWith("drawable://")) {
                        val resourceId = path.substringAfter("drawable://").toInt()
                        view.setBackgroundResource(resourceId)
                        
                        // Aktualisiere die Toolbar mit dem ausgewählten Hintergrund
                        updateToolbarBackground(resourceId)
                        
                        return@let
                    }
                    
                    // Es handelt sich um einen URI
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
                                    
                                    // Aktualisiere die Toolbar mit dem ausgewählten Hintergrund
                                    updateToolbarWithDrawable(resource)
                                }
                            }

                            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                if (isAdded && !isDetached) { // Prüfe, ob das Fragment noch angehängt ist
                                    view.setBackgroundResource(R.drawable.default_focus_background)
                                    
                                    // Aktualisiere die Toolbar mit dem Standard-Hintergrund
                                    updateToolbarBackground(R.drawable.default_focus_background)
                                }
                            }
                        })
                } catch (e: Exception) {
                    Log.e("BaseFragment", "Fehler beim Laden des Hintergrundbilds: ${e.message}", e)
                    // Bei einem Fehler setzen wir den Standard-Hintergrund
                    view.setBackgroundResource(R.drawable.default_focus_background)
                    
                    // Aktualisiere die Toolbar mit dem Standard-Hintergrund
                    updateToolbarBackground(R.drawable.default_focus_background)
                }
            }
        } catch (e: Exception) {
            Log.e("BaseFragment", "Fehler beim Aktualisieren des Hintergrundbilds", e)
        }
    }

    /**
     * Aktualisiert die Toolbar mit dem angegebenen Hintergrund-Ressourcen-ID.
     */
    protected fun updateToolbarBackground(resourceId: Int) {
        try {
            val activity = activity as? androidx.appcompat.app.AppCompatActivity ?: return
            activity.supportActionBar?.let { actionBar ->
                actionBar.setBackgroundDrawable(resources.getDrawable(resourceId, activity.theme))
            }
            
            // Setze die Statusleiste immer auf schwarz
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                activity.window.statusBarColor = resources.getColor(android.R.color.black, activity.theme)
            }
        } catch (e: Exception) {
            Log.e("BaseFragment", "Fehler beim Aktualisieren der Toolbar", e)
        }
    }

    /**
     * Aktualisiert die Toolbar mit dem angegebenen Drawable.
     */
    protected fun updateToolbarWithDrawable(drawable: android.graphics.drawable.Drawable) {
        try {
            val activity = activity as? androidx.appcompat.app.AppCompatActivity ?: return
            val newDrawable = drawable.constantState?.newDrawable()?.mutate()
            if (newDrawable != null) {
                activity.supportActionBar?.setBackgroundDrawable(newDrawable)
            }
            
            // Setze die Statusleiste immer auf schwarz
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                activity.window.statusBarColor = resources.getColor(android.R.color.black, activity.theme)
            }
        } catch (e: Exception) {
            Log.e("BaseFragment", "Fehler beim Aktualisieren der Toolbar mit Drawable", e)
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