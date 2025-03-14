package com.deepcore.kiytoapp.ui

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.slider.Slider
import com.google.android.material.tabs.TabLayout
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.base.BaseFragment
import com.deepcore.kiytoapp.databinding.FragmentFocusModeBinding
import com.deepcore.kiytoapp.viewmodel.PomodoroViewModel

class FocusModeFragment : BaseFragment() {

    private var _binding: FragmentFocusModeBinding? = null
    private val binding get() = _binding!!
    
    private val viewModel: PomodoroViewModel by viewModels {
        PomodoroViewModelFactory(requireContext())
    }
    
    companion object {
        private const val TAG = "FocusModeFragment"
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFocusModeBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        initializeSliders()
        initializeStatistics()
        setupPomodoroTimer()
        setupObservers()
        setupSettings()
    }
    
    override fun onResume() {
        super.onResume()
    }
    
    /**
     * Setzt die Timer-Dauer in Minuten
     * Diese Methode kann von außen aufgerufen werden, um die Timer-Dauer zu ändern
     */
    fun setTimerDuration(minutes: Int) {
        try {
            Log.d(TAG, "Timer-Dauer wird auf $minutes Minuten gesetzt")
            
            // Begrenze den Wert auf maximal 60 Minuten (Slider-Maximum)
            val limitedMinutes = if (minutes > 60) 60 else minutes
            
            if (limitedMinutes != minutes) {
                Log.d(TAG, "Timer-Dauer auf 60 Minuten begrenzt (ursprünglich $minutes)")
            }
            
            // Stelle sicher, dass der Pomodoro-Modus ausgewählt ist
            binding.modeTabLayout.getTabAt(0)?.select()
            viewModel.setMode(PomodoroViewModel.PomodoroMode.POMODORO)
            
            // Setze die Timer-Dauer im ViewModel
            viewModel.setPomodoroMinutes(limitedMinutes.toFloat())
            
            // Aktualisiere den Slider, falls nötig
            binding.focusTimeSlider.value = limitedMinutes.toFloat()
            
            // Setze den Timer zurück, falls er bereits läuft
            viewModel.resetTimer()
            binding.startButton.setImageResource(R.drawable.ic_play)
            binding.startButton.contentDescription = getString(R.string.start_timer)
            
            Log.d(TAG, "Timer-Dauer erfolgreich auf $limitedMinutes Minuten gesetzt")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Setzen der Timer-Dauer", e)
        }
    }
    
