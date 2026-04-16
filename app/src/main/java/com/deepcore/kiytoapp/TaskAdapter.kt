package com.deepcore.kiytoapp

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deepcore.kiytoapp.data.Priority
import com.deepcore.kiytoapp.data.Task
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

class TaskAdapter(
    private val onTaskChecked: (Task) -> Unit,
    private val onTaskClicked: (Task) -> Unit
) : ListAdapter<Task, TaskAdapter.TaskViewHolder>(TaskDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_task, parent, false)
        return TaskViewHolder(view, onTaskChecked, onTaskClicked)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class TaskViewHolder(
        itemView: View,
        private val onTaskChecked: (Task) -> Unit,
        private val onTaskClicked: (Task) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val taskCard: MaterialCardView = itemView.findViewById(R.id.taskCard)
        private val titleText: TextView = itemView.findViewById(R.id.titleText)
        private val descriptionText: TextView = itemView.findViewById(R.id.descriptionText)
        private val dueDateText: TextView = itemView.findViewById(R.id.dueDateText)
        private val tagsText: TextView = itemView.findViewById(R.id.tagsText)
        private val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
        
        private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        private var currentTask: Task? = null

        init {
            checkbox.setOnClickListener {
                currentTask?.let(onTaskChecked)
            }
            
            taskCard.setOnClickListener {
                currentTask?.let(onTaskClicked)
            }
        }

        fun bind(task: Task) {
            currentTask = task
            
            titleText.text = task.title
            descriptionText.text = task.description
            descriptionText.visibility = if (task.description.isNotEmpty()) View.VISIBLE else View.GONE
            
            // Fälligkeitsdatum
            task.dueDate?.let {
                dueDateText.text = dateFormat.format(it)
                dueDateText.visibility = View.VISIBLE
            } ?: run {
                dueDateText.visibility = View.GONE
            }
            
            // Tags
            if (task.tags.isNotEmpty()) {
                tagsText.text = task.tags.joinToString(" • ")
                tagsText.visibility = View.VISIBLE
            } else {
                tagsText.visibility = View.GONE
            }
            
            // Checkbox-Status
            checkbox.isChecked = task.completed
            
            // Visuelles Feedback für erledigte Aufgaben
            if (task.completed) {
                titleText.paintFlags = titleText.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                taskCard.alpha = 0.7f
            } else {
                titleText.paintFlags = titleText.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                taskCard.alpha = 1.0f
            }
            
            // Hintergrundfarbe basierend auf Priorität
            val backgroundColor = when (task.priority) {
                Priority.HIGH -> if (task.completed) 
                    R.color.priority_high_completed else R.color.priority_high
                Priority.MEDIUM -> if (task.completed)
                    R.color.priority_medium_completed else R.color.priority_medium
                Priority.LOW -> if (task.completed)
                    R.color.priority_low_completed else R.color.priority_low
            }
            taskCard.setCardBackgroundColor(itemView.context.getColor(backgroundColor))
        }
    }
}

private class TaskDiffCallback : DiffUtil.ItemCallback<Task>() {
    override fun areItemsTheSame(oldItem: Task, newItem: Task): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Task, newItem: Task): Boolean {
        return oldItem == newItem
    }
} 