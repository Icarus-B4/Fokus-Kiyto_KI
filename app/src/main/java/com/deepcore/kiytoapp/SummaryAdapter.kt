package com.deepcore.kiytoapp

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.PopupMenu
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.deepcore.kiytoapp.data.entity.Summary
import com.deepcore.kiytoapp.databinding.ItemSummaryBinding
import java.text.SimpleDateFormat
import java.util.Locale

class SummaryAdapter(
    private val onItemClick: (Summary) -> Unit,
    private val onCreateTask: (Summary) -> Unit,
    private val onItemLongClick: (Summary) -> Unit
) : ListAdapter<Summary, SummaryAdapter.SummaryViewHolder>(SummaryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SummaryViewHolder {
        val binding = ItemSummaryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return SummaryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SummaryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class SummaryViewHolder(
        private val binding: ItemSummaryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }

            binding.root.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemLongClick(getItem(position))
                }
                true
            }
        }

        private fun showPopupMenu(view: android.view.View, summary: Summary) {
            PopupMenu(view.context, view).apply {
                inflate(R.menu.menu_summary_item)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_create_task -> {
                            onItemClick(summary)
                            true
                        }
                        else -> false
                    }
                }
                show()
            }
        }

        fun bind(summary: Summary) {
            binding.apply {
                titleText.text = summary.title
                contentText.text = summary.content
                dateText.text = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(summary.createdAt)
                checkBox.isChecked = summary.isCompleted
            }
        }
    }

    private class SummaryDiffCallback : DiffUtil.ItemCallback<Summary>() {
        override fun areItemsTheSame(oldItem: Summary, newItem: Summary): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Summary, newItem: Summary): Boolean {
            return oldItem == newItem
        }
    }
} 