    /**
     * Startet den Timer automatisch
     * Diese Methode kann von außen aufgerufen werden, um den Timer zu starten
     */
    fun startTimer() {
        try {
            Log.d(TAG, "Timer wird automatisch gestartet")
            
            // Starte den Timer nur, wenn er nicht bereits läuft
            if (viewModel.timerState.value != PomodoroViewModel.TimerState.RUNNING) {
                viewModel.startTimer()
                binding.startButton.setImageResource(R.drawable.ic_pause)
                binding.startButton.contentDescription = getString(R.string.pause_timer)
                Log.d(TAG, "Timer erfolgreich gestartet")
            } else {
                Log.d(TAG, "Timer läuft bereits, kein erneuter Start notwendig")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim automatischen Starten des Timers", e)
        }
    }
    
    private fun initializeSliders() {
        try {
            // Lade die gespeicherten Werte aus den SharedPreferences
            val prefs = requireContext().getSharedPreferences("pomodoro_prefs", Context.MODE_PRIVATE)
            
            // Konfiguriere die Slider
            binding.focusTimeSlider.apply {
                valueFrom = 1f  // Minimum 1 Minute
                valueTo = 60f   // Maximum 60 Minuten
                stepSize = 1f   // 1-Minuten-Schritte
                value = prefs.getLong("pomodoro_length", 25).toFloat()
            }
            
            binding.shortBreakSlider.apply {
                valueFrom = 1f  // Minimum 1 Minute
                valueTo = 30f   // Maximum 30 Minuten
                stepSize = 1f   // 1-Minuten-Schritte
                value = prefs.getLong("short_break_length", 5).toFloat()
            }
            
            binding.longBreakSlider.apply {
                valueFrom = 1f  // Minimum 1 Minute
                valueTo = 45f   // Maximum 45 Minuten
                stepSize = 1f   // 1-Minuten-Schritte
                value = prefs.getLong("long_break_length", 15).toFloat()
            }
            
            binding.pomodorosUntilLongBreakSlider.apply {
                valueFrom = 1f  // Minimum 1 Pomodoro
                valueTo = 10f   // Maximum 10 Pomodoros
                stepSize = 1f   // 1-Pomodoro-Schritte
                value = prefs.getInt("pomodoros_until_long_break", 4).toFloat()
            }
            
            Log.d("FocusModeFragment", "Slider initialisiert: Pomodoro=${binding.focusTimeSlider.value}, " +
                    "ShortBreak=${binding.shortBreakSlider.value}, LongBreak=${binding.longBreakSlider.value}, " +
                    "PomodorosUntilLongBreak=${binding.pomodorosUntilLongBreakSlider.value}")
        } catch (e: Exception) {
            Log.e("FocusModeFragment", "Fehler bei der Initialisierung der Slider", e)
        }
    }
    
    private fun initializeStatistics() {
        try {
            viewModel.completedPomodoros.observe(viewLifecycleOwner) { count ->
                binding.completedPomodorosText.text = getString(R.string.completed_pomodoros, count)
            }
            
            viewModel.todaysFocusTime.observe(viewLifecycleOwner) { minutes ->
                binding.focusTimeText.text = getString(R.string.focus_time_minutes, minutes)
            }
            
            Log.d("FocusModeFragment", "Statistiken initialisiert")
        } catch (e: Exception) {
            Log.e("FocusModeFragment", "Fehler bei der Initialisierung der Statistiken", e)
        }
    }
    
    private fun setupPomodoroTimer() {
        try {
            binding.startButton.setOnClickListener {
                when (viewModel.timerState.value) {
                    PomodoroViewModel.TimerState.STOPPED,
                    PomodoroViewModel.TimerState.COMPLETED -> {
                        viewModel.startTimer()
                        binding.startButton.setImageResource(R.drawable.ic_pause)
                        binding.startButton.contentDescription = getString(R.string.pause_timer)
                    }
                    PomodoroViewModel.TimerState.PAUSED -> {
                        viewModel.startTimer()
                        binding.startButton.setImageResource(R.drawable.ic_pause)
                        binding.startButton.contentDescription = getString(R.string.pause_timer)
                    }
                    PomodoroViewModel.TimerState.RUNNING -> {
                        viewModel.pauseTimer()
                        binding.startButton.setImageResource(R.drawable.ic_play)
                        binding.startButton.contentDescription = getString(R.string.resume_timer)
                    }
                    else -> Log.w("FocusModeFragment", "Unbekannter Timer-Status")
                }
            }
            
            binding.resetButton.setOnClickListener {
                viewModel.resetTimer()
                binding.startButton.setImageResource(R.drawable.ic_play)
                binding.startButton.contentDescription = getString(R.string.start_timer)
            }
            
            binding.modeTabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabSelected(tab: TabLayout.Tab?) {
                    when (tab?.position) {
                        0 -> viewModel.setMode(PomodoroViewModel.PomodoroMode.POMODORO)
                        1 -> viewModel.setMode(PomodoroViewModel.PomodoroMode.SHORT_BREAK)
                        2 -> viewModel.setMode(PomodoroViewModel.PomodoroMode.LONG_BREAK)
                    }
                }
                
                override fun onTabUnselected(tab: TabLayout.Tab?) {}
                override fun onTabReselected(tab: TabLayout.Tab?) {}
            })
            
            Log.d("FocusModeFragment", "Pomodoro Timer Setup abgeschlossen")
        } catch (e: Exception) {
            Log.e("FocusModeFragment", "Fehler beim Setup des Pomodoro Timers", e)
        }
    }
    
    private fun setupObservers() {
        try {
            viewModel.currentTime.observe(viewLifecycleOwner) { time ->
                binding.timerText.text = time
            }
            
            viewModel.timerState.observe(viewLifecycleOwner) { state ->
                updateTimerUI(state)
            }
            
            viewModel.currentMode.observe(viewLifecycleOwner) { mode ->
                when (mode) {
                    PomodoroViewModel.PomodoroMode.POMODORO -> binding.modeTabLayout.selectTab(binding.modeTabLayout.getTabAt(0))
                    PomodoroViewModel.PomodoroMode.SHORT_BREAK -> binding.modeTabLayout.selectTab(binding.modeTabLayout.getTabAt(1))
                    PomodoroViewModel.PomodoroMode.LONG_BREAK -> binding.modeTabLayout.selectTab(binding.modeTabLayout.getTabAt(2))
                }
            }
            
            Log.d("FocusModeFragment", "Observer Setup abgeschlossen")
        } catch (e: Exception) {
            Log.e("FocusModeFragment", "Fehler beim Setup der Observer", e)
        }
    }
    
    private fun updateTimerUI(state: PomodoroViewModel.TimerState) {
        when (state) {
            PomodoroViewModel.TimerState.RUNNING -> {
                binding.startButton.setImageResource(R.drawable.ic_pause)
                binding.startButton.contentDescription = getString(R.string.pause_timer)
            }
            PomodoroViewModel.TimerState.PAUSED -> {
                binding.startButton.setImageResource(R.drawable.ic_play)
                binding.startButton.contentDescription = getString(R.string.resume_timer)
            }
            PomodoroViewModel.TimerState.STOPPED,
            PomodoroViewModel.TimerState.COMPLETED -> {
                binding.startButton.setImageResource(R.drawable.ic_play)
                binding.startButton.contentDescription = getString(R.string.start_timer)
            }
        }
    }
    
    private fun setupSettings() {
        try {
            binding.focusTimeSlider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    viewModel.updatePomodoroLength(value.toLong())
                    binding.focusTimeLabel.text = "Fokuszeit: ${value.toInt()} Minuten"
                }
            }
            
            binding.shortBreakSlider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    viewModel.updateShortBreakLength(value.toLong())
                    binding.shortBreakLabel.text = "Kurze Pause: ${value.toInt()} Minuten"
                }
            }
            
            binding.longBreakSlider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    viewModel.updateLongBreakLength(value.toLong())
                    binding.longBreakLabel.text = "Lange Pause: ${value.toInt()} Minuten"
                }
            }
            
            binding.pomodorosUntilLongBreakSlider.addOnChangeListener { _, value, fromUser ->
                if (fromUser) {
                    viewModel.updatePomodorosUntilLongBreak(value.toInt())
                    binding.pomodorosUntilLongBreakLabel.text = "Pomodoros bis zur langen Pause: ${value.toInt()}"
                }
            }

            // Initial-Werte setzen
            binding.focusTimeLabel.text = "Fokuszeit: ${binding.focusTimeSlider.value.toInt()} Minuten"
            binding.shortBreakLabel.text = "Kurze Pause: ${binding.shortBreakSlider.value.toInt()} Minuten"
            binding.longBreakLabel.text = "Lange Pause: ${binding.longBreakSlider.value.toInt()} Minuten"
            binding.pomodorosUntilLongBreakLabel.text = "Pomodoros bis zur langen Pause: ${binding.pomodorosUntilLongBreakSlider.value.toInt()}"
            
            Log.d("FocusModeFragment", "Einstellungen Setup abgeschlossen")
        } catch (e: Exception) {
            Log.e("FocusModeFragment", "Fehler beim Setup der Einstellungen", e)
        }
    }
    
    fun getViewModelInstance(): PomodoroViewModel {
        return viewModel
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        try {
            // Glide-Anfragen bereinigen, um Speicherlecks zu vermeiden
            context?.let {
                com.bumptech.glide.Glide.with(it).clear(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                    override fun onResourceReady(
                        resource: android.graphics.drawable.Drawable,
                        transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                    ) {
                        // Nichts zu tun
                    }

                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                        // Nichts zu tun
                    }
                })
            }
        } catch (e: Exception) {
            android.util.Log.e("FocusModeFragment", "Fehler beim Bereinigen von Glide", e)
        }
        
        _binding = null
    }
    
    class PomodoroViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PomodoroViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PomodoroViewModel(context) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
} 