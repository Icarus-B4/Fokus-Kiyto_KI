package com.deepcore.kiytoapp.base

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.settings.NotificationSettingsManager
import com.deepcore.kiytoapp.util.BlurTransformation

/**
 * Basis-Aktivität, die von allen Aktivitäten der App erweitert werden sollte.
 * Implementiert gemeinsame Funktionalitäten wie das Setzen des Hintergrundbilds.
 */
open class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Setze den Hintergrund sofort, um Flackern zu vermeiden
        updateBackgroundImage()
    }

    override fun onResume() {
        super.onResume()
        // Aktualisiere den Hintergrund, falls er sich geändert hat
        updateBackgroundImage()
    }

    /**
     * Aktualisiert das Hintergrundbild der Aktivität basierend auf den Benutzereinstellungen.
     * Wird automatisch in onCreate und onResume aufgerufen, kann aber auch manuell aufgerufen werden.
     */
    protected fun updateBackgroundImage() {
        try {
            val notificationSettingsManager = NotificationSettingsManager(this)
            
            // Setze zuerst das Standard-Hintergrundbild (schwarz transparent)
            window.decorView.findViewById<android.view.View>(android.R.id.content)
                .setBackgroundResource(R.drawable.default_focus_background)
            
            // Setze auch die ActionBar mit dem Standard-Hintergrund
            supportActionBar?.setBackgroundDrawable(resources.getDrawable(R.drawable.default_focus_background, theme))
            
            // Setze die Statusleiste immer auf schwarz
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                window.statusBarColor = resources.getColor(android.R.color.black, theme)
            }
            
            val backgroundPath = notificationSettingsManager.backgroundImagePath
            if (backgroundPath != null) {
                try {
                    // Prüfe, ob es sich um eine Drawable-Ressource handelt
                    if (backgroundPath.startsWith("drawable://")) {
                        val resourceId = backgroundPath.substringAfter("drawable://").toInt()
                        val rootView = window.decorView.findViewById<android.view.View>(android.R.id.content)
                        rootView.setBackgroundResource(resourceId)
                        
                        // Setze auch die ActionBar mit dem ausgewählten Hintergrund
                        supportActionBar?.setBackgroundDrawable(resources.getDrawable(resourceId, theme))
                        
                        // Statusleiste bleibt immer schwarz
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            window.statusBarColor = resources.getColor(android.R.color.black, theme)
                        }
                    } else {
                        // Es handelt sich um einen URI
                        val uri = android.net.Uri.parse(backgroundPath)
                        val rootView = window.decorView.findViewById<android.view.View>(android.R.id.content)
                        
                        Glide.with(applicationContext) // Verwende applicationContext, um Lifecycle-Probleme zu vermeiden
                            .load(uri)
                            .transform(BlurTransformation(25, 3))
                            .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                                override fun onResourceReady(
                                    resource: android.graphics.drawable.Drawable,
                                    transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                                ) {
                                    rootView.background = resource
                                    
                                    // Setze auch die ActionBar mit dem ausgewählten Hintergrund
                                    val newDrawable = resource.constantState?.newDrawable()?.mutate()
                                    if (newDrawable != null) {
                                        supportActionBar?.setBackgroundDrawable(newDrawable)
                                    }
                                    
                                    // Statusleiste bleibt immer schwarz
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        window.statusBarColor = resources.getColor(android.R.color.black, theme)
                                    }
                                }

                                override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                    rootView.setBackgroundResource(R.drawable.default_focus_background)
                                    
                                    // Setze auch die ActionBar mit dem Standard-Hintergrund
                                    supportActionBar?.setBackgroundDrawable(resources.getDrawable(R.drawable.default_focus_background, theme))
                                    
                                    // Statusleiste bleibt immer schwarz
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        window.statusBarColor = resources.getColor(android.R.color.black, theme)
                                    }
                                }
                            })
                    }
                } catch (e: Exception) {
                    Log.e("BaseActivity", "Fehler beim Laden des Hintergrundbilds", e)
                    
                    // Bei einem Fehler setzen wir den Standard-Hintergrund
                    supportActionBar?.setBackgroundDrawable(resources.getDrawable(R.drawable.default_focus_background, theme))
                    
                    // Statusleiste bleibt immer schwarz
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        window.statusBarColor = resources.getColor(android.R.color.black, theme)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("BaseActivity", "Fehler beim Aktualisieren des Hintergrundbilds", e)
        }
    }

    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
        // Füge Übergangsanimationen hinzu
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    override fun startActivity(intent: Intent, options: Bundle?) {
        super.startActivity(intent, options)
        // Füge Übergangsanimationen hinzu
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    override fun finish() {
        super.finish()
        // Füge Übergangsanimationen hinzu
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out)
    }

    override fun onDestroy() {
        try {
            // Glide-Anfragen bereinigen, um Speicherlecks zu vermeiden
            Glide.with(applicationContext).clear(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
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
        } catch (e: Exception) {
            Log.e("BaseActivity", "Fehler beim Bereinigen von Glide", e)
        }
        
        super.onDestroy()
    }
} 