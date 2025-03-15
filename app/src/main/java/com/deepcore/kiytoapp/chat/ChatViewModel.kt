package com.deepcore.kiytoapp.chat

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.deepcore.kiytoapp.data.Task

class ChatViewModel : ViewModel() {
    private val _taskSuggestions = MutableLiveData<List<TaskSuggestion>>()
    val taskSuggestions: LiveData<List<TaskSuggestion>> = _taskSuggestions

    fun analyzeTask(task: Task) {
        // Hier würde die KI-Analyse stattfinden
        // Fürs Erste geben wir Beispiel-Vorschläge zurück
        val suggestions = listOf(
            TaskSuggestion(
                title = "Ähnliche Aufgabe: ${task.title} Review",
                description = "Überprüfen Sie die Ergebnisse von ${task.title}",
                confidence = 0.85f
            ),
            TaskSuggestion(
                title = "Follow-up: ${task.title}",
                description = "Planen Sie die nächsten Schritte für ${task.title}",
                confidence = 0.75f
            )
        )
        _taskSuggestions.value = suggestions
    }
}

data class TaskSuggestion(
    val title: String,
    val description: String,
    val confidence: Float
) 