package com.deepcore.kiytoapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.deepcore.kiytoapp.data.entity.Task
import com.deepcore.kiytoapp.data.TaskDatabase
import com.deepcore.kiytoapp.data.dao.TaskDao
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.*

class DayVisualizerViewModel(application: Application) : AndroidViewModel(application) {
    private val taskDao = TaskDatabase.getDatabase(application).taskDao()
    
    private val _selectedDate = MutableLiveData<Date>()
    val selectedDate: LiveData<Date> = _selectedDate

    private val _timelineItems = MutableLiveData<List<TimelineItem>>()
    val timelineItems: LiveData<List<TimelineItem>> = _timelineItems

    private val _selectedTask = MutableLiveData<TimelineItem?>()
    val selectedTask: LiveData<TimelineItem?> = _selectedTask

    init {
        setSelectedDate(Date())
    }

    fun setSelectedDate(date: Date) {
        _selectedDate.value = date
        loadTimelineItems(date)
    }

    fun selectTask(task: TimelineItem?) {
        _selectedTask.value = task
    }

    fun setSampleTimelineItems(items: List<TimelineItem>) {
        _timelineItems.value = items
    }
    
    fun saveSampleTimelineItems(items: List<TimelineItem>) {
        viewModelScope.launch {
            // Lösche zuerst alle vorhandenen Aufgaben
            // taskDao.deleteAll() // Auskommentiert, um vorhandene Aufgaben nicht zu löschen
            
            // Speichere die Beispieldaten in der Datenbank
            for (item in items) {
                val task = Task(
                    id = item.id,
                    title = item.title,
                    description = "",
                    dueDate = item.startTime,
                    completed = item.completed,
                    priority = when (item.type) {
                        TimelineItemType.TASK -> Task.Priority.MEDIUM
                        TimelineItemType.HABIT -> Task.Priority.LOW
                        TimelineItemType.MEETING -> Task.Priority.HIGH
                    }
                )
                taskDao.insert(task)
            }
            
            // Lade die Aufgaben neu
            _selectedDate.value?.let { loadTimelineItems(it) }
        }
    }

    private fun loadTimelineItems(date: Date) {
        viewModelScope.launch {
            try {
                taskDao.getAllTasks().collectLatest { tasks ->
                    val filteredTasks = tasks.filter { task ->
                        val taskDate = task.dueDate?.let { Calendar.getInstance().apply { time = it } }
                        val selectedDate = Calendar.getInstance().apply { time = date }
                        
                        taskDate?.get(Calendar.YEAR) == selectedDate.get(Calendar.YEAR) &&
                        taskDate.get(Calendar.MONTH) == selectedDate.get(Calendar.MONTH) &&
                        taskDate.get(Calendar.DAY_OF_MONTH) == selectedDate.get(Calendar.DAY_OF_MONTH)
                    }
                    
                    // Sortiere die Aufgaben nach Uhrzeit
                    val sortedTasks = filteredTasks.sortedBy { it.dueDate }
                    
                    _timelineItems.value = sortedTasks.map { it.toTimelineItem() }
                    
                    // Wenn ein Task ausgewählt war und nicht mehr in der Liste ist, Auswahl zurücksetzen
                    _selectedTask.value?.let { selectedTask ->
                        if (_timelineItems.value?.none { it.id == selectedTask.id } == true) {
                            _selectedTask.value = null
                        }
                    }
                }
            } catch (e: Exception) {
                // Fehlerbehandlung
                _timelineItems.value = emptyList()
            }
        }
    }

    fun deleteSelectedTask() {
        viewModelScope.launch {
            _selectedTask.value?.let { timelineItem ->
                val task = Task(
                    id = timelineItem.id,
                    title = timelineItem.title,
                    description = "",
                    dueDate = timelineItem.startTime,
                    completed = timelineItem.completed,
                    priority = when (timelineItem.type) {
                        TimelineItemType.TASK -> Task.Priority.MEDIUM
                        TimelineItemType.HABIT -> Task.Priority.LOW
                        TimelineItemType.MEETING -> Task.Priority.HIGH
                    }
                )
                taskDao.delete(task)
                _selectedTask.value = null
                
                // Lade die Aufgaben neu
                _selectedDate.value?.let { loadTimelineItems(it) }
            }
        }
    }

    fun copySelectedTask() {
        viewModelScope.launch {
            _selectedTask.value?.let { timelineItem ->
                val task = Task(
                    title = timelineItem.title,
                    description = "",
                    dueDate = timelineItem.startTime,
                    completed = false,
                    priority = when (timelineItem.type) {
                        TimelineItemType.TASK -> Task.Priority.MEDIUM
                        TimelineItemType.HABIT -> Task.Priority.LOW
                        TimelineItemType.MEETING -> Task.Priority.HIGH
                    }
                )
                taskDao.insert(task)
                
                // Lade die Aufgaben neu
                _selectedDate.value?.let { loadTimelineItems(it) }
            }
        }
    }

    fun completeSelectedTask() {
        viewModelScope.launch {
            _selectedTask.value?.let { timelineItem ->
                taskDao.updateCompletionStatus(timelineItem.id, true)
                
                // Aktualisiere den ausgewählten Task, um die UI sofort zu aktualisieren
                _selectedTask.value = _selectedTask.value?.copy(completed = true)
                
                // Lade die Aufgaben neu, um die Änderung in der Liste zu reflektieren
                _selectedDate.value?.let { loadTimelineItems(it) }
            }
        }
    }

    // Hilfsmethode, um die Task-ID zu erhalten
    fun getSelectedTaskId(): Long? {
        return _selectedTask.value?.id
    }

    private fun Task.toTimelineItem(): TimelineItem {
        return TimelineItem(
            id = this.id,
            title = this.title,
            startTime = this.dueDate ?: Date(),
            endTime = null,
            completed = this.completed,
            type = when (this.priority) {
                Task.Priority.HIGH -> TimelineItemType.MEETING
                Task.Priority.MEDIUM -> TimelineItemType.TASK
                Task.Priority.LOW -> TimelineItemType.HABIT
            }
        )
    }
}

data class TimelineItem(
    val id: Long,
    val title: String,
    val startTime: Date,
    val endTime: Date?,
    val completed: Boolean = false,
    val type: TimelineItemType
)

enum class TimelineItemType {
    TASK,
    HABIT,
    MEETING
} 