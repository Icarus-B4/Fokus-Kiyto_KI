package com.deepcore.kiytoapp

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.deepcore.kiytoapp.base.BaseFragment
import com.deepcore.kiytoapp.data.ProductivityAnalyzer
import com.deepcore.kiytoapp.data.ProductivityAnalyzer.TimeBlock
import com.deepcore.kiytoapp.settings.NotificationReceiver
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Fragment zur Anzeige von Produktivitätsanalysen und Erkenntnissen.
 */
class ProductivityInsightsFragment : BaseFragment() {
    private val TAG = "ProductivityInsights"
    
    private lateinit var productivityAnalyzer: ProductivityAnalyzer
    private lateinit var insightsText: TextView
    private lateinit var productivityBarChart: BarChart
    private lateinit var focusDistributionChart: PieChart
    private lateinit var suggestFocusTimeButton: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        productivityAnalyzer = ProductivityAnalyzer(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_productivity_insights, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Initialisiere Views
        insightsText = view.findViewById(R.id.insightsText)
        productivityBarChart = view.findViewById(R.id.productivityBarChart)
        focusDistributionChart = view.findViewById(R.id.focusDistributionChart)
        suggestFocusTimeButton = view.findViewById(R.id.suggestFocusTimeButton)
        
        // Konfiguriere Charts
        setupBarChart()
        setupPieChart()
        
        // Lade Daten
        loadProductivityData()
        
        // Button-Listener
        suggestFocusTimeButton.setOnClickListener {
            suggestOptimalFocusTime()
        }
    }
    
    private fun setupBarChart() {
        productivityBarChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            legend.isEnabled = true
            legend.textColor = Color.WHITE
            setTouchEnabled(true)
            setDrawGridBackground(false)
            setDrawBorders(false)
            
            // X-Achse konfigurieren
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.WHITE
                setDrawGridLines(false)
                granularity = 1f
            }
            
            // Linke Y-Achse konfigurieren
            axisLeft.apply {
                textColor = Color.WHITE
                axisMinimum = 0f
                axisMaximum = 100f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#20FFFFFF")
            }
            
            // Rechte Y-Achse deaktivieren
            axisRight.isEnabled = false
            
