package com.deepcore.kiytoapp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.deepcore.kiytoapp.databinding.ActivityAboutMeBinding

class AboutMeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutMeBinding
    private val handler = Handler(Looper.getMainLooper())
    private val typewriterText = "Hello World! Ich bin Edgar Cuppari, ein Android-Entwickler. Willkommen auf meiner Profilseite!"
    private var typewriterIndex = 0
    private val typewriterDelay = 50L // Millisekunden zwischen den Buchstaben

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutMeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupClickListeners()
        startTypewriterAnimation()
    }

    private fun setupUI() {
        // Lottie Animation aus dem raw Ordner laden
        binding.lottieAnimation.setAnimation(R.raw.developer_animation)
        
        // Entwickler Informationen setzen
        binding.developerName.text = "Edgar Cuppari"
        binding.emailValue.text = "cupparikun@gmail.com"
        binding.locationValue.text = "Berlin, Deutschland"
        
        // Toolbar einrichten
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.contact_us)
    }

    private fun setupClickListeners() {
        // GitHub Link
        binding.githubLink.setOnClickListener {
            openUrl("https://github.com/Icarus-B4")
        }

        // landing page Link
        binding.linkedinLink.setOnClickListener {
            openUrl("https://fox-blog-social.vercel.app/portfolio")
        }

        // X Link (ehemals Twitter)
        binding.twitterLink.setOnClickListener {
            openUrl("https://x.com/cupparikun")
        }

        // E-Mail Link
        binding.emailValue.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:${binding.emailValue.text}")
            }
            startActivity(Intent.createChooser(intent, "E-Mail senden"))
        }
    }

    private fun startTypewriterAnimation() {
        typewriterIndex = 0
        binding.typewriterText.text = ""
        animateTypewriter()
    }

    private fun animateTypewriter() {
        if (typewriterIndex < typewriterText.length) {
            binding.typewriterText.append(typewriterText[typewriterIndex].toString())
            typewriterIndex++
            handler.postDelayed(::animateTypewriter, typewriterDelay)
        } else {
            // Animation ist fertig, wir können hier einen blinkenden Cursor hinzufügen
            handler.postDelayed({
                if (binding.typewriterText.text.endsWith("_")) {
                    binding.typewriterText.text = binding.typewriterText.text.dropLast(1)
                } else {
                    binding.typewriterText.append("_")
                }
                handler.postDelayed(::animateTypewriter, 500) // Blinken alle 500ms
            }, 500)
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Handler-Callbacks entfernen, um Memory Leaks zu vermeiden
        handler.removeCallbacksAndMessages(null)
    }
} 