package com.deepcore.kiytoapp

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.deepcore.kiytoapp.data.Task
import com.deepcore.kiytoapp.data.TaskManager
import com.deepcore.kiytoapp.chat.ChatActivity
import com.deepcore.kiytoapp.chat.ChatViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MindMapActivity : AppCompatActivity() {
    private lateinit var mindMapView: MindMapView
    private lateinit var taskManager: TaskManager
    private lateinit var chatViewModel: ChatViewModel
    private lateinit var zoomInButton: FloatingActionButton
    private lateinit var zoomOutButton: FloatingActionButton
    private lateinit var resetViewButton: FloatingActionButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_mindmap)

        // Aktiviere Up-Navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.mindmap_view)

        initializeViews()
        setupViewModel()
        setupTaskInteractions()
        setupTaskManager()
        observeChatSuggestions()
        loadTasks()
    }

    private fun setupViewModel() {
        chatViewModel = ViewModelProvider(this)[ChatViewModel::class.java]
    }

    private fun initializeViews() {
        mindMapView = findViewById(R.id.mindMapView)
        zoomInButton = findViewById(R.id.zoomInButton)
        zoomOutButton = findViewById(R.id.zoomOutButton)
        resetViewButton = findViewById(R.id.resetViewButton)

        zoomInButton.setOnClickListener { mindMapView.zoomIn() }
        zoomOutButton.setOnClickListener { mindMapView.zoomOut() }
        resetViewButton.setOnClickListener { mindMapView.resetView() }
    }

    private fun setupTaskInteractions() {
        mindMapView.setOnTaskClickListener { task ->
            showTaskOptionsDialog(task)
        }

        mindMapView.setOnTaskLongClickListener { task ->
            chatViewModel.analyzeTask(task)
        }
    }

    private fun setupTaskManager() {
        taskManager = TaskManager(this)
    }

    private fun loadTasks() {
        lifecycleScope.launch {
            val tasks = taskManager.getAllTasks().first()
            mindMapView.setTasks(tasks)
        }
    }

    private fun showTaskOptionsDialog(task: Task) {
        MaterialAlertDialogBuilder(this)
            .setTitle(task.title)
            .setItems(arrayOf(
                getString(R.string.edit),
                getString(R.string.delete),
                getString(R.string.analyze_with_ai)
            )) { _, which ->
                when (which) {
                    0 -> editTask(task)
                    1 -> deleteTaskWithUndo(task)
                    2 -> analyzeTaskWithAI(task)
                }
            }
            .show()
    }

    private fun editTask(task: Task) {
        val intent = Intent(this, TaskEditActivity::class.java).apply {
            putExtra(EXTRA_TASK_ID, task.id)
        }
        startActivity(intent)
    }

    private fun deleteTaskWithUndo(task: Task) {
        lifecycleScope.launch {
            taskManager.deleteTask(task.id)
            loadTasks()
            
            Snackbar.make(
                mindMapView,
                getString(R.string.task_deleted),
                Snackbar.LENGTH_LONG
            ).setAction(getString(R.string.undo)) {
                lifecycleScope.launch {
                    taskManager.createTask(task)
                    loadTasks()
                }
            }.show()
        }
    }

    private fun analyzeTaskWithAI(task: Task) {
        chatViewModel.analyzeTask(task)
        startActivity(Intent(this, ChatActivity::class.java).apply {
            putExtra(ChatActivity.EXTRA_TASK_ID, task.id)
        })
    }

    private fun observeChatSuggestions() {
        chatViewModel.taskSuggestions.observe(this) { suggestions ->
            mindMapView.showSuggestions(suggestions.map { suggestion ->
                MindMapView.TaskSuggestion(
                    title = suggestion.title,
                    description = suggestion.description,
                    confidence = suggestion.confidence
                )
            })
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