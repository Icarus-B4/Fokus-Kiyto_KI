package com.deepcore.kiytoapp.adapter

import android.content.Context
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
    private val context: Context,
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
        
        // Setze die Uhrzeit
        holder.tvTime.text = timeFormat.format(item.startTime)
        
        // Setze den Titel
        holder.tvTitle.text = item.title
        
        // Setze das Icon basierend auf dem Typ
        val iconResId = when (item.type) {
            TimelineItemType.TASK -> R.drawable.ic_tasks
            TimelineItemType.HABIT -> R.drawable.ic_habit
            TimelineItemType.MEETING -> R.drawable.ic_meeting
        }
        holder.ivIcon.setImageResource(iconResId)
        
        // Setze die Farbe des Dots basierend auf dem Typ
        val dotColor = when (item.type) {
            TimelineItemType.TASK -> R.color.blue
            TimelineItemType.HABIT -> R.color.green
            TimelineItemType.MEETING -> R.color.red
        }
        holder.timelineDot.background.setTint(ContextCompat.getColor(context, dotColor))
        
        // Setze die Farbe des Indikators basierend auf dem Typ
        holder.timelineIndicator.setBackgroundColor(ContextCompat.getColor(context, dotColor))
        
        // Setze die Transparenz basierend auf dem Erledigungsstatus
        val alpha = if (item.completed) 0.5f else 1.0f
        holder.itemView.alpha = alpha
        
        // Setze den Klick-Listener für das gesamte Item
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
        
        // Setze den Klick-Listener für den Bearbeiten-Button
        holder.btnEdit.setOnClickListener {
            onEditClick(item)
        }
        
        // Zeige den Indikator nur, wenn es nicht das letzte Element ist
        if (position == itemCount - 1) {
            holder.timelineIndicator.visibility = View.INVISIBLE
        } else {
            holder.timelineIndicator.visibility = View.VISIBLE
            
            // Verlängere den Indikator für einen besseren visuellen Effekt
            val params = holder.timelineIndicator.layoutParams
            params.height = 200 // Höhe in Pixeln
            holder.timelineIndicator.layoutParams = params
        }
    }

    class TimelineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTime: TextView = itemView.findViewById(R.id.tvTimelineTime)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTimelineTitle)
        val ivIcon: ImageView = itemView.findViewById(R.id.timelineIcon)
        val timelineDot: View = itemView.findViewById(R.id.timelineDot)
        val timelineIndicator: View = itemView.findViewById(R.id.timelineIndicator)
        val btnEdit: ImageButton = itemView.findViewById(R.id.btnEditTask)
    }
}

class TimelineDiffCallback : DiffUtil.ItemCallback<TimelineItem>() {
    override fun areItemsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: TimelineItem, newItem: TimelineItem): Boolean {
        return oldItem == newItem
    }
} 