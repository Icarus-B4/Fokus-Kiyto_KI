package com.deepcore.kiytoapp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CalendarView
import android.widget.ImageButton
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deepcore.kiytoapp.ai.DailyInspirationManager
import com.deepcore.kiytoapp.ai.TaskAIService
import com.deepcore.kiytoapp.auth.AuthManager
import com.deepcore.kiytoapp.auth.LoginActivity
import com.deepcore.kiytoapp.base.BaseFragment
import com.deepcore.kiytoapp.data.SessionManager
import com.deepcore.kiytoapp.data.Task
import com.deepcore.kiytoapp.data.TaskManager
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
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
import com.airbnb.lottie.LottieAnimationView
import com.airbnb.lottie.LottieDrawable

class DashboardFragment : BaseFragment() {
    private var _taskManager: TaskManager? = null
    private val taskManager get() = _taskManager!!
    
    private var _aiService: TaskAIService? = null
    private val aiService get() = _aiService!!
    
    private var _prioritizedTasksAdapter: TaskAdapter? = null
    private val prioritizedTasksAdapter get() = _prioritizedTasksAdapter!!
    
    private var _aiChatButton: ExtendedFloatingActionButton? = null
    private val aiChatButton get() = _aiChatButton!!
    
    private var _recyclerView: RecyclerView? = null
    private val recyclerView get() = _recyclerView!!

    private var _userName: TextView? = null
    private val userName get() = _userName!!

    private var _userStats: TextView? = null
    private val userStats get() = _userStats!!

    private var _dailyActivityChart: LineChart? = null
    private val dailyActivityChart get() = _dailyActivityChart!!

    private var _focusTimeValue: TextView? = null
    private val focusTimeValue get() = _focusTimeValue!!

    private var _tasksValue: TextView? = null
    private val tasksValue get() = _tasksValue!!

    private var _productivityValue: TextView? = null
    private val productivityValue get() = _productivityValue!!

    private var _calendarView: CalendarView? = null
    private val calendarView get() = _calendarView!!

    private var _categoryPieChart: PieChart? = null
    private val categoryPieChart get() = _categoryPieChart!!

    private var _monthYearText: TextView? = null
    private val monthYearText get() = _monthYearText!!

    private var _prevMonthButton: ImageButton? = null
    private val prevMonthButton get() = _prevMonthButton!!

    private var _nextMonthButton: ImageButton? = null
    private val nextMonthButton get() = _nextMonthButton!!

    private var _userStatusAnimation: LottieAnimationView? = null
    private val userStatusAnimation get() = _userStatusAnimation!!

    private lateinit var authManager: AuthManager
    private lateinit var sessionManager: SessionManager
    private var updateJob: Job? = null
    private lateinit var inspirationManager: DailyInspirationManager
    private var currentQuoteText: String = ""
    private var lastFocusTime: Int = 0

    private val currentDate = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _taskManager = TaskManager(requireContext())
        _aiService = TaskAIService(requireContext())
        sessionManager = SessionManager(requireContext())
        inspirationManager = DailyInspirationManager(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_dashboard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        authManager = AuthManager(requireContext())
        
        try {
            initializeViews(view)
            setupCharts()
            setupCalendar()
            setupQuickAccess(view)
            setupPrioritizedTasks(view)
            setupAIChatButton()
            setupDailyInspiration(view)
            
            // Initial load
            loadDailyStats()
            loadPrioritizedTasks()
            updateChartData()
            
            // Starte periodische Aktualisierungen
            startPeriodicUpdates()
            
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Fehler bei der Fragment-Initialisierung", e)
        }
    }
    
