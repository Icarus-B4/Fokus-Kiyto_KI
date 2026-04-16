package com.deepcore.kiytoapp

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.deepcore.kiytoapp.ai.TaskAIService
import com.deepcore.kiytoapp.data.Priority
import com.deepcore.kiytoapp.data.Task
import com.deepcore.kiytoapp.data.TaskManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import android.app.DatePickerDialog
import android.widget.DatePicker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.app.TimePickerDialog
import android.widget.TimePicker
import android.util.Log
import android.content.ContentValues
import android.provider.CalendarContract
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.switchmaterial.SwitchMaterial

class AddTaskFragment : Fragment() {
    private lateinit var taskManager: TaskManager
    private lateinit var aiService: TaskAIService
    private lateinit var titleInput: TextInputEditText
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var priorityDropdown: AutoCompleteTextView
    private lateinit var dueDateInput: TextInputEditText
    private lateinit var timeInput: TextInputEditText
    private lateinit var tagsInput: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var cancelButton: MaterialButton
    private lateinit var priorityInputLayout: TextInputLayout
    private lateinit var addToCalendarSwitch: SwitchMaterial
    
    private var selectedDueDate: Date? = null
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private var selectedTime: Date? = null
    private var taskToEdit: Task? = null

    private val CALENDAR_PERMISSION_REQUEST = 1001

    companion object {
        private const val ARG_TASK_ID = "task_id"

        fun newInstance(taskId: Long) = AddTaskFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_TASK_ID, taskId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskManager = TaskManager(requireContext())
        
        // Prüfe, ob wir eine Task-ID haben
        arguments?.getLong(ARG_TASK_ID, 0)?.let { taskId ->
            if (taskId != 0L) {
                lifecycleScope.launch {
                    try {
                        val tasks = taskManager.getAllTasks().first()
                        taskToEdit = tasks.find { it.id == taskId }
                        
                        // View aktualisieren, nachdem die Daten geladen wurden
                        view?.let { view ->
                            updateViewWithTaskData(view)
                        }
                    } catch (e: Exception) {
                        Log.e("AddTaskFragment", "Fehler beim Laden der Aufgabe: ${e.message}")
                    }
                }
            }
        }
        
        // Prüfe, ob wir direkte Daten aus dem DayVisualizerFragment haben
        arguments?.let { args ->
            if (args.containsKey("taskId") && !args.containsKey(ARG_TASK_ID)) {
                val taskId = args.getLong("taskId", 0)
                val title = args.getString("title", "")
                val description = args.getString("description", "")
                val startTimeMillis = args.getLong("startTime", 0)
                val endTimeMillis = args.getLong("endTime", 0)
                val completed = args.getBoolean("completed", false)
                val typeStr = args.getString("type", "TASK")
                
                if (taskId != 0L) {
                    // Erstelle ein temporäres Task-Objekt
                    val startTime = if (startTimeMillis > 0) Date(startTimeMillis) else null
                    val endTime = if (endTimeMillis > 0) Date(endTimeMillis) else null
                    
                    // Bestimme die Priorität basierend auf dem Typ
                    val priority = when (typeStr) {
                        "MEETING" -> Priority.HIGH
                        "HABIT" -> Priority.LOW
                        else -> Priority.MEDIUM
                    }
                    
                    taskToEdit = Task(
                        id = taskId,
                        title = title,
                        description = description,
                        priority = priority,
                        completed = completed,
                        dueDate = startTime,
                        startTime = startTime,
                        endTime = endTime
                    )
                    
                    // View aktualisieren, wenn sie bereits verfügbar ist
                    view?.let { view ->
                        updateViewWithTaskData(view)
                    }
                }
            }
        }
    }

    private fun updateViewWithTaskData(view: View) {
        taskToEdit?.let { task ->
            // Titel der View aktualisieren
            view.findViewById<TextView>(R.id.titleView)?.text = getString(R.string.edit)
            
            // Formularfelder mit den Daten füllen
            titleInput.setText(task.title)
            descriptionInput.setText(task.description)
            priorityDropdown.setText(task.priority.name, false)
            updatePriorityColors(task.priority)  // Prioritätsfarben aktualisieren
            
            // Verwende startTime, wenn verfügbar, sonst dueDate
            val dateToUse = task.startTime ?: task.dueDate
            
            dateToUse?.let { date ->
                selectedDueDate = date
                selectedTime = date
                dueDateInput.setText(dateFormat.format(date))
                timeInput.setText(timeFormat.format(date))
                addToCalendarSwitch.isEnabled = true
            }
            
            // Setze den Switch-Status basierend auf dem vorhandenen Kalendereintrag
            addToCalendarSwitch.isChecked = task.calendarEventId != null
            
            tagsInput.setText(task.tags.joinToString(", "))
        }
    }

