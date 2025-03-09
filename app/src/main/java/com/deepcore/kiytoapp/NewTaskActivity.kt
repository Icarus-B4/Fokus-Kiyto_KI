package com.deepcore.kiytoapp

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.deepcore.kiytoapp.data.TaskDatabase
import com.deepcore.kiytoapp.data.entity.Task
import com.deepcore.kiytoapp.databinding.ActivityNewTaskBinding
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import com.deepcore.kiytoapp.data.TaskManager
import com.deepcore.kiytoapp.base.BaseActivity

class NewTaskActivity : BaseActivity() {
    private lateinit var binding: ActivityNewTaskBinding
    private lateinit var database: TaskDatabase
    private var dueDate: Calendar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        database = TaskDatabase.getDatabase(this)
        setupToolbar()
        setupViews()
        loadInitialData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = getString(R.string.new_task)
        }
    }

    private fun setupViews() {
        binding.dueDateButton.setOnClickListener { showDatePicker() }
        binding.dueTimeButton.setOnClickListener { showTimePicker() }
        binding.saveButton.setOnClickListener { saveTask() }
    }

    private fun loadInitialData() {
        intent.getStringExtra(EXTRA_TITLE)?.let {
            binding.titleInput.setText(it)
        }
        intent.getStringExtra(EXTRA_DESCRIPTION)?.let {
            binding.descriptionInput.setText(it)
        }
    }

    private fun showDatePicker() {
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(getString(R.string.select_due_date))
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            dueDate = Calendar.getInstance().apply {
                timeInMillis = selection
            }
            updateDateTimeText()
        }

        picker.show(supportFragmentManager, "date_picker")
    }

    private fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_24H)
            .setHour(calendar.get(Calendar.HOUR_OF_DAY))
            .setMinute(calendar.get(Calendar.MINUTE))
            .build()

        picker.addOnPositiveButtonClickListener {
            dueDate = (dueDate ?: Calendar.getInstance()).apply {
                set(Calendar.HOUR_OF_DAY, picker.hour)
                set(Calendar.MINUTE, picker.minute)
            }
            updateDateTimeText()
        }

        picker.show(supportFragmentManager, "time_picker")
    }

    private fun updateDateTimeText() {
        dueDate?.let { calendar ->
            val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            binding.dueDateText.text = dateFormat.format(calendar.time)
        }
    }

    private fun saveTask() {
        val title = binding.titleInput.text.toString()
        if (title.isBlank()) {
            Snackbar.make(binding.root, R.string.title_required, Snackbar.LENGTH_SHORT).show()
            return
        }

        val task = Task(
            title = title,
            description = binding.descriptionInput.text.toString(),
            dueDate = dueDate?.time,
            tags = binding.tagsInput.text.toString()
                .split(",")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        )

        lifecycleScope.launch {
            try {
                database.taskDao().insert(task)
                finish()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "Fehler beim Speichern", Snackbar.LENGTH_SHORT).show()
            }
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
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DESCRIPTION = "extra_description"
    }
} 