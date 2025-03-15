package com.deepcore.kiytoapp.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log

class UpdateCompletionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_PACKAGE_FULLY_REMOVED) {
            val packageName = intent.data?.schemeSpecificPart
            if (packageName == "com.deepcore.kiytoapp") {
                // Hole die gespeicherte Update-URL
                val prefs = context.getSharedPreferences("update_prefs", Context.MODE_PRIVATE)
                val updateUrl = prefs.getString("pending_update_url", null)
                
                if (updateUrl != null) {
                    // Warte kurz und öffne dann die GitHub-Seite
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(updateUrl))
                            browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(browserIntent)
                            
                            // Lösche die gespeicherte URL
                            prefs.edit().remove("pending_update_url").apply()
                        } catch (e: Exception) {
                            Log.e("UpdateReceiver", "Fehler beim Öffnen des Browsers", e)
                        }
                    }, 1000)
                }
            }
        }
    }
} 