    private fun updatePriorityColors(priority: Priority) {
        val color = when (priority) {
            Priority.LOW -> resources.getColor(R.color.priority_low, null)
            Priority.MEDIUM -> resources.getColor(R.color.priority_medium, null)
            Priority.HIGH -> resources.getColor(R.color.priority_high, null)
        }
        priorityInputLayout.apply {
            setBoxBackgroundColor(color)
            defaultHintTextColor = ColorStateList.valueOf(resources.getColor(R.color.white, null))
            hintTextColor = ColorStateList.valueOf(resources.getColor(R.color.white, null))
        }
        priorityDropdown.setTextColor(resources.getColor(R.color.white, null))
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_add_task, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        aiService = TaskAIService(requireContext())
        initializeViews(view)
        setupPriorityDropdown()
        setupDueDateInput()
        setupButtons()
        
        // Aktualisiere die View mit den Daten, falls sie bereits geladen wurden
        updateViewWithTaskData(view)
        
        setupAISuggestions()
    }

    private fun initializeViews(view: View) {
        titleInput = view.findViewById(R.id.titleInput)
        descriptionInput = view.findViewById(R.id.descriptionInput)
        priorityDropdown = view.findViewById(R.id.priorityDropdown)
        dueDateInput = view.findViewById(R.id.dueDateInput)
        timeInput = view.findViewById(R.id.timeInput)
        tagsInput = view.findViewById(R.id.tagsInput)
        saveButton = view.findViewById(R.id.saveButton)
        cancelButton = view.findViewById(R.id.cancelButton)
        priorityInputLayout = view.findViewById(R.id.priorityInputLayout)
        addToCalendarSwitch = view.findViewById(R.id.addToCalendarSwitch)
        
        // Aktiviere den Switch nur, wenn ein Datum ausgewählt ist
        addToCalendarSwitch.isEnabled = selectedDueDate != null
    }