    private fun initializeViews(view: View) {
        _recyclerView = view.findViewById<RecyclerView>(R.id.prioritizedTasksRecyclerView)
        _aiChatButton = view.findViewById<ExtendedFloatingActionButton>(R.id.aiChatButton)
        _userName = view.findViewById<TextView>(R.id.userName)
        _userStats = view.findViewById<TextView>(R.id.userStats)
        _dailyActivityChart = view.findViewById<LineChart>(R.id.dailyActivityChart)
        _focusTimeValue = view.findViewById<TextView>(R.id.focusTimeValue)
        _tasksValue = view.findViewById<TextView>(R.id.tasksValue)
        _productivityValue = view.findViewById<TextView>(R.id.productivityValue)
        _calendarView = view.findViewById<CalendarView>(R.id.calendarView)
        _userStatusAnimation = view.findViewById<LottieAnimationView>(R.id.userStatusAnimation)

        val welcomeText = view.findViewById<TextView>(R.id.welcomeText)
        val loginButton = view.findViewById<Button>(R.id.loginButton)
        val userProfileCard = view.findViewById<View>(R.id.userProfileCard)
        val quickAccessView = view.findViewById<View>(R.id.quickAccessView)
        val dailyInspirationCard = view.findViewById<View>(R.id.dailyInspiration)
        
        // AI Chat Button Sichtbarkeit
        aiChatButton.visibility = if (authManager.isLoggedIn()) View.VISIBLE else View.GONE

        if (authManager.isLoggedIn()) {
            // Eingeloggter Zustand
            val user = authManager.getCurrentUser()
            welcomeText.text = getString(R.string.welcome_user, user?.email?.substringBefore("@") ?: "")
            userName.text = getString(R.string.welcome_user, user?.email?.substringBefore("@") ?: "")
            loginButton.visibility = View.GONE
            
            // Konfiguriere die Status-Animation
            userStatusAnimation.apply {
                setAnimation(R.raw.status)
                repeatCount = LottieDrawable.INFINITE
                playAnimation()
            }
            
            // Zeige personalisierte Elemente
            userProfileCard.visibility = View.VISIBLE
            quickAccessView.visibility = View.VISIBLE
            recyclerView.visibility = View.VISIBLE
            dailyInspirationCard.visibility = View.VISIBLE
            
            // Initialisiere die personalisierten Daten
            setupCalendar()
            setupQuickAccess(view)
            setupPrioritizedTasks(view)
            setupDailyInspiration(view)
            loadDailyStats()
            loadPrioritizedTasks()
            updateChartData()
        } else {
            // Nicht eingeloggter Zustand
            welcomeText.text = getString(R.string.welcome_guest)
            userName.text = getString(R.string.welcome_guest)
            loginButton.visibility = View.VISIBLE
            
            // Konfiguriere die Status-Animation auch für nicht eingeloggte Benutzer
            userStatusAnimation.apply {
                setAnimation(R.raw.status)
                repeatCount = LottieDrawable.INFINITE
                playAnimation()
            }
            
            // Verstecke personalisierte Elemente
            userProfileCard.visibility = View.GONE
            quickAccessView.visibility = View.GONE
            recyclerView.visibility = View.GONE
            dailyInspirationCard.visibility = View.GONE

            loginButton.setOnClickListener {
                startActivity(Intent(requireContext(), LoginActivity::class.java))
            }
            
            // Zeige Basis-Dashboard
            setupBasicDashboard()
        }

        setupMonthNavigation()
        setupPieChart()
    }

    private fun setupBasicDashboard() {
        // Zeige nur Kalender für nicht eingeloggte Benutzer
        calendarView.visibility = View.VISIBLE
    }

