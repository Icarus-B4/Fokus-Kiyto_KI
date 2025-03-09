package com.deepcore.kiytoapp.ai

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.base.BaseActivity
import com.deepcore.kiytoapp.data.TaskManager
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AIRecommendationsActivity : BaseActivity() {
    private lateinit var taskManager: TaskManager
    private lateinit var recommendationsRecyclerView: RecyclerView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var noDataText: TextView
    private lateinit var productivityCard: MaterialCardView
    private lateinit var focusCard: MaterialCardView
    private lateinit var taskOptimizationCard: MaterialCardView
    private lateinit var productivityAnalysisText: TextView
    private lateinit var focusRecommendationsText: TextView
    private lateinit var taskOptimizationText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_recommendations)

        // Aktiviere Up-Navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.ai_recommendations)
        
        // Setze die Hintergrundfarbe der ActionBar auf Schwarz
        supportActionBar?.setBackgroundDrawable(ColorDrawable(Color.parseColor("#0E0D0D")))

        initializeViews()
        setupTaskManager()
        loadRecommendations()
    }

    private fun initializeViews() {
        recommendationsRecyclerView = findViewById(R.id.recommendationsRecyclerView)
        loadingProgressBar = findViewById(R.id.loadingProgressBar)
        noDataText = findViewById(R.id.noDataText)
        productivityCard = findViewById(R.id.productivityCard)
        focusCard = findViewById(R.id.focusCard)
        taskOptimizationCard = findViewById(R.id.taskOptimizationCard)
        productivityAnalysisText = findViewById(R.id.productivityAnalysisText)
        focusRecommendationsText = findViewById(R.id.focusRecommendationsText)
        taskOptimizationText = findViewById(R.id.taskOptimizationText)

        recommendationsRecyclerView.layoutManager = LinearLayoutManager(this)
        recommendationsRecyclerView.adapter = RecommendationsAdapter(emptyList())
    }

    private fun setupTaskManager() {
        taskManager = TaskManager(this)
    }

    private fun loadRecommendations() {
        lifecycleScope.launch {
            try {
                loadingProgressBar.visibility = View.VISIBLE
                noDataText.visibility = View.GONE

                // Lade Aufgaben und analysiere sie
                val tasks = taskManager.getAllTasks().first()
                if (tasks.isEmpty()) {
                    showNoDataView()
                    return@launch
                }

                // Analysiere Aufgabenmuster und generiere Empfehlungen
                val analysis = TaskAnalyzer.analyzeTaskPatterns(tasks)
                
                // UI aktualisieren
                loadingProgressBar.visibility = View.GONE
                updateRecommendationCards(analysis)

            } catch (e: Exception) {
                Log.e("AIRecommendations", "Fehler beim Laden der Empfehlungen", e)
                showError()
            }
        }
    }

    private fun updateRecommendationCards(analysis: TaskAnalysis) {
        // Produktivitäts-Score
        val productivityPercentage = (analysis.productivityScore * 100).toInt()
        productivityAnalysisText.text = "Ihre Produktivität liegt bei $productivityPercentage%. " +
                                      "Basierend auf Ihren abgeschlossenen und offenen Aufgaben."

        // Fokus-Empfehlungen
        focusRecommendationsText.text = analysis.focusRecommendation

        // Aufgabenoptimierung
        taskOptimizationText.text = analysis.taskOptimizationTips

        // Detaillierte Empfehlungen in RecyclerView
        (recommendationsRecyclerView.adapter as RecommendationsAdapter)
            .updateRecommendations(analysis.detailedRecommendations)
    }

    private fun showNoDataView() {
        loadingProgressBar.visibility = View.GONE
        noDataText.visibility = View.VISIBLE
        noDataText.text = getString(R.string.no_tasks_yet)
    }

    private fun showError() {
        loadingProgressBar.visibility = View.GONE
        noDataText.visibility = View.VISIBLE
        noDataText.text = getString(R.string.error_loading_recommendations)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}

class RecommendationsAdapter(
    private var recommendations: List<String>
) : RecyclerView.Adapter<RecommendationsAdapter.ViewHolder>() {

    class ViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val textView = TextView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 8, 16, 8)
        }
        return ViewHolder(textView)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = recommendations[position]
    }

    override fun getItemCount() = recommendations.size

    fun updateRecommendations(newRecommendations: List<String>) {
        recommendations = newRecommendations
        notifyDataSetChanged()
    }
} 