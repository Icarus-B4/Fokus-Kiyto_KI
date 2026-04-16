package com.deepcore.kiytoapp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deepcore.kiytoapp.services.OpenAIService
import com.deepcore.kiytoapp.services.YouTubeService
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class VideoSummaryActivity : AppCompatActivity() {
    private lateinit var urlInput: TextInputEditText
    private lateinit var continueButton: MaterialButton
    private lateinit var summaryTextView: TextView
    private val youtubeService by lazy { YouTubeService(this) }
    private val openAIService by lazy { OpenAIService(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_summary)
        initializeViews()
        setupListeners()
    }

    private fun initializeViews() {
        urlInput = findViewById(R.id.urlInput)
        continueButton = findViewById(R.id.continueButton)
        summaryTextView = findViewById(R.id.textView)
        summaryTextView.text = """
            # YouTube Video Transkription

            Die YouTube Video Transkription ist ein Dienst, der gesprochene Sprache aus YouTube-Videos in geschriebenen Text umwandelt. 

            ### Hauptmerkmale:

            - Automatische Spracherkennung: Konvertiert gesprochene Worte in Text
            - Mehrsprachige Unterstützung: Erkennt und transkribiert verschiedene Sprachen
            - Zeitstempel: Synchronisiert Text mit dem entsprechenden Videoabschnitt
            - Suchbarkeit: Macht Videoinhalte durchsuchbar
            - Barrierefreiheit: Ermöglicht gehörlosen Menschen den Zugang zu Videoinhalten

            ### Anwendungsfälle:

            - Erstellung von Untertiteln
            - Inhaltliche Zusammenfassungen
            - SEO-Optimierung
            - Bildungszwecke
            - Content-Analyse

            Diese Technologie ist besonders nützlich für Content Creator, Bildungseinrichtungen und Unternehmen, die ihre Videos zugänglicher und besser auffindbar machen möchten.
        """.trimIndent()
    }

    private fun setupListeners() {
        continueButton.setOnClickListener {
            val url = urlInput.text?.toString()
            if (url.isNullOrBlank()) {
                showError(getString(R.string.error_invalid_url))
                return@setOnClickListener
            }
            processYouTubeUrl(url)
        }
    }

    private fun processYouTubeUrl(url: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val videoId = youtubeService.extractVideoId(url)
                val videoInfo = youtubeService.getVideoInfo(videoId)
                val summary = openAIService.generateVideoSummary(videoInfo)
                
                showSummary(summary, videoInfo.title, videoInfo.videoUrl)
            } catch (e: IllegalStateException) {
                showError(e.message ?: getString(R.string.error_network))
            } catch (e: Exception) {
                showError(e.message ?: getString(R.string.error_network))
            } finally {
                showLoading(false)
            }
        }
    }

    private fun showLoading(isLoading: Boolean) {
        continueButton.isEnabled = !isLoading
        continueButton.text = getString(
            if (isLoading) R.string.loading_summary else R.string.continue_text
        )
    }

    private fun showError(message: String) {
        Snackbar.make(
            findViewById<View>(android.R.id.content),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun showSummary(summary: String, title: String, videoUrl: String) {
        startActivity(ResultsActivity.createIntent(
            this,
            summary,
            title,
            videoUrl
        ))
    }
}