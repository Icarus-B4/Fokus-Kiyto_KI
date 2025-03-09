package com.deepcore.kiytoapp

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.deepcore.kiytoapp.ai.ChatManager
import com.deepcore.kiytoapp.ai.ChatMessage
import com.deepcore.kiytoapp.data.FocusSession
import com.deepcore.kiytoapp.data.SessionManager
import com.deepcore.kiytoapp.data.Task
import com.deepcore.kiytoapp.data.TaskManager
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class StatisticsFragment : Fragment() {
    private lateinit var taskManager: TaskManager
    private lateinit var sessionManager: SessionManager
    private lateinit var focusTimeText: TextView
    private lateinit var completedTasksText: TextView
    private lateinit var aiUsageText: TextView
    private lateinit var aiTypeText: TextView
    private lateinit var activeDaysText: TextView
    private lateinit var productivityChart: BarChart
    private lateinit var completedTasksChart: LineChart
    private var updateJob: Job? = null
    private var lastFocusTime: Int = 0
    private var lastCompletedTasks: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_statistics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        taskManager = TaskManager(requireContext())
        sessionManager = SessionManager(requireContext())
        
        initializeViews(view)
        setupCharts()
        
        // Sofort initial laden
        lifecycleScope.launch {
            loadStatistics()
            startPeriodicUpdates()
        }
    }

    private fun initializeViews(view: View) {
        focusTimeText = view.findViewById(R.id.focusTimeText)
        completedTasksText = view.findViewById(R.id.completedTasksText)
        aiUsageText = view.findViewById(R.id.aiUsageText)
        aiTypeText = view.findViewById(R.id.aiTypeText)
        activeDaysText = view.findViewById(R.id.activeDaysText)
        productivityChart = view.findViewById(R.id.productivityChart)
        completedTasksChart = view.findViewById(R.id.completedTasksChart)
    }

    private fun setupCharts() {
        // Grundeinstellungen für Produktivitätschart
        productivityChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setTouchEnabled(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.WHITE
                textSize = 10f
                setDrawGridLines(false)
                setDrawAxisLine(false)
            }
            
            axisLeft.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                granularity = 20f
                textColor = Color.WHITE
                textSize = 10f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#333333")
                setDrawAxisLine(false)
            }
            
            axisRight.isEnabled = false
        }

        // Grundeinstellungen für Aufgabenchart
        completedTasksChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawGridBackground(false)
            setDrawBorders(false)
            setTouchEnabled(false)
            
            xAxis.apply {
                position = XAxis.XAxisPosition.BOTTOM
                textColor = Color.WHITE
                textSize = 10f
                setDrawGridLines(false)
                setDrawAxisLine(false)
            }
            
            axisLeft.apply {
                axisMinimum = 0f
                granularity = 1f
                textColor = Color.WHITE
                textSize = 10f
                setDrawGridLines(true)
                gridColor = Color.parseColor("#333333")
                setDrawAxisLine(false)
            }
            
            axisRight.isEnabled = false
        }
    }

    private fun startPeriodicUpdates() {
        updateJob?.cancel()
        updateJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    // Fokuszeit direkt abrufen und aktualisieren
                    val focusTime = sessionManager.getTotalFocusTimeForToday()
                    
                    // Nur aktualisieren wenn sich die Werte geändert haben
                    if (focusTime != lastFocusTime) {
                        lastFocusTime = focusTime
                        withContext(Dispatchers.Main) {
                            updateFocusTimeDisplay(focusTime)
                            updateProductivityChart()
                        }
                    }
                    
                    delay(1000) // Jede Sekunde prüfen
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler bei der periodischen Aktualisierung", e)
                }
            }
        }
    }

    private fun updateFocusTimeDisplay(focusTime: Int) {
        focusTimeText.text = if (focusTime == 0) {
            "0 min"
        } else {
            val hours = focusTime / 60
            val minutes = focusTime % 60
            if (hours > 0) {
                "${hours}h ${minutes}min"
            } else {
                "$minutes min"
            }
        }
    }

    private fun loadStatistics() {
        lifecycleScope.launch {
            try {
                val focusTime = sessionManager.getTotalFocusTimeForToday()
                val tasks = taskManager.getAllTasks().first()
                val completedTasks = tasks.count { it.completed }
                
                // KI-Nutzungsstatistiken laden
                val chatManager = ChatManager(requireContext())
                val messages = chatManager.getMessages()
                val totalAiInteractions = messages.count { !it.isUser }
                val localResponses = messages.count { !it.isUser && isLocalResponse(it.text) }
                val openAiResponses = totalAiInteractions - localResponses
                
                withContext(Dispatchers.Main) {
                    updateFocusTimeDisplay(focusTime)
                    completedTasksText.text = "$completedTasks Aufgaben"
                    
                    // KI-Nutzung anzeigen
                    aiUsageText.text = "$totalAiInteractions Interaktionen"
                    aiTypeText.text = "Lokal: $localResponses | OpenAI: $openAiResponses"
                    
                    val activeDays = calculateActiveDays()
                    activeDaysText.text = "$activeDays aktive Tage"

                    // Initial einmal die Charts mit Animation laden
                    updateProductivityChart(true)
                    updateCompletedTasksChart(true)
                }
                
                lastFocusTime = focusTime
                lastCompletedTasks = completedTasks
                
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Laden der Statistiken", e)
            }
        }
    }

    private fun calculateActiveDays(): Int {
        val sessions = sessionManager.getFocusSessions()
        val calendar = Calendar.getInstance()
        val today = calendar.timeInMillis
        var activeDays = 0
        
        // Prüfe die letzten 30 Tage
        for (i in 0..29) {
            calendar.timeInMillis = today
            calendar.add(Calendar.DAY_OF_YEAR, -i)
            val dayStart = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }.timeInMillis
            
            val dayEnd = calendar.apply {
                set(Calendar.HOUR_OF_DAY, 23)
                set(Calendar.MINUTE, 59)
                set(Calendar.SECOND, 59)
            }.timeInMillis
            
            // Prüfe ob es Sessions oder abgeschlossene Tasks an diesem Tag gab
            val hasActivity = sessions.any { session ->
                session.startTime.time in dayStart..dayEnd
            } || sessionManager.getTotalFocusTimeForDate(dayStart) > 0
            
            if (hasActivity) activeDays++
        }
        
        return activeDays
    }

    private fun updateProductivityChart(animate: Boolean = false) {
        lifecycleScope.launch {
            try {
                val entries = ArrayList<BarEntry>()
                val colors = ArrayList<Int>()
                val labels = ArrayList<String>()
                val dateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
                
                val calendar = Calendar.getInstance()
                val allTasks = taskManager.getAllTasks().first()
                val sessions = sessionManager.getFocusSessions()
                
                for (i in 6 downTo 0) {
                    calendar.add(Calendar.DAY_OF_YEAR, -i)
                    val date = calendar.time
                    val dayProductivity = calculateDayProductivity(date, allTasks, sessions)
                    
                    entries.add(BarEntry((6-i).toFloat(), dayProductivity))
                    colors.add(getColorForProductivity(dayProductivity))
                    labels.add(dateFormat.format(date))
                    
                    calendar.add(Calendar.DAY_OF_YEAR, i)
                }
                
                withContext(Dispatchers.Main) {
                    val dataSet = BarDataSet(entries, "").apply {
                        setColors(colors)
                        valueTextColor = Color.WHITE
                        valueTextSize = 10f
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                return "${value.toInt()}%"
                            }
                        }
                    }
                    
                    productivityChart.apply {
                        data = BarData(dataSet).apply {
                            barWidth = 0.5f
                        }
                        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                        
                        if (animate) {
                            animateY(1000)
                        }
                        invalidate()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Aktualisieren des Produktivitätscharts", e)
            }
        }
    }

    private fun getColorForProductivity(productivity: Float): Int {
        return when {
            productivity == 0f -> Color.parseColor("#424242") // Grau für keine Aktivität
            productivity < 40f -> Color.parseColor("#FF4B81") // Rosa
            productivity < 60f -> Color.parseColor("#FF9800") // Orange
            productivity < 80f -> Color.parseColor("#4CAF50") // Grün
            else -> Color.parseColor("#8B80F9") // Violett
        }
    }

    private fun calculateDayProductivity(date: Date, tasks: List<Task>, sessions: List<FocusSession>): Float {
        val calendar = Calendar.getInstance()
        calendar.time = date
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDay = calendar.timeInMillis
        
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis - 1

        // Aufgaben für diesen Tag
        val dayTasks = tasks.filter { task ->
            task.created.time in startOfDay..endOfDay
        }
        
        // Fokuszeit für diesen Tag
        val focusTime = sessionManager.getTotalFocusTimeForDate(startOfDay)
        
        var productivity = 0f
        
        // Task-Produktivität (50% Gewichtung)
        if (dayTasks.isNotEmpty()) {
            val completedTasks = dayTasks.count { it.completed }
            productivity += (completedTasks.toFloat() / dayTasks.size.toFloat()) * 50f
        }
        
        // Fokuszeit-Produktivität (50% Gewichtung)
        if (focusTime > 0) {
            // Dynamische Berechnung der maximalen Fokuszeit
            // Für kurze Fokuszeiten (< 60 min): Max = 60 min
            // Für mittlere Fokuszeiten (60-120 min): Max = 120 min
            // Für lange Fokuszeiten (> 120 min): Max = 240 min
            val maxFocusTime = when {
                focusTime < 60 -> 60f
                focusTime < 120 -> 120f
                else -> 240f
            }
            
            val focusProductivity = (focusTime.toFloat() / maxFocusTime).coerceAtMost(1f) * 50f
            productivity += focusProductivity
            
            Log.d(TAG, "Fokuszeit: $focusTime min, Max: $maxFocusTime min, Produktivität: $focusProductivity%")
        }

        val dateStr = SimpleDateFormat("dd.MM.", Locale.getDefault()).format(date)
        Log.d(TAG, "Produktivität für $dateStr: $productivity% (Fokuszeit: $focusTime min)")
        return productivity
    }

    private fun updateCompletedTasksChart(animate: Boolean = false) {
        lifecycleScope.launch {
            try {
                val entries = ArrayList<Entry>()
                val labels = ArrayList<String>()
                val dateFormat = SimpleDateFormat("dd.MM.", Locale.getDefault())
                val calendar = Calendar.getInstance()
                val allTasks = taskManager.getAllTasks().first()
                
                for (i in 6 downTo 0) {
                    calendar.add(Calendar.DAY_OF_YEAR, -i)
                    val date = calendar.time
                    val dayStart = calendar.apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                    }.timeInMillis
                    
                    val dayEnd = calendar.apply {
                        set(Calendar.HOUR_OF_DAY, 23)
                        set(Calendar.MINUTE, 59)
                        set(Calendar.SECOND, 59)
                    }.timeInMillis
                    
                    val completedCount = allTasks.count { task ->
                        task.completed && task.created.time in dayStart..dayEnd
                    }
                    
                    entries.add(Entry((6-i).toFloat(), completedCount.toFloat()))
                    labels.add(dateFormat.format(date))
                    
                    calendar.add(Calendar.DAY_OF_YEAR, i)
                }
                
                withContext(Dispatchers.Main) {
                    val dataSet = LineDataSet(entries, "").apply {
                        color = Color.parseColor("#8B80F9")
                        setCircleColor(Color.WHITE)
                        lineWidth = 2f
                        circleRadius = 4f
                        setDrawCircleHole(true)
                        circleHoleRadius = 2f
                        valueTextSize = 10f
                        valueTextColor = Color.WHITE
                        mode = LineDataSet.Mode.CUBIC_BEZIER
                        setDrawFilled(true)
                        fillColor = Color.parseColor("#8B80F9")
                        fillAlpha = 50
                    }
                    
                    completedTasksChart.apply {
                        data = LineData(dataSet)
                        xAxis.valueFormatter = IndexAxisValueFormatter(labels)
                        
                        if (animate) {
                            animateY(1000)
                        }
                        invalidate()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Aktualisieren des Aufgabencharts", e)
            }
        }
    }

    private fun isLocalResponse(text: String): Boolean {
        // Typische Muster für lokale Antworten
        return text.contains("Timer für", ignoreCase = true) ||
               text.contains("Aufgabe erstellt", ignoreCase = true) ||
               text.contains("✓", ignoreCase = true) ||
               text.contains("Möchten Sie einen Timer", ignoreCase = true)
    }

    private fun analyzeMessages(messages: List<ChatMessage>): Map<String, Int> {
        return messages
            .filter { !it.isUser }  // Nur Assistenten-Nachrichten analysieren
            .flatMap { message ->
                message.text.split(Regex("[\\s,.!?]+"))  // Text in Wörter aufteilen
                    .filter { it.length > 3 }  // Kurze Wörter ignorieren
                    .map { it.toLowerCase() }  // Alles in Kleinbuchstaben umwandeln
            }
            .groupBy { it }  // Nach Wörtern gruppieren
            .mapValues { it.value.size }  // Häufigkeit zählen
            .filter { it.value > 1 }  // Nur Wörter mit mehr als einem Vorkommen
            .toList()
            .sortedByDescending { it.second }  // Nach Häufigkeit sortieren
            .take(10)  // Top 10 Wörter
            .toMap()
    }

    override fun onResume() {
        super.onResume()
        // Sofort beim Anzeigen aktualisieren
        lifecycleScope.launch {
            loadStatistics()
            startPeriodicUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        updateJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        updateJob?.cancel()
    }

    companion object {
        private const val TAG = "StatisticsFragment"
    }
} 