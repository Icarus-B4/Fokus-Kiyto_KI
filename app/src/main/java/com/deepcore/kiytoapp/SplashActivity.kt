package com.deepcore.kiytoapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import android.os.Handler
import android.os.Looper
import com.deepcore.kiytoapp.auth.AuthManager
import com.deepcore.kiytoapp.base.BaseActivity

@SuppressLint("CustomSplashScreen")
class SplashActivity : BaseActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(com.deepcore.kiytoapp.R.style.Theme_App_Starting)
        
        // Verstecke das App-Icon und setze Flags für einen sauberen Start
        window.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS or
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )
        
        super.onCreate(savedInstanceState)

        // Statusbar und Navigation Bar auf Schwarz setzen
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = getColor(android.R.color.transparent)
        window.navigationBarColor = getColor(android.R.color.transparent)
        
        // Verzögere das Laden des Layouts um sicherzustellen, dass das Theme zuerst angewendet wird
        Handler(Looper.getMainLooper()).postDelayed({
            setContentView(R.layout.activity_splash)
            setupAnimation()
        }, 100)
    }

    private fun setupAnimation() {
        val lottieView = findViewById<LottieAnimationView>(R.id.splashAnimation)
        
        lottieView.setAnimation(R.raw.splash_animation)
        lottieView.repeatCount = 0
        lottieView.playAnimation()
        
        lottieView.addAnimatorListener(object : android.animation.Animator.AnimatorListener {
            override fun onAnimationStart(animation: android.animation.Animator) {}
            override fun onAnimationEnd(animation: android.animation.Animator) {
                startActivity(Intent(this@SplashActivity, WelcomeActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {}
            override fun onAnimationRepeat(animation: android.animation.Animator) {}
        })
    }
} 