    private fun setupPriorityDropdown() {
        val priorities = Priority.values()
        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            R.layout.item_priority_dropdown,
            priorities.map { it.name }
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(resources.getColor(R.color.white, null))
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                val priority = priorities[position]
                view.setBackgroundColor(when (priority) {
                    Priority.LOW -> resources.getColor(R.color.priority_low, null)
                    Priority.MEDIUM -> resources.getColor(R.color.priority_medium, null)
                    Priority.HIGH -> resources.getColor(R.color.priority_high, null)
                })
                view.setTextColor(resources.getColor(R.color.white, null))
                return view
            }
        }
        
        priorityDropdown.setAdapter(adapter)
        
        priorityDropdown.setOnItemClickListener { _, _, position, _ ->
            val selectedPriority = priorities[position]
            val color = when (selectedPriority) {
                Priority.LOW -> resources.getColor(R.color.priority_low, null)
                Priority.MEDIUM -> resources.getColor(R.color.priority_medium, null)
                Priority.HIGH -> resources.getColor(R.color.priority_high, null)
            }
            priorityInputLayout.apply {
                setBoxBackgroundColor(color)
                defaultHintTextColor = ColorStateList.valueOf(resources.getColor(R.color.white, null))
                hintTextColor = ColorStateList.valueOf(resources.getColor(R.color.white, null))
            }
            priorityDropdown.setTextColor(resources.getColor(R.color.white, null))
        }
    }

    private fun setupDueDateInput() {
        dueDateInput.setOnClickListener {
            val calendar = Calendar.getInstance()
            selectedDueDate?.let { calendar.time = it }

            DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    selectedDueDate = calendar.time
                    dueDateInput.setText(dateFormat.format(selectedDueDate))
                    addToCalendarSwitch.isEnabled = true
                    showTimePickerDialog()
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        timeInput.setOnClickListener {
            showTimePickerDialog()
        }
    }

    private fun showTimePickerDialog() {
        val calendar = Calendar.getInstance()
        selectedTime?.let { calendar.time = it }

        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                calendar.set(Calendar.MINUTE, minute)
                selectedTime = calendar.time
                timeInput.setText(timeFormat.format(selectedTime))
                
                selectedDueDate?.let {
                    val dateCalendar = Calendar.getInstance()
                    dateCalendar.time = it
                    calendar.set(Calendar.YEAR, dateCalendar.get(Calendar.YEAR))
                    calendar.set(Calendar.MONTH, dateCalendar.get(Calendar.MONTH))
                    calendar.set(Calendar.DAY_OF_MONTH, dateCalendar.get(Calendar.DAY_OF_MONTH))
                    selectedDueDate = calendar.time
                }
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun setupButtons() {
        saveButton.setOnClickListener {
            saveTask()
        }

        cancelButton.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun checkCalendarPermissions(): Boolean {
        val readPermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.READ_CALENDAR
        )
        val writePermission = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.WRITE_CALENDAR
        )
        
        return readPermission == PackageManager.PERMISSION_GRANTED &&
               writePermission == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCalendarPermissions() {
        ActivityCompat.requestPermissions(
            requireActivity(),
            arrayOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR
            ),
            CALENDAR_PERMISSION_REQUEST
        )
    }
    
    private fun addEventToCalendar(task: Task): Task {
        if (!checkCalendarPermissions()) {
            requestCalendarPermissions()
            return task
        }
        
        try {
            // Wenn es einen vorhandenen Kalendereintrag gibt, diesen zuerst löschen
            task.calendarEventId?.let { eventId ->
                requireContext().contentResolver.delete(
                    CalendarContract.Events.CONTENT_URI,
                    "${CalendarContract.Events._ID} = ?",
                    arrayOf(eventId.toString())
                )
            }
            
            val eventValues = ContentValues().apply {
                put(CalendarContract.Events.TITLE, task.title)
                put(CalendarContract.Events.DESCRIPTION, task.description)
                put(CalendarContract.Events.DTSTART, task.dueDate?.time ?: System.currentTimeMillis())
                put(CalendarContract.Events.DTEND, (task.dueDate?.time ?: System.currentTimeMillis()) + 3600000) // +1 Stunde
                put(CalendarContract.Events.CALENDAR_ID, 1) // Standardkalender
                put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            }
            
            val uri = requireContext().contentResolver.insert(
                CalendarContract.Events.CONTENT_URI,
                eventValues
            )
            
            uri?.let {
                val eventId = uri.lastPathSegment?.toLongOrNull()
                if (eventId != null) {
                    // Erstelle eine neue Task-Instanz mit der Kalender-Event-ID
                    val updatedTask = task.copy(calendarEventId = eventId)
                    
                    Snackbar.make(
                        requireView(),
                        getString(R.string.task_added_to_calendar),
                        Snackbar.LENGTH_LONG
                    )
                    .setBackgroundTint(resources.getColor(R.color.background_dark, null))
                    .setTextColor(resources.getColor(R.color.white, null))
                    .setActionTextColor(resources.getColor(R.color.primary, null))
                    .show()
                    
                    return updatedTask
                }
            }
        } catch (e: Exception) {
            Log.e("AddTaskFragment", "Fehler beim Hinzufügen zum Kalender: ${e.message}")
            Snackbar.make(
                requireView(),
                getString(R.string.error_adding_to_calendar),
                Snackbar.LENGTH_LONG
            )
            .setBackgroundTint(resources.getColor(R.color.background_dark, null))
            .setTextColor(resources.getColor(R.color.white, null))
            .setActionTextColor(resources.getColor(R.color.primary, null))
            .show()
        }
        return task
    }

    private fun saveTask() {
        val title = titleInput.text.toString()
        if (title.isBlank()) {
            titleInput.error = getString(R.string.title_required)
            return
        }

        // Berechne die Endzeit (1 Stunde nach Startzeit, wenn nicht anders angegeben)
        val calculatedEndTime = if (taskToEdit?.endTime != null) {
            taskToEdit?.endTime
        } else {
            selectedDueDate?.let { Date(it.time + 3600000) } // +1 Stunde
        }

        var task = Task(
            id = taskToEdit?.id ?: 0,
            title = title,
            description = descriptionInput.text.toString(),
            priority = Priority.valueOf(priorityDropdown.text.toString()),
            dueDate = selectedDueDate,
            completed = taskToEdit?.completed ?: false,
            tags = tagsInput.text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() },
            created = taskToEdit?.created ?: Date(),
            calendarEventId = taskToEdit?.calendarEventId,
            // Setze die Startzeit auf das ausgewählte Datum/Zeit
            startTime = selectedDueDate,
            // Setze die Endzeit auf die berechnete Endzeit
            endTime = calculatedEndTime
        )

        lifecycleScope.launch {
            // Füge die Aufgabe zum Kalender hinzu, wenn der Switch aktiviert ist
            if (addToCalendarSwitch.isChecked && selectedDueDate != null) {
                task = addEventToCalendar(task)
            }
            
            if (taskToEdit == null) {
                taskManager.createTask(task)
            } else {
                taskManager.updateTask(task)
            }
            
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupAISuggestions() {
        titleInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!s.isNullOrBlank()) {
                    val tempTask = Task(
                        title = s.toString(),
                        description = descriptionInput.text?.toString() ?: "",
                        priority = Priority.MEDIUM,
                        tags = tagsInput.text?.toString()?.split(",")?.map { it.trim() } ?: emptyList()
                    )
                    
                    val suggestedPriority = aiService.suggestPriority(tempTask)
                    val suggestedTags = aiService.suggestTags(tempTask)
                    
                    if (priorityDropdown.text.isNullOrEmpty()) {
                        priorityDropdown.setText(suggestedPriority.name, false)
                    }
                    
                    if (tagsInput.text.isNullOrEmpty() && suggestedTags.isNotEmpty()) {
                        tagsInput.setText(suggestedTags.joinToString(", "))
                    }
                }
            }
        })
    }
} 