            // Standardtext für leere Daten
            setNoDataText("Keine Daten verfügbar")
            setNoDataTextColor(Color.WHITE)
        }
    }
    
    private fun setupPieChart() {
        focusDistributionChart.apply {
            setBackgroundColor(Color.TRANSPARENT)
            description.isEnabled = false
            legend.isEnabled = true
            legend.textColor = Color.WHITE
            setTouchEnabled(true)
            setDrawEntryLabels(false)
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.TRANSPARENT)
            setTransparentCircleAlpha(0)
            holeRadius = 40f
            
            // Standardtext für leere Daten
            setNoDataText("Keine Daten verfügbar")
            setNoDataTextColor(Color.WHITE)
        }
    }
    
    private fun loadProductivityData() {
        lifecycleScope.launch {
            try {
                // Lade Produktivitätserkenntnisse
                val insights = productivityAnalyzer.getProductivityInsights()
                insightsText.text = insights
                
                // Lade Produktivitätsdaten für das Balkendiagramm
                val productiveBlocks = productivityAnalyzer.getMostProductiveTimeBlocks()
                updateBarChart(productiveBlocks)
                
                // Lade Daten für das Kreisdiagramm
                updatePieChart(productiveBlocks)
                
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Laden der Produktivitätsdaten", e)
                insightsText.text = "Fehler beim Laden der Produktivitätsdaten. Bitte versuche es später erneut."
            }
        }
    }
    
    private fun updateBarChart(productiveBlocks: Map<TimeBlock, Float>) {
        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()
        
        // Sortiere die Zeitblöcke in chronologischer Reihenfolge
        val sortedBlocks = listOf(
            TimeBlock.EARLY_MORNING,
            TimeBlock.MORNING,
            TimeBlock.AFTERNOON,
            TimeBlock.EVENING,
            TimeBlock.NIGHT
        )
        
        // Erstelle Einträge für das Balkendiagramm
        sortedBlocks.forEachIndexed { index, block ->
            val value = productiveBlocks[block] ?: 0f
            entries.add(BarEntry(index.toFloat(), value))
            
            // Füge Labels hinzu
            labels.add(when(block) {
                TimeBlock.EARLY_MORNING -> "5-8 Uhr"
                TimeBlock.MORNING -> "8-12 Uhr"
                TimeBlock.AFTERNOON -> "12-17 Uhr"
                TimeBlock.EVENING -> "17-21 Uhr"
                TimeBlock.NIGHT -> "21-5 Uhr"
            })
        }
        
        // Erstelle den Datensatz
        val dataSet = BarDataSet(entries, "Produktivität").apply {
            colors = listOf(
                Color.parseColor("#4CAF50"),  // Grün
                Color.parseColor("#2196F3"),  // Blau
                Color.parseColor("#FF9800"),  // Orange
                Color.parseColor("#9C27B0"),  // Lila
                Color.parseColor("#F44336")   // Rot
            )
            valueTextColor = Color.WHITE
            valueTextSize = 12f
        }
        
        // Aktualisiere das Diagramm
        productivityBarChart.apply {
            data = BarData(dataSet)
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            animateY(1000)
            invalidate()
        }
    }
    
    private fun updatePieChart(productiveBlocks: Map<TimeBlock, Float>) {
        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()
        
        // Berechne die Gesamtproduktivität
        val totalProductivity = productiveBlocks.values.sum()
        
        // Wenn keine Daten vorhanden sind, zeige Standardwerte
        if (totalProductivity <= 0) {
            entries.add(PieEntry(100f, "Keine Daten"))
            colors.add(Color.GRAY)
        } else {
            // Füge Einträge für jeden Zeitblock hinzu
            TimeBlock.values().forEach { block ->
                val value = productiveBlocks[block] ?: 0f
                if (value > 0) {
                    val percentage = (value / totalProductivity) * 100
                    entries.add(PieEntry(percentage, when(block) {
                        TimeBlock.EARLY_MORNING -> "Früher Morgen"
                        TimeBlock.MORNING -> "Vormittag"
                        TimeBlock.AFTERNOON -> "Nachmittag"
                        TimeBlock.EVENING -> "Abend"
                        TimeBlock.NIGHT -> "Nacht"
                    }))
                    
                    // Füge Farben hinzu
                    colors.add(when(block) {
                        TimeBlock.EARLY_MORNING -> Color.parseColor("#4CAF50")  // Grün
                        TimeBlock.MORNING -> Color.parseColor("#2196F3")        // Blau
                        TimeBlock.AFTERNOON -> Color.parseColor("#FF9800")      // Orange
                        TimeBlock.EVENING -> Color.parseColor("#9C27B0")        // Lila
                        TimeBlock.NIGHT -> Color.parseColor("#F44336")          // Rot
                    })
                }
            }
        }
        
        // Erstelle den Datensatz
        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextColor = Color.WHITE
            valueTextSize = 14f
            sliceSpace = 3f
        }
        
        // Aktualisiere das Diagramm
        focusDistributionChart.apply {
            data = PieData(dataSet)
            legend.textColor = Color.WHITE
            animateY(1000)
            invalidate()
        }
    }
    
    private fun suggestOptimalFocusTime() {
        lifecycleScope.launch {
            try {
                val optimalTime = productivityAnalyzer.suggestOptimalFocusTime()
                
                // Formatiere die Zeit für die Anzeige
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val dateFormat = SimpleDateFormat("EEEE, dd.MM.yyyy", Locale.getDefault())
                
                val timeStr = timeFormat.format(optimalTime)
                val dateStr = dateFormat.format(optimalTime)
                
                // Zeige einen Toast mit der vorgeschlagenen Zeit
                val message = "Optimale Fokuszeit: $timeStr Uhr am $dateStr"
                Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
                
                // Frage den Benutzer, ob er eine Erinnerung für diese Zeit einrichten möchte
                showFocusTimeReminder(optimalTime)
                
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Vorschlagen der optimalen Fokuszeit", e)
                Toast.makeText(
                    requireContext(),
                    "Konnte keine optimale Fokuszeit ermitteln. Bitte versuche es später erneut.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    
    private fun showFocusTimeReminder(focusTime: Date) {
        // Hier könnte ein Dialog angezeigt werden, der fragt, ob eine Erinnerung eingerichtet werden soll
        // Für dieses Beispiel setzen wir direkt eine Erinnerung
        
        val alarmManager = requireContext().getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(requireContext(), NotificationReceiver::class.java).apply {
            action = "FOCUS_TIME_REMINDER"
            putExtra("NOTIFICATION_TITLE", "Zeit für Fokus!")
            putExtra("NOTIFICATION_MESSAGE", "Jetzt ist deine optimale Fokuszeit. Starte eine Fokussession!")
        }
        
        val pendingIntent = PendingIntent.getBroadcast(
            requireContext(),
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // Setze den Alarm für die vorgeschlagene Zeit
        alarmManager.setExact(
            AlarmManager.RTC_WAKEUP,
            focusTime.time,
            pendingIntent
        )
        
        // Informiere den Benutzer
        val calendar = Calendar.getInstance().apply { time = focusTime }
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("EEEE", Locale.getDefault())
        
        Toast.makeText(
            requireContext(),
            "Erinnerung für ${timeFormat.format(focusTime)} Uhr am ${dateFormat.format(focusTime)} eingerichtet",
            Toast.LENGTH_LONG
        ).show()
    }
} 