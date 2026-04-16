package com.deepcore.kiytoapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.deepcore.kiytoapp.utils.PermissionManager
import com.deepcore.kiytoapp.auth.AuthManager
import com.deepcore.kiytoapp.base.BaseActivity

class WelcomeActivity : BaseActivity() {
    private lateinit var welcomeAnimation: LottieAnimationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Statusbar Farbe auf Schwarz setzen
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = getColor(R.color.black)
        
        setContentView(R.layout.activity_welcome)

        welcomeAnimation = findViewById(R.id.welcomeAnimation)
        
        welcomeAnimation.apply {
            setAnimation(R.raw.wilkommen_animation)  // Animation aus dem raw Ordner
            playAnimation()
            repeatCount = LottieDrawable.INFINITE
            repeatMode = LottieDrawable.RESTART
        }

        findViewById<MaterialButton>(R.id.btnGetStarted).setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun checkAndRequestPermissions() {
        if (PermissionManager.shouldShowRationale(this)) {
            showPermissionRationaleDialog()
        } else {
            PermissionManager.checkAndRequestPermissions(this)
            proceedToMainActivity()
        }
    }

    private fun showPermissionRationaleDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Berechtigungen erforderlich")
            .setMessage("Diese App benötigt verschiedene Berechtigungen, um optimal zu funktionieren:\n\n" +
                    "• Kamera: Für Dokumentenscans und Aufgabenerfassung\n" +
                    "• Mikrofon: Für Sprachbefehle und Audionotizen\n" +
                    "• Kalender: Für die Synchronisation von Terminen\n" +
                    "• Speicher: Für das Speichern von Dokumenten und Medien\n" +
                    "• Benachrichtigungen: Für wichtige Erinnerungen\n\n" +
                    "Bitte gewähren Sie diese Berechtigungen im nächsten Schritt.")
            .setPositiveButton("Verstanden") { _, _ ->
                PermissionManager.checkAndRequestPermissions(this)
                proceedToMainActivity()
            }
            .setCancelable(false)
            .show()
    }

    private fun proceedToMainActivity() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PermissionManager.PERMISSION_REQUEST_CODE) {
            proceedToMainActivity()
        }
    }
} 