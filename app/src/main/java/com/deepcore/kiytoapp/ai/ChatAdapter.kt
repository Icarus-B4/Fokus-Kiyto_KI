package com.deepcore.kiytoapp.ai

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.deepcore.kiytoapp.R
import com.google.android.material.button.MaterialButton
import com.bumptech.glide.Glide

class ChatAdapter(
    private val onActionClicked: (ChatAction) -> Unit,
    private val onMessageLongClicked: (ChatMessage, Int) -> Unit
) : RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()
    private var pinnedMessage: ChatMessage? = null
    private var pinnedView: View? = null
    private var pinnedMessageChangedListener: ((ChatMessage?) -> Unit)? = null
    private var recyclerView: RecyclerView? = null
    
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layout = if (viewType == VIEW_TYPE_USER) {
            R.layout.item_user_message
        } else {
            R.layout.item_assistant_message
        }
        
        val view = LayoutInflater.from(parent.context)
            .inflate(layout, parent, false)
        return MessageViewHolder(view, onActionClicked, onMessageLongClicked)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount() = messages.size

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        this.recyclerView = null
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].isUser) VIEW_TYPE_USER else VIEW_TYPE_ASSISTANT
    }

    class MessageViewHolder(
        itemView: View,
        private val onActionClicked: (ChatAction) -> Unit,
        private val onMessageLongClicked: (ChatMessage, Int) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val messageCard: LinearLayout = itemView.findViewById(R.id.messageCard)
        private val messageText: TextView = itemView.findViewById(R.id.messageText)
        private val messageImage: ImageView = itemView.findViewById(R.id.messageImage)
        private val actionButton: MaterialButton = itemView.findViewById(R.id.actionButton)
        private val pinIndicator: View = itemView.findViewById(R.id.pinIndicator)
        private val pinIcon: ImageView = itemView.findViewById(R.id.pinIcon)

        init {
            itemView.setOnLongClickListener {
                val position = absoluteAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val adapter = itemView.parent as? RecyclerView
                    val chatAdapter = adapter?.adapter as? ChatAdapter
                    chatAdapter?.let { adapter ->
                        onMessageLongClicked(adapter.getMessage(position), position)
                    }
                }
                true
            }
        }

        fun bind(message: ChatMessage) {
            messageText.text = message.content
            
            // Bild anzeigen wenn vorhanden
            message.imageUri?.let { uriString ->
                try {
                    val uri = android.net.Uri.parse(uriString)
                    messageImage.visibility = View.VISIBLE
                    try {
                        Glide.with(itemView.context)
                            .load(uri)
                            .placeholder(R.drawable.ic_image)
                            .error(R.drawable.ic_image)
                            .into(messageImage)
                    } catch (e: Exception) {
                        android.util.Log.e("ChatAdapter", "Fehler beim Laden des Bildes mit Glide: $uriString", e)
                        messageImage.visibility = View.GONE
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatAdapter", "Fehler beim Verarbeiten der Bild-URI: $uriString", e)
                    messageImage.visibility = View.GONE
                }
            } ?: run {
                messageImage.visibility = View.GONE
            }
            
            // Setze die Ausrichtung und den Stil basierend auf dem Nachrichtentyp
            if (message.isUser) {
                messageCard.setBackgroundResource(R.drawable.bg_message_user)
                messageText.setTextColor(itemView.context.getColor(R.color.user_message_text))
                (messageCard.layoutParams as FrameLayout.LayoutParams).apply {
                    gravity = Gravity.END
                    marginStart = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_large)
                    marginEnd = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_small)
                }
            } else {
                messageCard.setBackgroundResource(R.drawable.bg_message)
                messageText.setTextColor(itemView.context.getColor(R.color.assistant_message_text))
                (messageCard.layoutParams as FrameLayout.LayoutParams).apply {
                    gravity = Gravity.START
                    marginStart = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_small)
                    marginEnd = itemView.context.resources.getDimensionPixelSize(R.dimen.message_margin_large)
                }
            }

            // Zeige den Pin-Indikator und das Icon nur für angepinnte Nachrichten
            pinIndicator.visibility = if (message.isPinned) View.VISIBLE else View.GONE
            pinIcon.visibility = if (message.isPinned) View.VISIBLE else View.GONE

            // Setze den Action-Button wenn vorhanden
            message.action?.let { action ->
                actionButton.apply {
                    visibility = View.VISIBLE
                    when (action) {
                        is ChatAction.OpenCalendar -> {
                            text = itemView.context.getString(R.string.open_calendar)
                            setIconResource(R.drawable.ic_calendar)
                            setIconTintResource(R.color.primary)
                            setTextColor(itemView.context.getColor(R.color.primary))
                        }
                        is ChatAction.SetTimer -> {
                            text = itemView.context.getString(R.string.start_timer)
                            setIconResource(R.drawable.ic_timer)
                            setIconTintResource(R.color.primary)
                            setTextColor(itemView.context.getColor(R.color.primary))
                        }
                        is ChatAction.CreateTask -> {
                            text = itemView.context.getString(R.string.create_task)
                            setIconResource(R.drawable.ic_category)
                            setIconTintResource(R.color.primary)
                            setTextColor(itemView.context.getColor(R.color.primary))
                        }
                        is ChatAction.PlaySpotify -> {
                            text = itemView.context.getString(R.string.play_music)
                            setIconResource(R.drawable.ic_music)
                            setIconTintResource(R.color.primary)
                            setTextColor(itemView.context.getColor(R.color.primary))
                        }
                        else -> {
                            visibility = View.GONE
                        }
                    }
                    setOnClickListener { onActionClicked(action) }
                }
            } ?: run {
                actionButton.visibility = View.GONE
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_USER = 1
        private const val VIEW_TYPE_ASSISTANT = 2
    }
} 