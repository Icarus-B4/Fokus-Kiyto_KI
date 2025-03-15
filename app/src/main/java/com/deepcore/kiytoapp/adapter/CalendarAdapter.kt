package com.deepcore.kiytoapp.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.deepcore.kiytoapp.R
import java.util.*

class CalendarAdapter(
    private val context: Context,
    private val onDayClick: (Date) -> Unit
) : RecyclerView.Adapter<CalendarAdapter.CalendarViewHolder>() {

    private var days: List<CalendarDay> = emptyList()
    private var selectedPosition = -1

    fun submitList(days: List<CalendarDay>) {
        this.days = days
        notifyDataSetChanged()
    }

    fun selectDay(date: Date) {
        val calendar = Calendar.getInstance()
        calendar.time = date
        
        val dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH)
        val newPosition = days.indexOfFirst { it.dayOfMonth == dayOfMonth && !it.isPlaceholder }
        
        if (newPosition != -1 && newPosition != selectedPosition) {
            val oldPosition = selectedPosition
            selectedPosition = newPosition
            
            if (oldPosition != -1) {
                notifyItemChanged(oldPosition)
            }
            notifyItemChanged(selectedPosition)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CalendarViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_day, parent, false)
        return CalendarViewHolder(view)
    }

    override fun onBindViewHolder(holder: CalendarViewHolder, position: Int) {
        val day = days[position]
        
        // Setze den Tag
        holder.tvDay.text = if (day.isPlaceholder) "" else day.dayOfMonth.toString()
        
        // Setze den Hintergrund und die Textfarbe basierend auf dem Auswahlstatus
        val isSelected = position == selectedPosition
        holder.tvDay.isSelected = isSelected
        
        if (isSelected) {
            holder.tvDay.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        } else {
            holder.tvDay.setTextColor(ContextCompat.getColor(context, android.R.color.white))
        }
        
        // Setze den Klick-Listener
        if (!day.isPlaceholder) {
            holder.itemView.setOnClickListener {
                val oldPosition = selectedPosition
                selectedPosition = holder.adapterPosition
                
                if (oldPosition != -1) {
                    notifyItemChanged(oldPosition)
                }
                notifyItemChanged(selectedPosition)
                
                onDayClick(day.date)
            }
        } else {
            holder.itemView.setOnClickListener(null)
        }
    }

    override fun getItemCount(): Int = days.size

    class CalendarViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvDay: TextView = itemView.findViewById(R.id.tvDay)
    }
}

data class CalendarDay(
    val date: Date,
    val dayOfMonth: Int,
    val isPlaceholder: Boolean = false
) 