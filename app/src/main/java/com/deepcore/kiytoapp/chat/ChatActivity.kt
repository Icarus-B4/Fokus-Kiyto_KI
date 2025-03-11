package com.deepcore.kiytoapp.chat

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.data.TaskManager

class ChatActivity : AppCompatActivity() {
    private lateinit var viewModel: ChatViewModel
    private lateinit var taskManager: TaskManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        // Aktiviere Up-Navigation
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.ai_assistant)

        setupViewModel()
        setupTaskManager()
        handleIntent()
    }

    private fun setupViewModel() {
        viewModel = ViewModelProvider(this)[ChatViewModel::class.java]
    }

    private fun setupTaskManager() {
        taskManager = TaskManager(this)
    }

    private fun handleIntent() {
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
        if (taskId != -1L) {
            // Hier würden wir den Task laden und analysieren
            // Für jetzt lassen wir es leer
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