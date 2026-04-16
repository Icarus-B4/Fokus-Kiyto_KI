package com.deepcore.kiytoapp.ai

import android.animation.ValueAnimator
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import androidx.recyclerview.widget.RecyclerView
import com.deepcore.kiytoapp.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.*

class ChatAdapter(
    private val onActionClicked: (ChatAction) -> Unit,
    private val onActionItemClicked: (ActionItem) -> Unit,
    private val onMessageLongClicked: (ChatMessage, Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private var pinnedMessage: ChatMessage? = null
    private var pinnedView: View? = null
    private var pinnedMessageChangedListener: ((ChatMessage?) -> Unit)? = null
    private var recyclerView: RecyclerView? = null
    private val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    
    // Getter für die aktuelle Liste
    val currentList: List<ChatMessage>
        get() = messages.toList()

    fun addOnPinnedMessageChangedListener(listener: (ChatMessage?) -> Unit) {
        pinnedMessageChangedListener = listener
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    fun getMessage(position: Int): ChatMessage {
        return messages[position]
    }

    fun removeMessage(position: Int) {
        if (messages[position].isPinned) {
            pinnedMessage = null
            pinnedView = null
        }
        val message = messages[position]
        messages.removeAt(position)
        notifyItemRemoved(position)
    }

    fun addMessageAt(position: Int, message: ChatMessage) {
        messages.add(position, message)
        notifyItemInserted(position)
    }

    fun clearMessages() {
        messages.clear()
        pinnedMessage = null
        pinnedView = null
        notifyDataSetChanged()
    }

    fun setMessages(newMessages: List<ChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun toggleMessagePin(position: Int) {
        val message = messages[position]
        
        // Wenn eine andere Nachricht bereits angepinnt ist, diese zuerst entpinnen
        pinnedMessage?.let { pinned ->
            val pinnedIndex = messages.indexOf(pinned)
            if (pinnedIndex != -1) {
                pinned.isPinned = false
                notifyItemChanged(pinnedIndex)
            }
        }

        // Toggle den Pin-Status der ausgewählten Nachricht
        message.isPinned = !message.isPinned
        
        if (message.isPinned) {
            pinnedMessage = message
        } else {
            pinnedMessage = null
        }
        
        notifyItemChanged(position)
        pinnedMessageChangedListener?.invoke(pinnedMessage)
    }

    fun getPinnedMessage(): ChatMessage? = pinnedMessage

    fun removeLastMessage() {
        if (messages.isNotEmpty()) {
            val lastIndex = messages.size - 1
            messages.removeAt(lastIndex)
            notifyItemRemoved(lastIndex)
        }
    }

    override fun getItemViewType(position: Int): Int {
        val message = messages[position]
        return when {
            message.isUser -> VIEW_TYPE_USER
            message.isTyping -> VIEW_TYPE_ASSISTANT_TYPING
            else -> VIEW_TYPE_ASSISTANT
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> {
                val view = inflater.inflate(R.layout.item_user_message, parent, false)
                UserMessageViewHolder(view, onMessageLongClicked)
            }
            VIEW_TYPE_ASSISTANT_TYPING -> {
                val view = inflater.inflate(R.layout.item_assistant_message, parent, false)
                TypingViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_assistant_message, parent, false)
                MessageViewHolder(view, onActionClicked, onActionItemClicked, onMessageLongClicked)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        
        when (holder) {
            is UserMessageViewHolder -> holder.bind(message, position)
            is MessageViewHolder -> holder.bind(message, position)
            is TypingViewHolder -> holder.startTypingAnimation()
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    inner class UserMessageViewHolder(
        view: View,
        private val onMessageLongClicked: (ChatMessage, Int) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.messageText)
        
        fun bind(message: ChatMessage, position: Int) {
            messageText.text = message.content
            
            itemView.setOnLongClickListener {
                onMessageLongClicked(message, position)
                true
            }
        }
    }

    inner class MessageViewHolder(
        view: View,
        private val onActionClicked: (ChatAction) -> Unit,
        private val onActionItemClicked: (ActionItem) -> Unit,
        private val onMessageLongClicked: (ChatMessage, Int) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.messageText)
        private val actionButton: Button? = view.findViewById(R.id.actionButton)
        
        fun bind(message: ChatMessage, position: Int) {
            messageText.text = message.content
            
            // Alte ChatAction-Typen anzeigen
            if (message.action != null && actionButton != null) {
                actionButton.visibility = View.VISIBLE
                
                when (val action = message.action) {
                    is ChatAction.OpenCalendar -> {
                        setActionButton("Kalender öffnen", action)
                    }
                    is ChatAction.SetTimer -> {
                        setActionButton("${action.minutes} Min. Timer starten", action)
                    }
                    is ChatAction.CreateTask -> {
                        setActionButton("Aufgabe erstellen: ${action.title}", action)
                    }
                    else -> {
                        actionButton.visibility = View.GONE
                    }
                }
            } else if (actionButton != null) {
                actionButton.visibility = View.GONE
            }
            
            // Neue ActionItems anzeigen - wir verwenden vorhandene Views
            if (message.chatActions?.isNotEmpty() == true && actionButton != null) {
                actionButton.visibility = View.VISIBLE
                actionButton.text = message.chatActions.firstOrNull()?.label ?: ""
                actionButton.setOnClickListener {
                    message.chatActions?.firstOrNull()?.let { item ->
                        onActionItemClicked(item)
                    }
                }
            }
            
            itemView.setOnLongClickListener {
                onMessageLongClicked(message, position)
                true
            }
        }
        
        private fun setActionButton(text: String, action: ChatAction) {
            actionButton?.apply {
                this.text = text
                visibility = View.VISIBLE
                setOnClickListener { onActionClicked(action) }
            }
        }
    }

    inner class TypingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val messageText: TextView = view.findViewById(R.id.messageText)
        
        init {
            messageText.text = "Assistent schreibt..."
        }
        
        fun startTypingAnimation() {
            // Einfache Anzeige, keine Animation
        }
    }

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
        private const val VIEW_TYPE_ASSISTANT_TYPING = 3
    }
} 