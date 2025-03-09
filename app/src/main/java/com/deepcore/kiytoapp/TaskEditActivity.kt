package com.deepcore.kiytoapp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.MenuItem
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deepcore.kiytoapp.data.Task
import com.deepcore.kiytoapp.data.TaskManager
import com.deepcore.kiytoapp.data.Priority
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TaskEditActivity : AppCompatActivity() {
    private lateinit var taskManager: TaskManager
    private lateinit var titleInput: TextInputEditText
    private lateinit var descriptionInput: TextInputEditText
    private lateinit var dueDateButton: MaterialButton
    private lateinit var dueTimeButton: MaterialButton
    private lateinit var tagsInput: TextInputEditText
    private lateinit var saveButton: MaterialButton
    private lateinit var titleLayout: TextInputLayout
    
    private var taskId: Long = -1
    private var dueDate: Calendar? = null
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_task_edit)

        // Aktiviere Up-Navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        supportActionBar?.title = if (taskId == -1L) {
            getString(R.string.new_task)
        } else {
            getString(R.string.edit)
        }

        initializeViews()
        setupTaskManager()
        loadTask()
        setupListeners()
    }

    private fun initializeViews() {
        titleInput = findViewById(R.id.titleInput)
        descriptionInput = findViewById(R.id.descriptionInput)
        dueDateButton = findViewById(R.id.dueDateButton)
        dueTimeButton = findViewById(R.id.dueTimeButton)
        tagsInput = findViewById(R.id.tagsInput)
        saveButton = findViewById(R.id.saveButton)
        titleLayout = findViewById(R.id.titleLayout)
    }

    private fun setupTaskManager() {
        taskManager = TaskManager(this)
    }

    private fun loadTask() {
        if (taskId != -1L) {
            lifecycleScope.launch {
                val tasks = taskManager.getAllTasks().first()
                val task = tasks.find { it.id == taskId }
                task?.let { currentTask ->
                    titleInput.setText(currentTask.title)
                    descriptionInput.setText(currentTask.description)
                    tagsInput.setText(currentTask.tags.joinToString(", "))
                    
                    currentTask.dueDate?.let { date ->
                        dueDate = Calendar.getInstance().apply { time = date }
                        updateDateTimeButtons()
                    }
                }
            }
        }
    }

    private fun setupListeners() {
        dueDateButton.setOnClickListener { showDatePicker() }
        dueTimeButton.setOnClickListener { showTimePicker() }
        saveButton.setOnClickListener { saveTask() }
    }

    private fun showDatePicker() {
        val calendar = dueDate ?: Calendar.getInstance()
        DatePickerDialog(this,
            { _, year, month, day ->
                dueDate = (dueDate ?: Calendar.getInstance()).apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, day)
                }
                updateDateTimeButtons()
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        val calendar = dueDate ?: Calendar.getInstance()
        TimePickerDialog(this,
            { _, hour, minute ->
                dueDate = (dueDate ?: Calendar.getInstance()).apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, minute)
                }
                updateDateTimeButtons()
            },
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateDateTimeButtons() {
        dueDate?.let {
            dueDateButton.text = dateFormat.format(it.time)
            dueTimeButton.text = timeFormat.format(it.time)
        }
    }

    private fun saveTask() {
        val title = titleInput.text.toString().trim()
        if (title.isEmpty()) {
            titleLayout.error = getString(R.string.title_required)
            return
        }

        lifecycleScope.launch {
            val task = Task(
                id = if (taskId == -1L) System.currentTimeMillis() else taskId,
                title = title,
                description = descriptionInput.text.toString().trim(),
                priority = Priority.MEDIUM, // Standardpriorität
                dueDate = dueDate?.time,
                tags = tagsInput.text.toString()
                    .split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            )

            if (taskId == -1L) {
                taskManager.createTask(task)
            } else {
                taskManager.updateTask(task)
            }
            
            finish()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_TASK_ID = "extra_task_id"
    }
} 