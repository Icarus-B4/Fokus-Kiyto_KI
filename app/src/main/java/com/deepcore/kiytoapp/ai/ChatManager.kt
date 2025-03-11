package com.deepcore.kiytoapp.ai

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.util.Date

class ChatManager(context: Context) {
    private val TAG = "ChatManager"
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val messages = mutableListOf<ChatMessage>()
    
    companion object {
        private const val PREFS_NAME = "chat_history"
        private const val KEY_MESSAGES = "messages"
        private const val KEY_ARCHIVED_CHATS = "archived_chats"
        
        val gson: Gson = GsonBuilder()
            .registerTypeAdapter(ChatAction::class.java, object : JsonSerializer<ChatAction>, JsonDeserializer<ChatAction> {
                override fun serialize(src: ChatAction, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
                    val obj = JsonObject()
                    when (src) {
                        is ChatAction.CreateTask -> {
                            obj.addProperty("type", "create_task")
                            obj.addProperty("title", src.title)
                            src.description?.let { obj.addProperty("description", it) }
                            src.priority?.let { obj.addProperty("priority", it) }
                            src.dueDate?.let { obj.addProperty("dueDate", it) }
                        }
                        is ChatAction.SetTimer -> {
                            obj.addProperty("type", "set_timer")
                            obj.addProperty("minutes", src.minutes)
                        }
                        is ChatAction.StartVoiceInput -> {
                            obj.addProperty("type", "start_voice")
                        }
                        is ChatAction.ClearHistory -> {
                            obj.addProperty("type", "clear_history")
                        }
                        is ChatAction.OpenCalendar -> {
                            obj.addProperty("type", "open_calendar")
                            src.eventTitle?.let { obj.addProperty("eventTitle", it) }
                            src.eventDate?.let { obj.addProperty("eventDate", it) }
                        }
                        is ChatAction.PlaySpotify -> {
                            obj.addProperty("type", "play_spotify")
                            src.playlistName?.let { obj.addProperty("playlistName", it) }
                        }
                    }
                    return obj
                }

                override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ChatAction {
                    val obj = json.asJsonObject
                    return when (obj.get("type").asString) {
                        "create_task" -> ChatAction.CreateTask(
                            title = obj.get("title").asString,
                            description = obj.get("description")?.asString,
                            dueDate = obj.get("dueDate")?.asString
                        )
                        "set_timer" -> ChatAction.SetTimer(
                            minutes = obj.get("minutes").asInt
                        )
                        "start_voice" -> ChatAction.StartVoiceInput
                        "clear_history" -> ChatAction.ClearHistory
                        "open_calendar" -> ChatAction.OpenCalendar(
                            eventTitle = obj.get("eventTitle")?.asString,
                            eventDate = obj.get("eventDate")?.asString
                        )
                        "play_spotify" -> ChatAction.PlaySpotify(
                            playlistName = obj.get("playlistName")?.asString
                        )
                        else -> throw JsonParseException("Unknown action type")
                    }
                }
            })
            .create()
    }

    init {
        loadMessages()
    }

    private fun loadMessages() {
        try {
            val json = prefs.getString(KEY_MESSAGES, "[]")
            Log.d(TAG, "Loading messages from prefs: $json")
            val type = object : TypeToken<List<ChatMessage>>() {}.type
            messages.clear()
            messages.addAll(gson.fromJson<List<ChatMessage>>(json, type) ?: emptyList())
            Log.d(TAG, "Loaded ${messages.size} messages")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading messages", e)
            messages.clear()
        }
    }

    private fun saveMessages() {
        try {
            val editor = prefs.edit()
            val json = gson.toJson(messages)
            Log.d(TAG, "Saving messages to prefs: $json")
            editor.putString(KEY_MESSAGES, json)
            editor.apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving messages", e)
        }
    }

    fun addMessage(message: ChatMessage) {
        messages.add(message)
        saveMessages()
        Log.d(TAG, "Added message, total messages: ${messages.size}")
    }

    fun addMessageAt(position: Int, message: ChatMessage) {
        messages.add(position, message)
        saveMessages()
        Log.d(TAG, "Added message at position $position, total messages: ${messages.size}")
    }

    fun removeMessage(position: Int) {
        messages.removeAt(position)
        saveMessages()
        Log.d(TAG, "Removed message at position $position, remaining messages: ${messages.size}")
    }

    fun getMessages(): List<ChatMessage> = messages.toList()

    fun clearHistory() {
        Log.d(TAG, "Clearing chat history")
        messages.clear()
        val editor = prefs.edit()
        editor.remove(KEY_MESSAGES)
        editor.apply()
        Log.d(TAG, "Chat history cleared")
    }

    fun archiveChat() {
        try {
            if (messages.isEmpty()) {
                Log.d(TAG, "No messages to archive")
                return
            }

            // Aktuelle Nachrichten für das Archiv speichern
            val timestamp = System.currentTimeMillis()
            val currentChat = messages.toList()
            
            Log.d(TAG, "Archiving chat with ${currentChat.size} messages")
            
            // Bestehende Archive laden
            val archivedChats = getArchivedChats().toMutableList()
            archivedChats.add(ArchivedChat(timestamp, currentChat))
            
            // Neues Archiv speichern
            val editor = prefs.edit()
            val json = gson.toJson(archivedChats)
            Log.d(TAG, "Saving archived chats: $json")
            editor.putString(KEY_ARCHIVED_CHATS, json)
            editor.apply()
            
            // Chat-Verlauf löschen
            clearHistory()
            
            Log.d(TAG, "Chat successfully archived")
        } catch (e: Exception) {
            Log.e(TAG, "Error archiving chat", e)
        }
    }

    fun getArchivedChats(): List<ArchivedChat> {
        try {
            val json = prefs.getString(KEY_ARCHIVED_CHATS, "[]")
            Log.d(TAG, "Loading archived chats: $json")
            val type = object : TypeToken<List<ArchivedChat>>() {}.type
            val chats = gson.fromJson<List<ArchivedChat>>(json, type) ?: emptyList()
            Log.d(TAG, "Loaded ${chats.size} archived chats")
            return chats
        } catch (e: Exception) {
            Log.e(TAG, "Error loading archived chats", e)
            return emptyList()
        }
    }
}

data class ArchivedChat(
    val timestamp: Long,
    val messages: List<ChatMessage>
) 