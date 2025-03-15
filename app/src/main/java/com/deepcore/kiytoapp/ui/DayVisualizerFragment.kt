package com.deepcore.kiytoapp.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.deepcore.kiytoapp.AddTaskFragment
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.SettingsFragment
import com.deepcore.kiytoapp.adapter.TimelineAdapter
import com.deepcore.kiytoapp.databinding.FragmentDayVisualizerBinding
import com.deepcore.kiytoapp.viewmodel.DayVisualizerViewModel
import com.deepcore.kiytoapp.viewmodel.TimelineItem
import com.deepcore.kiytoapp.viewmodel.TimelineItemType
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DayVisualizerFragment : Fragment() {
    private var _binding: FragmentDayVisualizerBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: DayVisualizerViewModel
    private lateinit var timelineAdapter: TimelineAdapter
    
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    
    // Variable zum Speichern des Action Bar-Status
    private var isActionBarVisible = true
    
    // Speichern der letzten Klickzeit für Doppelklick-Erkennung
    private var lastClickTime = 0L
    
    // Speichern des aktuell ausgewählten Tages
    private var selectedDayIndex = 6 // Standardmäßig Sonntag (Index 6)

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDayVisualizerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[DayVisualizerViewModel::class.java]
        
        setupTimelineAdapter()
        setupCalendarDays()
        setupTaskActions()
        setupCalendarButtons()
        observeSelectedTask()
        
        // Prüfe, ob wir die Action Bar umschalten sollen (wenn der Benutzer zweimal auf den Tagesplaner geklickt hat)
        arguments?.let {
            if (it.getBoolean("toggle_action_bar", false)) {
                toggleActionBarVisibility(forceToggle = true)
            }
        }
        
        // Stelle sicher, dass die Toolbar sichtbar ist, wenn keine Argumente gesetzt sind
        if (arguments == null) {
            showActionBar()
        }
        
        // Beobachte das ausgewählte Datum
        viewModel.selectedDate.observe(viewLifecycleOwner) { date ->
            updateCalendarView(date)
        }
        
        // Beobachte die Timeline-Items
        viewModel.timelineItems.observe(viewLifecycleOwner) { items ->
            timelineAdapter.submitList(items)
            
            // Zeige eine Nachricht an, wenn keine Aufgaben vorhanden sind
            binding.emptyStateMessage.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.timelineRecyclerView.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            
            if (items.isEmpty()) {
                loadSampleData()
            }
            
            // Aktualisiere die Counter unter den Tagen
            updateTaskCounters(items)
        }
        
        // Setze einen Klick-Listener auf den gesamten Fragment-Container
        binding.root.setOnClickListener {
            toggleActionBarVisibility()
        }
    }
    
    private fun toggleActionBarVisibility(forceToggle: Boolean = false) {
        // Aktuelle Zeit für Doppelklick-Erkennung
        val clickTime = System.currentTimeMillis()
        
        // Wenn der letzte Klick weniger als 500ms her ist oder forceToggle true ist
        if (forceToggle || clickTime - lastClickTime < 500) {
            isActionBarVisible = !isActionBarVisible
            
            if (isActionBarVisible) {
                showActionBar()
            } else {
                hideActionBar()
            }
        }
        
        lastClickTime = clickTime
    }
    
    private fun showActionBar() {
        // Verwende die Activity-Methode, um die ActionBar anzuzeigen
        (activity as? AppCompatActivity)?.supportActionBar?.show()
        
        // Stelle auch sicher, dass die Toolbar sichtbar ist (falls vorhanden)
        activity?.findViewById<View>(R.id.toolbar)?.visibility = View.VISIBLE
        
        // Aktualisiere den Status
        isActionBarVisible = true
    }
    
    private fun hideActionBar() {
        // Verwende die Activity-Methode, um die ActionBar auszublenden
        (activity as? AppCompatActivity)?.supportActionBar?.hide()
        
        // Stelle auch sicher, dass die Toolbar ausgeblendet ist (falls vorhanden)
        activity?.findViewById<View>(R.id.toolbar)?.visibility = View.GONE
        
        // Aktualisiere den Status
        isActionBarVisible = false
    }

    private fun setupTimelineAdapter() {
        timelineAdapter = TimelineAdapter(
            context = requireContext(),
            onItemClick = { item ->
                viewModel.selectTask(item)
            },
            onToggleComplete = { item ->
                // Aktualisiere den Task-Status im ViewModel
                viewModel.updateTimelineItem(item.copy(completed = !item.completed))
            }
        )
        binding.timelineRecyclerView.adapter = timelineAdapter
    }

    private fun setupCalendarDays() {
        // Aktuelles Datum
        val calendar = Calendar.getInstance()
        
        // Setze den Kalender auf Dienstag (erster Tag in der Wochenansicht)
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.TUESDAY) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }
        
        // Setze die Tage in der Wochenansicht
        val dayViews = listOf(
            binding.tvDay1, binding.tvDay2, binding.tvDay3, 
            binding.tvDay4, binding.tvDay5, binding.tvDay6, binding.tvDay7
        )
        
        // Speichere die Daten für jeden Tag
        val dayDates = mutableListOf<Date>()
        
        // Setze die Texte der Tage
        for (i in 0 until 7) {
            dayViews[i].text = calendar.get(Calendar.DAY_OF_MONTH).toString()
            
            // Speichere das Datum
            dayDates.add(calendar.time)
            
            // Setze einen Click-Listener für jeden Tag
            val finalI = i
            dayViews[i].setOnClickListener {
                selectDay(finalI, dayDates[finalI])
            }
            
            // Gehe zum nächsten Tag
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        // Wähle den aktuellen Tag aus
        val today = Calendar.getInstance()
        val dayOfWeek = today.get(Calendar.DAY_OF_WEEK)
        
        // Konvertiere den Tag der Woche in den Index in unserer Ansicht (Dienstag = 0, ..., Montag = 6)
        val todayIndex = when (dayOfWeek) {
            Calendar.TUESDAY -> 0
            Calendar.WEDNESDAY -> 1
            Calendar.THURSDAY -> 2
            Calendar.FRIDAY -> 3
            Calendar.SATURDAY -> 4
            Calendar.SUNDAY -> 5
            Calendar.MONDAY -> 6
            else -> 0
        }
        
        // Wähle den aktuellen Tag aus
        selectDay(todayIndex, dayDates[todayIndex])
    }
    
    private fun selectDay(index: Int, date: Date) {
        // Setze alle Tage zurück
        val dayViews = listOf(
            binding.tvDay1, binding.tvDay2, binding.tvDay3, 
            binding.tvDay4, binding.tvDay5, binding.tvDay6, binding.tvDay7
        )
        
        for (i in 0 until 7) {
            // Setze den Hintergrund basierend auf der Auswahl
            dayViews[i].background = ContextCompat.getDrawable(
                requireContext(),
                if (i == index) R.drawable.calendar_day_background_selected else R.drawable.calendar_day_background
            )
        }
        
        // Speichere den ausgewählten Tag
        selectedDayIndex = index
        
        // Aktualisiere das ausgewählte Datum im ViewModel
        viewModel.setSelectedDate(date)
    }

    private fun updateCalendarView(selectedDate: Date) {
        // Aktualisiere den Monat/Jahr-Text
        binding.tvMonthYear.text = monthYearFormat.format(selectedDate)
    }
    
    private fun updateTaskCounters(tasks: List<TimelineItem>) {
        // Zähle die Aufgaben pro Tag
        val calendar = Calendar.getInstance()
        val taskCounters = IntArray(7) { 0 }
        
        // Zähle die Aufgaben für jeden Tag
        for (task in tasks) {
            calendar.time = task.startTime
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            
            // Konvertiere den Tag der Woche in den Index in unserer Ansicht (Dienstag = 0, ..., Montag = 6)
            val dayIndex = when (dayOfWeek) {
                Calendar.TUESDAY -> 0
                Calendar.WEDNESDAY -> 1
                Calendar.THURSDAY -> 2
                Calendar.FRIDAY -> 3
                Calendar.SATURDAY -> 4
                Calendar.SUNDAY -> 5
                Calendar.MONDAY -> 6
                else -> -1
            }
            
            if (dayIndex >= 0) {
                taskCounters[dayIndex]++
            }
        }
        
        // Aktualisiere die Counter-Texte
        val counterViews = listOf(
            binding.tvCounter1, binding.tvCounter2, binding.tvCounter3, 
            binding.tvCounter4, binding.tvCounter5, binding.tvCounter6, binding.tvCounter7
        )
        
        for (i in 0 until 7) {
            counterViews[i].text = "${taskCounters[i]}/20"
        }
    }

    private fun setupTaskActions() {
        binding.apply {
            btnDeleteTask.setOnClickListener {
                viewModel.deleteSelectedTask()
                showSnackbar("Aufgabe gelöscht")
                // Blende das Aktionsmenü aus
                taskActionsLayout.visibility = View.GONE
                taskActionsBackground.visibility = View.GONE
            }
            btnCopyTask.setOnClickListener {
                viewModel.copySelectedTask()
                showSnackbar("Aufgabe kopiert")
                // Blende das Aktionsmenü aus
                taskActionsLayout.visibility = View.GONE
                taskActionsBackground.visibility = View.GONE
            }
            btnCompleteTask.setOnClickListener {
                viewModel.completeSelectedTask()
                showSnackbar("Aufgabe als erledigt markiert")
                // Blende das Aktionsmenü aus
                taskActionsLayout.visibility = View.GONE
                taskActionsBackground.visibility = View.GONE
                // Setze die Auswahl zurück
                viewModel.selectTask(null)
            }
            btnEditTask.setOnClickListener {
                navigateToEditTask()
            }
        }
    }

    private fun setupCalendarButtons() {
        binding.apply {
            btnCalendarSettings.setOnClickListener {
                // Öffne die Kalender-Einstellungen
                val settingsFragment = SettingsFragment()
                val args = Bundle()
                args.putString("scroll_to", "calendar")
                settingsFragment.arguments = args
                
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    .setReorderingAllowed(true)
                    .replace(R.id.fragmentContainer, settingsFragment)
                    .addToBackStack(null)
                    .commit()
            }
            
            btnNotifications.setOnClickListener {
                // Öffne die Benachrichtigungseinstellungen
                val settingsFragment = SettingsFragment()
                val args = Bundle()
                args.putString("scroll_to", "notifications")
                settingsFragment.arguments = args
                
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    .setReorderingAllowed(true)
                    .replace(R.id.fragmentContainer, settingsFragment)
                    .addToBackStack(null)
                    .commit()
            }
            
            btnSettings.setOnClickListener {
                // Öffne die allgemeinen Einstellungen
                parentFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    .setReorderingAllowed(true)
                    .replace(R.id.fragmentContainer, SettingsFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun observeSelectedTask() {
        viewModel.selectedTask.observe(viewLifecycleOwner) { task ->
            binding.apply {
                // Zeige die Aktionsschaltflächen nur an, wenn ein Task ausgewählt ist
                taskActionsLayout.visibility = if (task != null) View.VISIBLE else View.GONE
                taskActionsBackground.visibility = if (task != null) View.VISIBLE else View.GONE
                
                btnDeleteTask.isEnabled = task != null
                btnEditTask.isEnabled = task != null
                btnCopyTask.isEnabled = task != null
                btnCompleteTask.isEnabled = task != null && !task.completed
                
                // Aktualisiere die Farbe des Erledigt-Buttons basierend auf dem Status des Tasks
                if (task != null && !task.completed) {
                    btnCompleteTask.setColorFilter(ContextCompat.getColor(requireContext(), R.color.coral))
                    txtComplete.setTextColor(ContextCompat.getColor(requireContext(), R.color.coral))
                } else {
                    btnCompleteTask.setColorFilter(ContextCompat.getColor(requireContext(), R.color.gray))
                    txtComplete.setTextColor(ContextCompat.getColor(requireContext(), R.color.gray))
                }
                
                // Füge einen Klick-Listener für den Hintergrund des Aktionsmenüs hinzu,
                // damit der Benutzer das Menü durch Klicken außerhalb schließen kann
                taskActionsBackground.setOnClickListener {
                    closeTaskActionsMenu()
                }
            }
        }
    }
    
    private fun closeTaskActionsMenu() {
        binding.taskActionsLayout.visibility = View.GONE
        binding.taskActionsBackground.visibility = View.GONE
        viewModel.selectTask(null)
    }
    
    private fun loadSampleData() {
        // Beispieldaten für die Timeline erstellen
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 8)
        calendar.set(Calendar.MINUTE, 0)
        
        val wakeUpTime = calendar.time
        
        calendar.set(Calendar.HOUR_OF_DAY, 22)
        val sleepTime = calendar.time
        
        val sampleItems = listOf(
            TimelineItem(
                id = 1,
                title = "Aufwachen",
                startTime = wakeUpTime,
                endTime = null,
                completed = true,
                type = TimelineItemType.HABIT
            ),
            TimelineItem(
                id = 2,
                title = "Schlafen gehen",
                startTime = sleepTime,
                endTime = null,
                completed = false,
                type = TimelineItemType.HABIT
            )
        )
        
        // Speichere die Beispieldaten in der Datenbank
        viewModel.saveSampleTimelineItems(sampleItems)
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }

    private fun navigateToEditTask() {
        val timelineItem = viewModel.selectedTask.value ?: return
        val bundle = Bundle().apply {
            putLong("taskId", timelineItem.id)
            putString("title", timelineItem.title)
            putString("description", timelineItem.description)
            putLong("startTime", timelineItem.startTime?.time ?: System.currentTimeMillis())
            putLong("endTime", timelineItem.endTime?.time ?: (timelineItem.startTime?.time?.plus(3600000) ?: System.currentTimeMillis()))
            putBoolean("completed", timelineItem.completed)
            putString("type", timelineItem.type.name)
        }
        
        // Navigation zum AddTaskFragment mit allen Daten
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, AddTaskFragment().apply { 
                arguments = bundle 
            })
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Stelle sicher, dass die Toolbar wieder sichtbar ist, wenn das Fragment zerstört wird
        showActionBar()
        _binding = null
    }
} 