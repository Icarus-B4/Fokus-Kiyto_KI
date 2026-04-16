package com.deepcore.kiytoapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.deepcore.kiytoapp.data.Priority
import com.deepcore.kiytoapp.data.Task
import com.deepcore.kiytoapp.data.TaskManager
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

class TaskDetailsFragment : Fragment() {
    private lateinit var taskManager: TaskManager
    private var taskId: Long = 0
    private var task: Task? = null

    companion object {
        private const val ARG_TASK_ID = "task_id"

        fun newInstance(taskId: Long) = TaskDetailsFragment().apply {
            arguments = Bundle().apply {
                putLong(ARG_TASK_ID, taskId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        taskId = arguments?.getLong(ARG_TASK_ID, 0) ?: 0
        taskManager = TaskManager(requireContext())
        
        lifecycleScope.launch {
            task = taskManager.getAllTasks().first().find { it.id == taskId }
            task?.let { loadTaskDetails(it, requireView()) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_task_details, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Edit-Button
        view.findViewById<FloatingActionButton>(R.id.editButton).setOnClickListener {
            task?.let { task ->
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, AddTaskFragment.newInstance(task.id))
                    .addToBackStack(null)
                    .commit()
            }
        }

        // Delete-Button
        view.findViewById<FloatingActionButton>(R.id.deleteButton).setOnClickListener {
            showDeleteConfirmationDialog()
        }
    }

    private fun loadTaskDetails(task: Task, view: View) {
        // Titel
        view.findViewById<TextView>(R.id.titleText).apply {
            text = task.title
            visibility = View.VISIBLE  // Immer sichtbar
        }

        // Beschreibung
        view.findViewById<TextView>(R.id.descriptionText).apply {
            text = if (task.description.isNotEmpty()) task.description else getString(R.string.no_description)
            visibility = View.VISIBLE  // Immer sichtbar
        }

        // Priorität
        view.findViewById<TextView>(R.id.priorityText).text = when (task.priority) {
            Priority.HIGH -> getString(R.string.high_priority)
            Priority.MEDIUM -> getString(R.string.medium_priority)
            Priority.LOW -> getString(R.string.low_priority)
        }

        // Fälligkeitsdatum und Uhrzeit
        val dueDateText = view.findViewById<TextView>(R.id.dueDateText)
        task.dueDate?.let {
            val dateTimeFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            dueDateText.text = dateTimeFormat.format(it)
            dueDateText.visibility = View.VISIBLE
        } ?: run {
            dueDateText.visibility = View.GONE
        }

        // Status
        view.findViewById<TextView>(R.id.statusText).text = 
            if (task.completed) getString(R.string.completed)
            else getString(R.string.active)

        // Tags
        val chipGroup = view.findViewById<ChipGroup>(R.id.tagChipGroup)
        chipGroup.removeAllViews()
        task.tags.forEach { tag ->
            val chip = Chip(requireContext()).apply {
                text = tag
                isClickable = false
            }
            chipGroup.addView(chip)
        }
    }

    private fun showDeleteConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.delete_task))
            .setMessage(getString(R.string.delete_task_confirmation))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                lifecycleScope.launch {
                    taskId.let { taskManager.deleteTask(it) }
                    parentFragmentManager.popBackStack()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }
} 