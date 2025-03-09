package com.deepcore.kiytoapp.ai

data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val action: ChatAction? = null,
    val timestamp: Long = System.currentTimeMillis(),
    var isPinned: Boolean = false,
    val imageUri: String? = null,
    val fileUri: String? = null
) {
    val text: String
        get() = content
}

sealed class ChatAction {
    data class CreateTask(
        val title: String,
        val description: String? = null,
        val priority: String? = null,
        val dueDate: String? = null
    ) : ChatAction()
    
    data class SetTimer(
        val minutes: Int
    ) : ChatAction()
    
    data object StartVoiceInput : ChatAction()
    
    data object ClearHistory : ChatAction()
    
    data class OpenCalendar(
        val eventTitle: String? = null,
        val eventDate: String? = null
    ) : ChatAction()
    
    data class PlaySpotify(
        val playlistName: String? = null
    ) : ChatAction()
} 