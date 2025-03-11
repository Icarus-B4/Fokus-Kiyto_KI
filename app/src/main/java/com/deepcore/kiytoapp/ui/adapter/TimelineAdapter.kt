package com.deepcore.kiytoapp.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.viewmodel.TimelineItem
import com.deepcore.kiytoapp.viewmodel.TimelineItemType
import java.text.SimpleDateFormat
import java.util.*

class TimelineAdapter(
    private val onItemClick: (TimelineItem) -> Unit,
    private val onEditClick: (TimelineItem) -> Unit
) : ListAdapter<TimelineItem, TimelineAdapter.TimelineViewHolder>(TimelineDiffCallback()) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timeline, parent, false)
        return TimelineViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item)
    }

    inner class TimelineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvTime: TextView = itemView.findViewById(R.id.tvTimelineTime)
        private val tvTitle: TextView = itemView.findViewById(R.id.tvTimelineTitle)
        private val ivIcon: ImageView = itemView.findViewById(R.id.timelineIcon)
        private val timelineDot: View = itemView.findViewById(R.id.timelineDot)
        private val timelineIndicator: View = itemView.findViewById(R.id.timelineIndicator)
        private val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditTask)

        fun bind(item: TimelineItem) {
            // Setze die Uhrzeit
            tvTime.text = timeFormat.format(item.startTime)
            
            // Setze den Titel
            tvTitle.text = item.title
            
            // Styling basierend auf dem Typ
            val (dotColor, indicatorColor, iconRes) = when (item.type) {
                TimelineItemType.TASK -> {
                    Triple(
                        if (item.completed) R.color.completed_task else R.color.blue,
                        R.color.gray_dark,
                        R.drawable.ic_tasks
                    )
                }
                TimelineItemType.HABIT -> {
                    Triple(
                        if (item.completed) R.color.completed_task else R.color.coral,
                        R.color.gray_dark,
                        R.drawable.ic_habit
                    )
                }
                TimelineItemType.MEETING -> {
                    Triple(
                        if (item.completed) R.color.completed_task else R.color.coral,
                        R.color.gray_dark,
                        R.drawable.ic_meeting
                    )
                }
            }

            // Setze die Farbe des Dots
            timelineDot.background.setTint(ContextCompat.getColor(itemView.context, dotColor))
            
            // Setze die Farbe des Indikators
            timelineIndicator.setBackgroundColor(ContextCompat.getColor(itemView.context, indicatorColor))
            
            // Icon setzen
            ivIcon.setImageResource(iconRes)
            
            // Transparenz für erledigte Aufgaben
            itemView.alpha = if (item.completed) 0.5f else 1.0f

            // Zeige den Indikator nur, wenn es nicht das letzte Element ist
            if (bindingAdapterPosition == itemCount - 1) {
                timelineIndicator.visibility = View.INVISIBLE
            } else {
                timelineIndicator.visibility = View.VISIBLE
                
                // Verlängere den Indikator für einen besseren visuellen Effekt
                val params = timelineIndicator.layoutParams
                params.height = 200 // Höhe in Pixeln
                timelineIndicator.layoutParams = params
            }

            // Click-Listener
            itemView.setOnClickListener { onItemClick(item) }
            btnEdit.setOnClickListener { onEditClick(item) }
        }
    }

    private class TimelineDiffCallback : DiffUtil.ItemCallback<TimelineItem>() {
        override fun areItemsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
            return oldItem == newItem
        }
    }
} 