    private fun setupCharts() {
        try {
            // Konfiguriere Activity Chart
            dailyActivityChart.apply {
                setBackgroundColor(Color.TRANSPARENT)
                description.isEnabled = false
                legend.apply {
                    isEnabled = true
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    form = Legend.LegendForm.CIRCLE
                    formSize = 15f
                    formLineWidth = 2f
                    horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                    verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                    orientation = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                }
                setTouchEnabled(false)
                setDrawGridBackground(false)
                setDrawBorders(false)
                setPadding(16, 16, 16, 16)
                setViewPortOffsets(60f, 16f, 16f, 60f)
                
                // X-Achse konfigurieren
                xAxis.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#20FFFFFF")
                    setDrawAxisLine(true)
                    axisLineColor = Color.parseColor("#40FFFFFF")
                    setDrawLabels(true)
                    setTextColor(Color.WHITE)
                    position = XAxis.XAxisPosition.BOTTOM
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}:00"
                        }
                    }
                }
                
                // Linke Y-Achse konfigurieren
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = Color.parseColor("#20FFFFFF")
                    setDrawAxisLine(true)
                    axisLineColor = Color.parseColor("#40FFFFFF")
                    setDrawLabels(true)
                    setTextColor(Color.WHITE)
                    axisMinimum = 0f
                    axisMaximum = 100f
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            return "${value.toInt()}%"
                        }
                    }
                }
                
                
                // Standardtext für leere Daten
                setNoDataText("Keine Daten verfügbar")
                setNoDataTextColor(Color.WHITE)
            }

            // Initial mit leeren Daten starten
            updateDailyActivityData()

        } catch (e: Exception) {
            Log.e("DashboardFragment", "Fehler beim Setup der Charts", e)
        }
    }

    private fun setupCalendar() {
        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            val selectedDate = Calendar.getInstance().apply {
                set(year, month, dayOfMonth)
            }
            loadDailyStats(selectedDate.timeInMillis)
        }
        
        // Initial den heutigen Tag laden
        loadDailyStats(System.currentTimeMillis())
    }

    private fun loadDailyStats(timestamp: Long = System.currentTimeMillis()) {
        lifecycleScope.launch {
            try {
                val allTasks = taskManager.getAllTasks().first()
                
                val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                val startOfDay = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val endOfDay = calendar.apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis

                val dayTasks = allTasks.filter { task ->
                    task.created.time in startOfDay..endOfDay
                }

                val totalTasks = dayTasks.size
                val completedTasks = dayTasks.count { it.completed }
                val openTasks = totalTasks - completedTasks
                
                // Fokuszeit für das ausgewählte Datum laden
                val focusTime = sessionManager.getTotalFocusTimeForDate(startOfDay)
                
                // Update Stats für den ausgewählten Tag
                val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val dateStr = dateFormat.format(Date(timestamp))
                userStats.text = getString(R.string.day_stats, dateStr, completedTasks, openTasks, formatFocusTime(focusTime))

                // Aktualisiere die Statistik-Werte
                focusTimeValue.text = formatFocusTime(focusTime)
                tasksValue.text = "$completedTasks/$totalTasks"
                val productivity = if (totalTasks > 0) {
                    (completedTasks.toFloat() / totalTasks.toFloat() * 100).toInt()
                } else {
                    0
                }
                productivityValue.text = "$productivity%"

                // Aktualisiere Chart-Daten für den ausgewählten Tag
                updateDailyActivityData(dayTasks, timestamp)
                
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Fehler beim Laden der Aufgaben für das Datum", e)
            }
        }
    }

    private fun formatFocusTime(focusTime: Int): String {
        return if (focusTime == 0) {
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

    private fun updateChartData() {
        lifecycleScope.launch {
            try {
                updateDailyActivityData(emptyList()) // Leere Liste für initiale Anzeige
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Fehler beim Aktualisieren der Chart-Daten", e)
            }
        }
    }

    private fun updateDailyActivityData(tasks: List<Task> = emptyList(), selectedTimestamp: Long = System.currentTimeMillis()) {
        try {
            val entries = ArrayList<Entry>()
            val focusEntries = ArrayList<Entry>()
            val taskEntries = ArrayList<Entry>()
            
            val calendar = Calendar.getInstance()
            val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
            
            // Verwende das ausgewählte Datum anstatt immer heute
            val selectedDate = Calendar.getInstance().apply {
                timeInMillis = selectedTimestamp
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val isSelectedDateToday = isToday(selectedTimestamp)
            
            // Aktivitätsdaten für die 24 Stunden des ausgewählten Tages
            for (hour in 0..23) {
                val startTime = selectedDate.clone() as Calendar
                startTime.set(Calendar.HOUR_OF_DAY, hour)
                startTime.set(Calendar.MINUTE, 0)
                startTime.set(Calendar.SECOND, 0)
                val startTimeMillis = startTime.timeInMillis

                val endTime = selectedDate.clone() as Calendar
                endTime.set(Calendar.HOUR_OF_DAY, hour)
                endTime.set(Calendar.MINUTE, 59)
                endTime.set(Calendar.SECOND, 59)
                val endTimeMillis = endTime.timeInMillis

                // Wenn das ausgewählte Datum heute ist und die Stunde in der Zukunft liegt, setze 0
                if (isSelectedDateToday && hour > currentHour) {
                    entries.add(Entry(hour.toFloat(), 0f))
                    focusEntries.add(Entry(hour.toFloat(), 0f))
                    taskEntries.add(Entry(hour.toFloat(), 0f))
                    continue
                }

                // Berechne tatsächliche Aktivität
                val tasksInHour = tasks.filter { task ->
                    task.created.time in startTimeMillis..endTimeMillis
                }
                
                // Berechne Fokuszeit in dieser Stunde (0-60 Minuten)
                val focusTimeInHour = if (isSelectedDateToday && hour == currentHour) {
                    sessionManager.getTotalFocusTimeForToday().toFloat()
                } else {
                    sessionManager.getTotalFocusTimeForDate(startTimeMillis).toFloat()
                }.coerceIn(0f, 60f)
                
                // Berechne Aktivität basierend auf realen Daten
                val taskActivity = if (tasksInHour.isNotEmpty()) {
                    val completedTasks = tasksInHour.count { it.completed }
                    (completedTasks.toFloat() / tasksInHour.size) * 100f
                } else 0f

                // Normalisiere Fokuszeit auf 100%
                val focusActivity = (focusTimeInHour / 60f) * 100f

                entries.add(Entry(hour.toFloat(), (taskActivity + focusActivity) / 2))
                focusEntries.add(Entry(hour.toFloat(), focusActivity))
                taskEntries.add(Entry(hour.toFloat(), taskActivity))
            }

            val dataSets = ArrayList<ILineDataSet>()

            // Gesamtaktivität DataSet
            val totalDataSet = LineDataSet(entries, "Gesamtaktivität").apply {
                valueTextColor = Color.WHITE
                color = Color.parseColor("#4CAF50")  // Grün
                setDrawCircles(true)
                circleRadius = 3f
                setCircleColor(Color.parseColor("#4CAF50"))
                lineWidth = 2f
                mode = LineDataSet.Mode.LINEAR
                setDrawFilled(true)
                fillColor = Color.parseColor("#4CAF50")
                fillAlpha = 30
                setDrawValues(false)
            }
            dataSets.add(totalDataSet)

            // Fokuszeit DataSet
            val focusDataSet = LineDataSet(focusEntries, "Fokuszeit").apply {
                valueTextColor = Color.WHITE
                color = Color.parseColor("#2196F3")  // Blau
                setDrawCircles(true)
                circleRadius = 3f
                setCircleColor(Color.parseColor("#2196F3"))
                lineWidth = 2f
                mode = LineDataSet.Mode.LINEAR
                setDrawFilled(false)
                setDrawValues(false)
            }
            dataSets.add(focusDataSet)

            // Task Aktivität DataSet
            val taskDataSet = LineDataSet(taskEntries, "Aufgaben").apply {
                valueTextColor = Color.WHITE
                color = Color.parseColor("#FF9800")  // Orange
                setDrawCircles(true)
                circleRadius = 3f
                setCircleColor(Color.parseColor("#FF9800"))
                lineWidth = 2f
                mode = LineDataSet.Mode.LINEAR
                setDrawFilled(false)
                setDrawValues(false)
            }
            dataSets.add(taskDataSet)

            dailyActivityChart.apply {
                clear()
                data = LineData(dataSets)
                legend.apply {
                    textColor = Color.WHITE
                    textSize = 12f
                    form = Legend.LegendForm.CIRCLE
                    formSize = 15f
                    formLineWidth = 2f
                    isEnabled = true
                    horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
                    verticalAlignment = Legend.LegendVerticalAlignment.BOTTOM
                    orientation = Legend.LegendOrientation.HORIZONTAL
                    setDrawInside(false)
                    xOffset = 0f
                    yOffset = 15f
                    xEntrySpace = 20f
                }
                description.textColor = Color.WHITE
                xAxis.textColor = Color.WHITE
                axisLeft.textColor = Color.WHITE
                animateX(500)
                invalidate()
            }
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Fehler beim Aktualisieren der Aktivitätsdaten", e)
        }
    }

    private fun setupQuickAccess(view: View) {
        view.findViewById<Chip>(R.id.addTaskChip)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AddTaskFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<Chip>(R.id.startFocusChip)?.setOnClickListener {
            (activity as? MainActivity)?.let { mainActivity ->
                mainActivity.findViewById<BottomNavigationView>(R.id.bottomNavigation)?.selectedItemId = R.id.navigation_focus
            }
        }

        view.findViewById<Chip>(R.id.statisticsChip)?.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, StatisticsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun setupPrioritizedTasks(view: View) {
        _prioritizedTasksAdapter = TaskAdapter(
            onTaskChecked = { task ->
                lifecycleScope.launch {
                    taskManager.toggleTaskCompletion(task.id)
                    loadPrioritizedTasks()
                }
            },
            onTaskClicked = { task ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, TaskDetailsFragment.newInstance(task.id))
                    .addToBackStack(null)
                    .commit()
            }
        )

        recyclerView.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = prioritizedTasksAdapter
            itemAnimator = null
            overScrollMode = View.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = false
        }
    }

    private fun setupAIChatButton() {
        aiChatButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AIChatFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun loadPrioritizedTasks() {
        lifecycleScope.launch {
            try {
                Log.d("DashboardFragment", "Starte Laden der priorisierten Aufgaben")
                
                val allTasks = try {
                    taskManager.getAllTasks().first()
                } catch (e: Exception) {
                    Log.e("DashboardFragment", "Fehler beim Laden aller Aufgaben", e)
                    emptyList()
                }
                
                Log.d("DashboardFragment", "Geladene Aufgaben: ${allTasks.size}")
                
                val nonCompletedTasks = allTasks.filter { !it.completed }
                Log.d("DashboardFragment", "Nicht abgeschlossene Aufgaben: ${nonCompletedTasks.size}")
                
                val sortedTasks = nonCompletedTasks.sortedByDescending { it.created }
                Log.d("DashboardFragment", "Nach Datum sortierte Aufgaben: ${sortedTasks.size}")
                
                val prioritizedTasks = try {
                    aiService.prioritizeTasks(sortedTasks)
                } catch (e: Exception) {
                    Log.e("DashboardFragment", "Fehler bei der KI-Priorisierung", e)
                    sortedTasks
                }
                
                val limitedTasks = prioritizedTasks.take(5)
                Log.d("DashboardFragment", "Finale Anzahl priorisierter Aufgaben: ${limitedTasks.size}")
                
                try {
                    prioritizedTasksAdapter.submitList(limitedTasks)
                    recyclerView.post {
                        prioritizedTasksAdapter.notifyDataSetChanged()
                    }
                    Log.d("DashboardFragment", "Adapter erfolgreich aktualisiert")
                } catch (e: Exception) {
                    Log.e("DashboardFragment", "Fehler beim Aktualisieren des Adapters", e)
                }
                
            } catch (e: Exception) {
                Log.e("DashboardFragment", "Allgemeiner Fehler beim Laden der priorisierten Aufgaben", e)
            }
        }
    }

    private fun startPeriodicUpdates() {
        updateJob?.cancel()
        updateJob = lifecycleScope.launch {
            while (isActive) {
                try {
                    // Aktuelle Fokuszeit abrufen
                    val currentDate = calendarView.date
                    val calendar = Calendar.getInstance().apply { timeInMillis = currentDate }
                    val startOfDay = calendar.apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    
                    val focusTime = if (isToday(currentDate)) {
                        sessionManager.getTotalFocusTimeForToday()
                    } else {
                        sessionManager.getTotalFocusTimeForDate(startOfDay)
                    }
                    
                    // Nur aktualisieren, wenn sich die Fokuszeit geändert hat
                    if (focusTime != lastFocusTime) {
                        lastFocusTime = focusTime
                        withContext(Dispatchers.Main) {
                            focusTimeValue.text = formatFocusTime(focusTime)
                            
                            // Auch die Benutzerstatistik aktualisieren, wenn das aktuelle Datum angezeigt wird
                            val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                            val dateStr = dateFormat.format(Date(currentDate))
                            
                            // Aufgaben für das aktuelle Datum abrufen
                            val allTasks = taskManager.getAllTasks().first()
                            val dayTasks = allTasks.filter { task ->
                                val taskCal = Calendar.getInstance().apply { time = task.created }
                                isSameDay(taskCal, calendar)
                            }
                            
                            val totalTasks = dayTasks.size
                            val completedTasks = dayTasks.count { it.completed }
                            val openTasks = totalTasks - completedTasks
                            
                            userStats.text = getString(R.string.day_stats, dateStr, completedTasks, openTasks, formatFocusTime(focusTime))
                            
                            // Aktualisiere auch die Produktivität
                            val productivity = if (totalTasks > 0) {
                                (completedTasks.toFloat() / totalTasks.toFloat() * 100).toInt()
                            } else {
                                0
                            }
                            productivityValue.text = "$productivity%"
                            
                            // Aktualisiere die Diagrammdaten für das ausgewählte Datum
                            updateDailyActivityData(dayTasks, currentDate)
                        }
                    }
                    
                    delay(1000) // Jede Sekunde prüfen
                } catch (e: Exception) {
                    Log.e("DashboardFragment", "Fehler bei der periodischen Aktualisierung", e)
                }
            }
        }
    }

    private fun isToday(timestamp: Long): Boolean {
        val today = Calendar.getInstance()
        val date = Calendar.getInstance().apply { timeInMillis = timestamp }
        
        return today.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
               today.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
    }
    
    private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun onResume() {
        super.onResume()
        // Aktualisiere die Daten, wenn das Fragment wieder sichtbar wird
        loadPrioritizedTasks()
        loadDailyStats(calendarView.date)
        startPeriodicUpdates()
        
        // Animation fortsetzen
        userStatusAnimation.resumeAnimation()
    }
    
    override fun onPause() {
        super.onPause()
        // Stoppe die periodische Aktualisierung, wenn das Fragment nicht sichtbar ist
        updateJob?.cancel()
        
        // Animation pausieren
        userStatusAnimation.pauseAnimation()
    }
    
    fun refreshTasks() {
        loadPrioritizedTasks()
        loadDailyStats(calendarView.date)
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        updateJob?.cancel()
        _recyclerView = null
        _prioritizedTasksAdapter = null
        _aiChatButton = null
        _userName = null
        _userStats = null
        _dailyActivityChart = null
        _focusTimeValue = null
        _tasksValue = null
        _productivityValue = null
        _calendarView = null
        _categoryPieChart = null
        _monthYearText = null
        _prevMonthButton = null
        _nextMonthButton = null
        _userStatusAnimation = null
        inspirationManager.shutdown()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        _taskManager = null
        _aiService = null
    }

    private fun setupMonthNavigation() {
        updateMonthYearText()
        
        prevMonthButton.setOnClickListener {
            currentDate.add(Calendar.MONTH, -1)
            updateMonthYearText()
            updateCharts()
        }
        
        nextMonthButton.setOnClickListener {
            currentDate.add(Calendar.MONTH, 1)
            updateMonthYearText()
            updateCharts()
        }
    }

    private fun updateMonthYearText() {
        val monthFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        monthYearText.text = monthFormat.format(currentDate.time)
    }

    private fun setupPieChart() {
        categoryPieChart.apply {
            description.isEnabled = false
            legend.isEnabled = false
            setDrawEntryLabels(false)
            setHoleColor(Color.TRANSPARENT)
            setTransparentCircleColor(Color.TRANSPARENT)
            setTransparentCircleAlpha(0)
            holeRadius = 70f
            setTouchEnabled(false)
        }
        
        updatePieChartData()
    }

    private fun updatePieChartData() {
        val entries = listOf(
            PieEntry(30f, "Work"),
            PieEntry(20f, "Study"),
            PieEntry(15f, "Exercise"),
            PieEntry(10f, "Social"),
            PieEntry(15f, "Entertainment"),
            PieEntry(10f, "Other")
        )
        
        val colors = listOf(
            Color.parseColor("#FF4B81"),  // Pink
            Color.parseColor("#4B7BFF"),  // Blue
            Color.parseColor("#FFB74B"),  // Orange
            Color.parseColor("#4BFF82"),  // Green
            Color.parseColor("#9B4BFF"),  // Purple
            Color.parseColor("#FF4B4B")   // Red
        )
        
        val dataSet = PieDataSet(entries, "").apply {
            this.colors = colors
            valueTextSize = 0f
            valueTextColor = Color.WHITE
            sliceSpace = 2f
        }
        
        categoryPieChart.apply {
            data = PieData(dataSet)
            invalidate()
        }
    }

    private fun updateCharts() {
        updatePieChartData()
        updateDailyActivityData(emptyList())
    }

    private fun setupDailyInspiration(view: View) {
        try {
            Log.d("DashboardFragment", "Starte Setup der täglichen Inspiration")
            
            val quoteText = view.findViewById<TextView>(R.id.quoteText)
            val quoteAuthor = view.findViewById<TextView>(R.id.quoteAuthor)
            val aiCommentText = view.findViewById<TextView>(R.id.aiCommentText)
            val shareButton = view.findViewById<MaterialButton>(R.id.shareButton)

            // Setze initiale Ladeanzeige
            quoteText.text = "Lade Inspiration..."
            quoteAuthor.text = ""
            aiCommentText.text = "Lade KI-Kommentar..."


            shareButton.setOnClickListener { view ->
                Log.d("DashboardFragment", "Teilen-Button geklickt")
                view.isEnabled = false // Deaktiviere Button während des Teilens
                try {
                    val shareText = """
                        ${quoteText.text}
                        ${quoteAuthor.text}
                        
                        ${aiCommentText.text}
                        
                        - Geteilt aus der KiytoApp
                    """.trimIndent()

                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Zitat teilen"))
                } catch (e: Exception) {
                    Log.e("DashboardFragment", "Fehler beim Teilen", e)
                }
                view.isEnabled = true // Aktiviere Button wieder
            }

            // Lade das Zitat
            lifecycleScope.launch {
                try {
                    Log.d("DashboardFragment", "Starte Laden des Zitats")
                    val quote = inspirationManager.getDailyQuote()
                    val aiComment = inspirationManager.getAIComment(quote)
                    
                    Log.d("DashboardFragment", "Zitat geladen: ${quote.text}")
                    
                    // UI-Updates auf dem Hauptthread
                    view.post {
                        quoteText.text = quote.text
                        quoteAuthor.text = "- ${quote.author}"
                        aiCommentText.text = aiComment
                        currentQuoteText = quote.text
                        
                        // Aktiviere die Buttons
                        shareButton.isEnabled = true
                    }
                } catch (e: Exception) {
                    Log.e("DashboardFragment", "Fehler beim Laden der täglichen Inspiration", e)
                    view.post {
                        quoteText.text = "Der frühe Vogel fängt den Wurm."
                        quoteAuthor.text = "- Sprichwort"
                        aiCommentText.text = "Ein zeitloser Rat für mehr Produktivität."
                        
                        // Aktiviere die Buttons auch im Fehlerfall
                        shareButton.isEnabled = true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("DashboardFragment", "Allgemeiner Fehler beim Setup der täglichen Inspiration", e)
        }
    }
} 