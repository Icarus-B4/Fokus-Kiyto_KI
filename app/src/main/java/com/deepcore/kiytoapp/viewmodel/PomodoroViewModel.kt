package com.deepcore.kiytoapp.viewmodel

import android.content.Context
import android.content.SharedPreferences
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.data.SessionManager
import com.deepcore.kiytoapp.util.NotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

class PomodoroViewModel(private val context: Context) : ViewModel() {
    
    private var timerJob: Job? = null
    private lateinit var notificationHelper: NotificationHelper
    private val prefs: SharedPreferences = context.getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        when (key) {
            "daily_pomodoros" -> _completedPomodoros.value = prefs.getInt(key, 0)
            "daily_focus_time" -> _todaysFocusTime.value = prefs.getInt(key, 0)
        }
    }
    
    // SessionManager für die Synchronisierung der Fokuszeit
    private val sessionManager = SessionManager(context)
    
    // Timer-Zustände
    private val _timerState = MutableLiveData<TimerState>(TimerState.STOPPED)
    val timerState: LiveData<TimerState> = _timerState
    
    private val _currentTime = MutableLiveData<String>("25:00")
    val currentTime: LiveData<String> = _currentTime
    
    private val _currentMode = MutableLiveData<PomodoroMode>(PomodoroMode.POMODORO)
    val currentMode: LiveData<PomodoroMode> = _currentMode
    
    // Statistik
    private val _completedPomodoros = MutableLiveData<Int>(0)
    val completedPomodoros: LiveData<Int> = _completedPomodoros
    
    private val _todaysFocusTime = MutableLiveData<Int>(0)
    val todaysFocusTime: LiveData<Int> = _todaysFocusTime
    
    // Timer-Dauern in Minuten (jetzt konfigurierbar)
    private var pomodoroLength: Long
        get() = prefs.getLong("pomodoro_length", 25L)
        set(value) = prefs.edit().putLong("pomodoro_length", value).apply()
    
    private var shortBreakLength: Long
        get() = prefs.getLong("short_break_length", 5L)
        set(value) = prefs.edit().putLong("short_break_length", value).apply()
    
    private var longBreakLength: Long
        get() = prefs.getLong("long_break_length", 15L)
        set(value) = prefs.edit().putLong("long_break_length", value).apply()
    
    private var pomodorosUntilLongBreak: Int
        get() = prefs.getInt("pomodoros_until_long_break", 4)
        set(value) = prefs.edit().putInt("pomodoros_until_long_break", value).apply()
    
    private var remainingTimeInSeconds = pomodoroLength * 60
    private var completedPomodorosInSession = 0
    
    init {
        try {
            notificationHelper = NotificationHelper(context)
            prefs.registerOnSharedPreferenceChangeListener(prefsListener)
            loadDailyStats()
            loadTimerSettings()
            
            // Globale Statistiken aktualisieren
            updateGlobalStatistics()
            
            Log.d("PomodoroViewModel", "Initialized with pomodoro: $pomodoroLength, short: $shortBreakLength, long: $longBreakLength")
        } catch (e: Exception) {
            Log.e("PomodoroViewModel", "Error in initialization", e)
        }
    }
    
    private fun loadTimerSettings() {
        remainingTimeInSeconds = when (_currentMode.value) {
            PomodoroMode.POMODORO -> pomodoroLength * 60
            PomodoroMode.SHORT_BREAK -> shortBreakLength * 60
            PomodoroMode.LONG_BREAK -> longBreakLength * 60
            else -> pomodoroLength * 60
        }
        updateTimerText()
    }
    
    /**
     * Setzt die Pomodoro-Dauer in Minuten
     * Diese Methode kann von außen aufgerufen werden, um die Timer-Dauer zu ändern
     */
    fun setPomodoroMinutes(minutes: Float) {
        try {
            val minutesLong = minutes.toLong()
            Log.d("PomodoroViewModel", "Setze Pomodoro-Dauer auf $minutesLong Minuten")
            
            // Speichere die neue Dauer
            pomodoroLength = minutesLong
            
            // Aktualisiere den Timer, wenn wir im Pomodoro-Modus sind
            if (_currentMode.value == PomodoroMode.POMODORO) {
                remainingTimeInSeconds = minutesLong * 60
                updateTimerText()
            }
            
            Log.d("PomodoroViewModel", "Pomodoro-Dauer erfolgreich auf $minutesLong Minuten gesetzt")
        } catch (e: Exception) {
            Log.e("PomodoroViewModel", "Fehler beim Setzen der Pomodoro-Dauer", e)
        }
    }
    
    fun startTimer() {
        if (_timerState.value == TimerState.RUNNING) return
        
        _timerState.value = TimerState.RUNNING
        Log.d("PomodoroViewModel", "Starting timer in mode: ${_currentMode.value}, time: $remainingTimeInSeconds")
        
        timerJob = viewModelScope.launch {
            while (remainingTimeInSeconds > 0) {
                delay(1000)
                remainingTimeInSeconds--
                updateTimerText()
            }
            handleTimerComplete()
        }
    }
    
    fun pauseTimer() {
        timerJob?.cancel()
        _timerState.value = TimerState.PAUSED
        Log.d("PomodoroViewModel", "Timer paused")
    }
    
    fun resetTimer() {
        timerJob?.cancel()
        _timerState.value = TimerState.STOPPED
        remainingTimeInSeconds = when (_currentMode.value) {
            PomodoroMode.POMODORO -> pomodoroLength * 60
            PomodoroMode.SHORT_BREAK -> shortBreakLength * 60
            PomodoroMode.LONG_BREAK -> longBreakLength * 60
            else -> pomodoroLength * 60
        }
        updateTimerText()
        Log.d("PomodoroViewModel", "Timer reset in mode: ${_currentMode.value}, time: $remainingTimeInSeconds")
    }
    
    fun setMode(mode: PomodoroMode) {
        _currentMode.value = mode
        remainingTimeInSeconds = when (mode) {
            PomodoroMode.POMODORO -> pomodoroLength * 60
            PomodoroMode.SHORT_BREAK -> shortBreakLength * 60
            PomodoroMode.LONG_BREAK -> longBreakLength * 60
        }
        updateTimerText()
        _timerState.value = TimerState.STOPPED
        Log.d("PomodoroViewModel", "Mode set to: $mode, time: $remainingTimeInSeconds")
    }
    
    private fun updateTimerText() {
        val minutes = remainingTimeInSeconds / 60
        val seconds = remainingTimeInSeconds % 60
        _currentTime.value = String.format("%02d:%02d", minutes, seconds)
    }
    
    private fun handleTimerComplete() {
        try {
            _timerState.value = TimerState.COMPLETED
            Log.d("PomodoroViewModel", "Timer completed in mode: ${_currentMode.value}")
            
            when (_currentMode.value) {
                PomodoroMode.POMODORO -> {
                    // Statistik aktualisieren
                    completedPomodorosInSession++
                    updateCompletedPomodoros()
                    updateTodaysFocusTime(pomodoroLength.toInt())
                    
                    // Automatischer Wechsel zur passenden Pause
                    if (completedPomodorosInSession % pomodorosUntilLongBreak == 0) {
                        Log.d("PomodoroViewModel", "Starting long break after $completedPomodorosInSession pomodoros")
                        setMode(PomodoroMode.LONG_BREAK)
                        showNotification(
                            context.getString(R.string.focus_session_complete),
                            context.getString(R.string.long_break_start)
                        )
                        startTimer() // Automatisch die Pause starten
                    } else {
                        Log.d("PomodoroViewModel", "Starting short break after $completedPomodorosInSession pomodoros")
                        setMode(PomodoroMode.SHORT_BREAK)
                        showNotification(
                            context.getString(R.string.focus_session_complete),
                            context.getString(R.string.short_break_start)
                        )
                        startTimer() // Automatisch die Pause starten
                    }
                }
                PomodoroMode.SHORT_BREAK, PomodoroMode.LONG_BREAK -> {
                    Log.d("PomodoroViewModel", "Break completed, returning to pomodoro mode")
                    setMode(PomodoroMode.POMODORO)
                    showNotification(
                        context.getString(R.string.break_complete),
                        context.getString(R.string.pomodoro_start)
                    )
                    // Hier starten wir den Timer NICHT automatisch
                }
                else -> {}
            }
        } catch (e: Exception) {
            Log.e("PomodoroViewModel", "Error in handleTimerComplete", e)
        }
    }
    
    private fun showNotification(title: String, message: String) {
        try {
            Log.d("PomodoroViewModel", "Showing notification: $title - $message")
            notificationHelper.showTimerCompleteNotification(title, message)
        } catch (e: Exception) {
            Log.e("PomodoroViewModel", "Error showing notification", e)
        }
    }
    
    private fun updateCompletedPomodoros() {
        val currentPomodoros = _completedPomodoros.value ?: 0
        val newValue = currentPomodoros + 1
        _completedPomodoros.value = newValue
        
        // Direkt in SharedPreferences speichern
        prefs.edit().putInt("daily_pomodoros", newValue).apply()
        
        // Globale Statistik aktualisieren
        updateGlobalStatistics()
        
        Log.d("PomodoroViewModel", "Updated completed pomodoros: $newValue")
    }
    
    private fun updateTodaysFocusTime(minutes: Int) {
        val currentFocusTime = _todaysFocusTime.value ?: 0
        val newValue = currentFocusTime + minutes
        _todaysFocusTime.value = newValue
        
        // Direkt in SharedPreferences speichern
        prefs.edit().putInt("daily_focus_time", newValue).apply()
        
        // Globale Statistik aktualisieren
        updateGlobalStatistics()
        
        // Fokuszeit auch im SessionManager aktualisieren
        sessionManager.addFocusTime(minutes)
        
        Log.d("PomodoroViewModel", "Updated focus time: $newValue")
    }
    
    private fun updateGlobalStatistics() {
        try {
            // Aktualisiere die globalen Statistiken in den SharedPreferences
            val globalPrefs = context.getSharedPreferences("global_stats", Context.MODE_PRIVATE)
            
            // Aktuelle Werte lesen
            val pomodoros = _completedPomodoros.value ?: 0
            val focusTime = _todaysFocusTime.value ?: 0
            
            // In globalen Einstellungen speichern
            globalPrefs.edit()
                .putInt("daily_pomodoros", pomodoros)
                .putInt("daily_focus_time", focusTime)
                .apply()
            
            Log.d("PomodoroViewModel", "Global statistics updated: pomodoros=$pomodoros, focusTime=$focusTime")
        } catch (e: Exception) {
            Log.e("PomodoroViewModel", "Error updating global statistics", e)
        }
    }
    
    private fun loadDailyStats() {
        val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        val lastSavedDay = prefs.getInt("last_saved_day", -1)
        
        if (today != lastSavedDay) {
            // Neuer Tag - Stats zurücksetzen
            _completedPomodoros.postValue(0)
            _todaysFocusTime.postValue(0)
            completedPomodorosInSession = 0
            prefs.edit()
                .putInt("daily_pomodoros", 0)
                .putInt("daily_focus_time", 0)
                .putInt("last_saved_day", today)
                .apply()
        } else {
            // Laden der gespeicherten Stats
            val savedPomodoros = prefs.getInt("daily_pomodoros", 0)
            val savedFocusTime = prefs.getInt("daily_focus_time", 0)
            _completedPomodoros.postValue(savedPomodoros)
            _todaysFocusTime.postValue(savedFocusTime)
        }
        Log.d("PomodoroViewModel", "Loaded stats: pomodoros=${_completedPomodoros.value}, focusTime=${_todaysFocusTime.value}")
    }
    
    private fun saveDailyStats() {
        prefs.edit()
            .putInt("daily_pomodoros", _completedPomodoros.value ?: 0)
            .putInt("daily_focus_time", _todaysFocusTime.value ?: 0)
            .putInt("last_saved_day", Calendar.getInstance().get(Calendar.DAY_OF_YEAR))
            .apply()
        Log.d("PomodoroViewModel", "Saved stats: pomodoros=${_completedPomodoros.value}, focusTime=${_todaysFocusTime.value}")
    }
    
    // Einstellungen für Timer-Dauern
    fun updatePomodoroLength(minutes: Long) {
        pomodoroLength = minutes
        Log.d("PomodoroViewModel", "Updated pomodoro length: $minutes")
        if (_currentMode.value == PomodoroMode.POMODORO) {
            setMode(PomodoroMode.POMODORO) // Kompletter Reset des Modus
        }
    }
    
    fun updateShortBreakLength(minutes: Long) {
        shortBreakLength = minutes
        Log.d("PomodoroViewModel", "Updated short break length: $minutes")
        if (_currentMode.value == PomodoroMode.SHORT_BREAK) {
            setMode(PomodoroMode.SHORT_BREAK) // Kompletter Reset des Modus
        }
    }
    
    fun updateLongBreakLength(minutes: Long) {
        longBreakLength = minutes
        Log.d("PomodoroViewModel", "Updated long break length: $minutes")
        if (_currentMode.value == PomodoroMode.LONG_BREAK) {
            setMode(PomodoroMode.LONG_BREAK) // Kompletter Reset des Modus
        }
    }
    
    fun updatePomodorosUntilLongBreak(count: Int) {
        pomodorosUntilLongBreak = count
        Log.d("PomodoroViewModel", "Updated pomodoros until long break: $count")
    }
    
    enum class TimerState {
        STOPPED, RUNNING, PAUSED, COMPLETED
    }
    
    enum class PomodoroMode {
        POMODORO, SHORT_BREAK, LONG_BREAK
    }
    
    override fun onCleared() {
        super.onCleared()
        timerJob?.cancel()
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        Log.d("PomodoroViewModel", "ViewModel cleared")
    }
} 