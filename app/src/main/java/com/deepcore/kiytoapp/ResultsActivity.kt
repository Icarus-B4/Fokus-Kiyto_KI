package com.deepcore.kiytoapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deepcore.kiytoapp.data.SummaryDatabase
import com.deepcore.kiytoapp.data.entity.Summary
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class ResultsActivity : AppCompatActivity() {
    private lateinit var toolbar: MaterialToolbar
    private lateinit var summaryText: TextView
    private lateinit var database: SummaryDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_results)
        
        database = SummaryDatabase.getDatabase(this)
        initializeViews()
        setupToolbar()
        displaySummary()
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        summaryText = findViewById(R.id.summaryText)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.summary_results)
        
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun displaySummary() {
        val summary = intent.getStringExtra(EXTRA_SUMMARY) ?: return
        summaryText.text = summary
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_results, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_save -> {
                saveSummary()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun saveSummary() {
        val summary = intent.getStringExtra(EXTRA_SUMMARY) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Neue Zusammenfassung"
        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL)

        val summaryEntity = Summary(
            title = title,
            content = summary,
            videoUrl = videoUrl
        )

        lifecycleScope.launch {
            try {
                database.summaryDao().insert(summaryEntity)
                Snackbar.make(summaryText, R.string.summary_saved, Snackbar.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Snackbar.make(summaryText, R.string.error_saving, Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val EXTRA_SUMMARY = "extra_summary"
        private const val EXTRA_TITLE = "extra_title"
        private const val EXTRA_VIDEO_URL = "extra_video_url"

        fun createIntent(context: Context, summary: String, title: String? = null, videoUrl: String? = null): Intent {
            return Intent(context, ResultsActivity::class.java).apply {
                putExtra(EXTRA_SUMMARY, summary)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_VIDEO_URL, videoUrl)
            }
        }
    }
} 