package com.deepcore.kiytoapp

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.deepcore.kiytoapp.ai.*
import com.deepcore.kiytoapp.base.BaseFragment
import com.deepcore.kiytoapp.data.SessionManager
import com.deepcore.kiytoapp.data.TaskManager
import com.deepcore.kiytoapp.databinding.FragmentAiChatBinding
import com.deepcore.kiytoapp.ui.FocusModeFragment
import com.deepcore.kiytoapp.viewmodel.PomodoroViewModel
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class AIChatFragment : BaseFragment(), APISettingsDialog.OnApiKeySetListener {
    private lateinit var messageInput: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var suggestionChipGroup: ChipGroup
    private lateinit var adapter: com.deepcore.kiytoapp.ai.ChatAdapter
    private lateinit var taskAIService: TaskAIService
    private lateinit var taskManager: TaskManager
    private lateinit var sessionManager: SessionManager
    private lateinit var chatManager: ChatManager
    private lateinit var speechManager: SpeechManager
    private lateinit var voiceButton: ImageButton
    private var isSpeaking = false
    private lateinit var pinnedMessageContainer: ViewGroup
    private lateinit var plusButton: ImageButton
    private lateinit var plusMenuContainer: LinearLayout
    private lateinit var binding: FragmentAiChatBinding
    private lateinit var photosOption: LinearLayout
    private lateinit var cameraOption: LinearLayout
    private lateinit var filesOption: LinearLayout
    private lateinit var createImageOption: LinearLayout
    private lateinit var collectIdeasOption: LinearLayout
    private lateinit var analyzeImagesOption: LinearLayout
    private lateinit var moreOption: LinearLayout
    private lateinit var imageGenerationService: ImageGenerationService
    private lateinit var loadingDialog: AlertDialog
    private var toneGenerator: ToneGenerator? = null

    private val PERMISSION_REQUEST_CODE = 100
    private val PERMISSIONS_REQUEST_CODE = 101
    
    // ActivityResultLauncher für Berechtigungsanfragen
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var requestMultiplePermissionsLauncher: ActivityResultLauncher<Array<String>>
    
    // ActivityResultLauncher für Bild- und Dateiauswahl
    private lateinit var pickImageLauncher: ActivityResultLauncher<Intent>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var pickFileLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            // Berechtigungsanfrage-Launcher initialisieren
            requestPermissionLauncher = registerForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                if (isGranted) {
                    // Berechtigung wurde gewährt, Spracherkennung starten
                    startVoiceRecognition()
                } else {
                    // Berechtigung wurde verweigert
                    showMicPermissionDeniedDialog()
                }
            }
            
            requestMultiplePermissionsLauncher = registerForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) { permissions ->
                val deniedPermissions = permissions.filter { !it.value }.keys.toList()
                
                if (deniedPermissions.isNotEmpty()) {
                    Log.w("AIChatFragment", "Einige Berechtigungen wurden verweigert: ${deniedPermissions.joinToString()}")
                    showPermissionExplanationDialog(deniedPermissions)
                } else {
                    Log.d("AIChatFragment", "Alle angeforderten Berechtigungen wurden gewährt")
                }
            }
            
            // Bild- und Dateiauswahl-Launcher initialisieren
            pickImageLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        processImage(uri)
                    }
                }
            }
            
            takePictureLauncher = registerForActivityResult(
                ActivityResultContracts.TakePicture()
            ) { success ->
                if (success) {
                    currentPhotoUri?.let { uri ->
                        processImage(uri)
                    }
                }
            }
            
            pickFileLauncher = registerForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.data?.let { uri ->
                        processFile(uri)
                    }
                }
            }
            
            // Cache-Verzeichnis bereinigen
            cleanImageCache()
            
            // Services initialisieren
            taskAIService = TaskAIService(requireContext())
            chatManager = ChatManager(requireContext())
            taskManager = TaskManager(requireContext())
            imageGenerationService = ImageGenerationService(requireContext())
            // SpeechManager korrekt initialisieren
            speechManager = SpeechManager(requireContext(), OpenAIService).apply {
                initialize()
            }
            
            Log.d("AIChatFragment", "Fragment-Initialisierung erfolgreich")
            
            // Berechtigungen prüfen und anfordern
            checkRequiredPermissions()
            
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler bei der Fragment-Initialisierung", e)
            showErrorDialog(R.string.initialization_error)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentAiChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        try {
            initializeViews(view)
            setupRecyclerView()
            setupSendButton()
            setupSuggestionChips()
            setupPinnedMessage()
            setupPlusMenu()
            setupSoundEffects()

            // Chat-Historie erst laden wenn alles initialisiert ist
            view.post {
                loadChatHistory()
            }

            // Menü-Provider hinzufügen (ersetzt setHasOptionsMenu)
            requireActivity().addMenuProvider(object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    menuInflater.inflate(R.menu.chat_menu, menu)
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        R.id.action_api_settings -> {
                            showApiSettingsDialog()
                            true
                        }
                        R.id.action_clear_history -> {
                            showClearHistoryDialog()
                            true
                        }
                        R.id.action_archive_chat -> {
                            archiveCurrentChat()
                            true
                        }
                        R.id.action_view_archives -> {
                            showArchivedChats()
                            true
                        }
                        else -> false
                    }
                }
            }, viewLifecycleOwner, Lifecycle.State.RESUMED)

        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler in onViewCreated", e)
        }
    }

    private fun initializeViews(view: View) {
        try {
            messageInput = view.findViewById(R.id.messageInput)
            sendButton = view.findViewById(R.id.sendButton)
            voiceButton = view.findViewById(R.id.voiceButton)
            recyclerView = view.findViewById(R.id.recyclerView)
            suggestionChipGroup = view.findViewById(R.id.suggestionChipGroup)
            pinnedMessageContainer = view.findViewById(R.id.pinnedMessageContainer)
            plusButton = view.findViewById(R.id.plusButton)
            plusMenuContainer = view.findViewById(R.id.plusMenuContainer)
            photosOption = view.findViewById(R.id.photosOption)
            cameraOption = view.findViewById(R.id.cameraOption)
            filesOption = view.findViewById(R.id.filesOption)
            createImageOption = view.findViewById(R.id.createImageOption)
            collectIdeasOption = view.findViewById(R.id.collectIdeasOption)
            analyzeImagesOption = view.findViewById(R.id.analyzeImagesOption)
            moreOption = view.findViewById(R.id.moreOption)

            // Speech-Manager initialisieren
            speechManager = SpeechManager(requireContext(), OpenAIService)

            setupVoiceButton()

            Log.d("AIChatFragment", "Views erfolgreich initialisiert")

        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler bei der View-Initialisierung", e)
            throw e
        }
    }

    private fun setupSoundEffects() {
        try {
            // ToneGenerator für Systemtöne initialisieren
            toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, 80) // 80% Lautstärke
            Log.d("AIChatFragment", "Soundeffekte erfolgreich initialisiert")
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler beim Initialisieren der Soundeffekte", e)
        }
    }

    private fun setupVoiceButton() {
        voiceButton.setOnClickListener {
            if (isSpeaking) {
                // Stopp-Sound abspielen
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 150) // 150ms Ton
                } catch (e: Exception) {
                    Log.e("AIChatFragment", "Fehler beim Abspielen des Stopp-Sounds", e)
                }
                stopSpeaking()
            } else {
                // Start-Sound abspielen
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) // 150ms Ton
                } catch (e: Exception) {
                    Log.e("AIChatFragment", "Fehler beim Abspielen des Start-Sounds", e)
                }
                startVoiceRecognition()
            }
        }

        // Status-Callback für den SpeechManager
        speechManager.setStatusCallback { isRecording, isSpeaking ->
            activity?.runOnUiThread {
                this.isSpeaking = isRecording || isSpeaking
                voiceButton.setImageResource(
                    when {
                        isRecording -> R.drawable.ic_mic_active
                        isSpeaking -> R.drawable.ic_stop
                        else -> R.drawable.ic_mic
                    }
                )
            }
        }
    }

    private fun startVoiceRecognition() {
        if (checkPermission()) {
            CoroutineScope(Dispatchers.Main).launch {
                try {
                    val spokenText = withContext(Dispatchers.IO) {
                        speechManager.startListening()
                    }
                    
                    if (!spokenText.isNullOrEmpty()) {
                        // Schnelle lokale Befehlserkennung
                        val command = spokenText.lowercase()
                        var commandExecuted = false
                        
                        // Timer-Befehle - Direkte Ausführung
                        if (command.contains("timer") || command.contains("fokus")) {
                            val minutes = extractTimerMinutes(spokenText)
                            if (minutes != null) {
                                handleChatAction(ChatAction.SetTimer(minutes))
                                commandExecuted = true
                            }
                        }
                        // Aufgaben-Befehle - Direkte Ausführung
                        else if (command.contains("aufgabe") || command.contains("task")) {
                            val title = extractTaskTitle(spokenText)
                            if (title.isNotEmpty()) {
                                handleChatAction(ChatAction.CreateTask(title))
                                commandExecuted = true
                            }
                        }
                        // Chat löschen - Direkte Ausführung
                        else if ((command.contains("lösche") && command.contains("chat")) ||
                            (command.contains("chat") && command.contains("löschen")) ||
                            (command.contains("clear") && command.contains("chat"))) {
                            handleChatAction(ChatAction.ClearHistory)
                            commandExecuted = true
                        }
                        // Kalender öffnen - Direkte Ausführung
                        else if (command.contains("öffne") && command.contains("kalender")) {
                            handleChatAction(ChatAction.OpenCalendar())
                            commandExecuted = true
                        }
                        // Spotify/Musik - Direkte Ausführung
                        else if (command.contains("spotify") || command.contains("musik")) {
                            handleChatAction(ChatAction.PlaySpotify())
                            commandExecuted = true
                        }

                        // Nachricht zum Chat hinzufügen
                        val message = ChatMessage(spokenText.trim(), true)
                        withContext(Dispatchers.Main) {
                            adapter.addMessage(message)
                            chatManager.addMessage(message)
                            scrollToBottom()
                        }
                        
                        // Wenn kein lokaler Befehl erkannt wurde, KI-Antwort generieren
                        if (!commandExecuted) {
                            withContext(Dispatchers.IO) {
                                val response = taskAIService.generateResponse(spokenText)
                                // Starte die Sprachausgabe sofort
                                launch { speakResponse(response.text) }
                                
                                // Aktualisiere die UI parallel zur Sprachausgabe
                                withContext(Dispatchers.Main) {
                                    adapter.addMessage(response)
                                    chatManager.addMessage(response)
                                    scrollToBottom()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AIChatFragment", "Fehler bei der Spracherkennung", e)
                    withContext(Dispatchers.Main) {
                        val errorMsg = ChatMessage(
                            "Entschuldigung, es gab einen Fehler bei der Spracherkennung. Bitte versuchen Sie es erneut.",
                            false
                        )
                        adapter.addMessage(errorMsg)
                        chatManager.addMessage(errorMsg)
                        scrollToBottom()
                    }
                }
            }
        }
    }

    private fun checkPermission(): Boolean {
        try {
            val permission = android.Manifest.permission.RECORD_AUDIO
            val permissionCheck = requireContext().checkSelfPermission(permission)
            
            if (permissionCheck != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                // Prüfe, ob wir dem Benutzer erklären sollten, warum wir die Berechtigung brauchen
                if (shouldShowRequestPermissionRationale(permission)) {
                    showPermissionRationaleDialog(permission)
                } else {
                    // Direkt nach Berechtigung fragen
                    requestPermissionLauncher.launch(permission)
                }
                return false
            }
            return true
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler bei der Berechtigungsprüfung", e)
            return false
        }
    }

    private fun showPermissionRationaleDialog(permission: String) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mikrofonberechtigung benötigt")
            .setMessage("Die App benötigt Zugriff auf das Mikrofon, um Sprachbefehle zu erkennen. Bitte gewähren Sie die Berechtigung im nächsten Dialog.")
            .setPositiveButton("OK") { _, _ ->
                requestPermissionLauncher.launch(permission)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun checkRequiredPermissions() {
        val requiredPermissions = mutableListOf<String>()
        
        // Prüfe Kamera-Berechtigung
        if (requireContext().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.CAMERA)
        }
        
        // Prüfe Speicher-Berechtigung
        if (requireContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        
        if (requiredPermissions.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    private fun setupRecyclerView() {
        recyclerView = binding.recyclerView
        adapter = ChatAdapter(
            onActionClicked = { action ->
                handleChatAction(action)
            },
            onMessageLongClicked = { message, position ->
                showMessageOptions(message, position)
            }
        )
        
        recyclerView.apply {
            setHasFixedSize(true)
            layoutManager = LinearLayoutManager(context).apply {
                stackFromEnd = true
            }
            adapter = this@AIChatFragment.adapter
            
            // RecyclerView Optimierungen
            setItemViewCacheSize(20)
            recycledViewPool.setMaxRecycledViews(0, 20)
        }

        // Swipe-to-Delete Setup
        setupSwipeToDelete()
    }

    private fun setupSwipeToDelete() {
        val swipeHandler = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(
            0,
            androidx.recyclerview.widget.ItemTouchHelper.LEFT or androidx.recyclerview.widget.ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val message = adapter.getMessage(position)
                
                // Nachricht aus Adapter und Manager entfernen
                adapter.removeMessage(position)
                chatManager.removeMessage(position)

                // Snackbar mit Undo-Option anzeigen
                com.google.android.material.snackbar.Snackbar.make(
                    requireView(),
                    getString(R.string.message_deleted),
                    com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                )
                .setAction(getString(R.string.undo)) {
                    adapter.addMessageAt(position, message)
                    chatManager.addMessageAt(position, message)
                }
                .setAnchorView(requireActivity().findViewById(R.id.bottomNavigation))
                .setBackgroundTint(resources.getColor(R.color.snackbar_background, null))
                .setActionTextColor(resources.getColor(R.color.snackbar_action, null))
                .setTextColor(resources.getColor(R.color.white, null))
                .show()
            }
        }

        androidx.recyclerview.widget.ItemTouchHelper(swipeHandler).attachToRecyclerView(recyclerView)
    }

    private fun scrollToBottom() {
        try {
            val lastPosition = adapter.itemCount - 1
            if (lastPosition >= 0) {
                recyclerView.scrollToPosition(lastPosition)
            }
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler beim Scrollen", e)
        }
    }

    private fun loadChatHistory() {
        lifecycleScope.launch(Dispatchers.IO) {
            val messages = chatManager.getMessages()
            withContext(Dispatchers.Main) {
                adapter.setMessages(messages)
                scrollToBottom()
                
                // Wenn keine Nachrichten vorhanden sind, zeige die Begrüßungsnachricht an
                if (messages.isEmpty()) {
                    showWelcomeMessage()
                }
            }
        }
    }

    private fun setupSendButton() {
        sendButton.setOnClickListener {
            val message = messageInput.text.toString().trim()
            if (message.isNotEmpty()) {
                sendMessage(message)
                messageInput.text.clear()
            }
        }
    }

    private fun setupSuggestionChips() {
        suggestionChipGroup.setOnCheckedStateChangeListener { group, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val chip = group.findViewById<Chip>(checkedIds.first())
                sendMessage(chip.text.toString())
                group.clearCheck()
            }
        }
    }

    private fun sendMessage(message: String) {
        try {
            val userMessage = ChatMessage(message, true)
            adapter.addMessage(userMessage)
            chatManager.addMessage(userMessage)
            scrollToBottom()

            CoroutineScope(Dispatchers.Main).launch {
                try {
                    // PDF-Analyse-Aktionen
                    val lastBotMessage = chatManager.getMessages()
                        .lastOrNull { !it.isUser && it.content.contains("Möchten Sie:") }
                    
                    if (lastBotMessage != null) {
                        when {
                            message.contains("1") || message.lowercase().contains("speichern") -> {
                                val summary = chatManager.getMessages()
                                    .lastOrNull { !it.isUser && it.content.startsWith("📄 PDF-Analyse") }
                                    ?.content
                                    ?.substringAfter("\n\n")
                                    ?: return@launch
                                
                                // Aufgabe erstellen
                                val task = com.deepcore.kiytoapp.data.Task(
                                    title = "PDF-Zusammenfassung",
                                    description = summary,
                                    priority = com.deepcore.kiytoapp.data.Priority.MEDIUM,
                                    created = Date()
                                )
                                taskManager.createTask(task)
                                
                                val confirmationMessage = ChatMessage(
                                    "✓ Die PDF-Analyse wurde als Aufgabe gespeichert.",
                                    false
                                )
                                adapter.addMessage(confirmationMessage)
                                chatManager.addMessage(confirmationMessage)
                                return@launch
                            }
                            
                            message.contains("2") || message.lowercase().contains("teilen") -> {
                                val summary = chatManager.getMessages()
                                    .lastOrNull { !it.isUser && it.content.startsWith("📄 PDF-Analyse") }
                                    ?.content
                                    ?: return@launch
                                
                                val shareIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, summary)
                                }
                                startActivity(Intent.createChooser(shareIntent, "PDF-Analyse teilen"))
                                return@launch
                            }
                            
                            message.contains("3") || message.lowercase().contains("frag") -> {
                                val response = ChatMessage(
                                    "Natürlich! Stellen Sie Ihre Frage zum Inhalt der PDF-Datei.",
                                    false
                                )
                                adapter.addMessage(response)
                                chatManager.addMessage(response)
                                return@launch
                            }
                        }
                    }

                    // Normale Nachrichtenverarbeitung fortsetzen...
                    // Befehlserkennung vor AI-Antwort
                    val command = message.lowercase()
                    when {
                        // Aufgaben-Befehle
                        command.contains("aufgabe") || command.contains("task") -> {
                            try {
                                val title = when {
                                    command.startsWith("aufgabe ") -> message.substringAfter("aufgabe ", "").trim()
                                    command.startsWith("task ") -> message.substringAfter("task ", "").trim()
                                    else -> ""
                                }

                                Log.d("AIChatFragment", "Extrahierter Titel: '$title'")

                                // Prüfen ob der Titel gültig ist
                                if (title.isNotEmpty() &&
                                    !title.equals("aufgabe", ignoreCase = true) &&
                                    !title.equals("task", ignoreCase = true)) {

                                    Log.d("AIChatFragment", "Erstelle neue Aufgabe: $title")

                                    // Task erstellen
                                    val task = com.deepcore.kiytoapp.data.Task(
                                        title = title,
                                        description = "",
                                        priority = com.deepcore.kiytoapp.data.Priority.MEDIUM,
                                        created = Date()
                                    )

                                    try {
                                        taskManager.createTask(task)
                                        Log.d("AIChatFragment", "Task erfolgreich erstellt")

                                        // Bestätigungsnachricht
                                        val confirmationMessage = ChatMessage(
                                            "✓ Neue Aufgabe erstellt: \"$title\"",
                                            false
                                        )
                                        adapter.addMessage(confirmationMessage)
                                        chatManager.addMessage(confirmationMessage)
                                        scrollToBottom()

                                        // Dashboard aktualisieren
                                        try {
                                            val dashboardFragment = parentFragmentManager.findFragmentByTag("dashboard") as? DashboardFragment
                                            Log.d("AIChatFragment", "DashboardFragment gefunden: ${dashboardFragment != null}")
                                            dashboardFragment?.refreshTasks()
                                        } catch (e: Exception) {
                                            Log.e("AIChatFragment", "Fehler beim Aktualisieren des Dashboards", e)
                                        }

                                        return@launch
                                    } catch (e: Exception) {
                                        Log.e("AIChatFragment", "Fehler beim Speichern des Tasks", e)
                                        throw e
                                    }
                                } else {
                                    Log.d("AIChatFragment", "Ungültiger Titel: '$title'")
                                    val errorMsg = ChatMessage(
                                        "Bitte geben Sie einen gültigen Titel für die Aufgabe an.\nBeispiel: 'Aufgabe Präsentation erstellen' oder 'Task Meeting vorbereiten'",
                                        false
                                    )
                                    adapter.addMessage(errorMsg)
                                    chatManager.addMessage(errorMsg)
                                    scrollToBottom()
                                    return@launch
                                }
                            } catch (e: Exception) {
                                Log.e("AIChatFragment", "Fehler bei der Aufgabenerstellung", e)
                                val errorMsg = ChatMessage(
                                    "Die Aufgabe konnte nicht erstellt werden. Bitte versuchen Sie es erneut.",
                                    false
                                )
                                adapter.addMessage(errorMsg)
                                chatManager.addMessage(errorMsg)
                                scrollToBottom()
                                return@launch
                            }
                        }

                        // Timer-Befehle
                        command.contains("timer") || command.contains("fokus") -> {
                            val minutes = extractTimerMinutes(message) ?: 25
                            Log.d("AIChatFragment", "Timer-Befehl erkannt: $minutes Minuten")
                            handleChatAction(ChatAction.SetTimer(minutes))
                        }

                        // Chat löschen
                        command.contains("lösche") && command.contains("chat") ||
                        command.contains("chat") && command.contains("löschen") ||
                        command.contains("clear") && command.contains("chat") -> {
                            Log.d("AIChatFragment", "Chat-Lösch-Befehl erkannt")
                            handleChatAction(ChatAction.ClearHistory)
                        }

                        // Kalender öffnen
                        command.contains("öffne") && command.contains("kalender") -> {
                            val title = message.substringAfter("termin", "").trim()
                                .takeIf { it.isNotEmpty() }
                            Log.d("AIChatFragment", "Kalender-Befehl erkannt: $title")
                            handleChatAction(ChatAction.OpenCalendar(eventTitle = title))
                        }

                        // Spotify/Musik
                        command.contains("spotify") || command.contains("musik") -> {
                            val playlist = message.substringAfter("playlist", "").trim()
                                .takeIf { it.isNotEmpty() }
                            Log.d("AIChatFragment", "Musik-Befehl erkannt: $playlist")
                            handleChatAction(ChatAction.PlaySpotify(playlistName = playlist))
                        }

                        // Timer-Start Bestätigung
                        command == "ja" || command.contains("starte") -> {
                            val lastTimerMessage = chatManager.getMessages()
                                .takeLast(5)
                                .find { it.content.contains("Timer für", ignoreCase = true) }

                            if (lastTimerMessage != null) {
                                val minutes = extractTimerMinutes(lastTimerMessage.content) ?: 25
                                Log.d("AIChatFragment", "Timer-Start bestätigt: $minutes Minuten")
                                handleChatAction(ChatAction.SetTimer(minutes))
                            }
                        }

                        // Spracheingabe
                        command.contains("sprich") || command.contains("stimme") -> {
                            Log.d("AIChatFragment", "Spracheingabe-Befehl erkannt")
                            handleChatAction(ChatAction.StartVoiceInput)
                        }
                    }

                    // AI-Antwort nur generieren wenn kein Befehl ausgeführt wurde
                    val response = taskAIService.generateResponse(message)
                    adapter.addMessage(response)
                    chatManager.addMessage(response)
                    scrollToBottom()

                    // Sprich die KI-Antwort aus
                    speakResponse(response.text)

                } catch (e: Exception) {
                    Log.e("AIChatFragment", "Fehler in der Nachrichtenverarbeitung", e)
                    val errorMessage = ChatMessage(
                        "Entschuldigung, es gab einen Fehler bei der Verarbeitung Ihrer Nachricht.",
                        false
                    )
                    adapter.addMessage(errorMessage)
                    chatManager.addMessage(errorMessage)
                    scrollToBottom()
                }
            }
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler beim Senden der Nachricht", e)
        }
    }

    private fun extractTimerMinutes(text: String): Int? {
        val patterns = listOf(
            "(\\d+)\\s*(?:minute[n]?|min)",  // z.B. "25 minuten" oder "25 min"
            "timer\\s*(?:für)?\\s*(\\d+)",   // z.B. "timer 25" oder "timer für 25"
            "fokus\\s*(?:für)?\\s*(\\d+)"    // z.B. "fokus 25" oder "fokus für 25"
        )

        for (pattern in patterns) {
            val match = Regex(pattern, RegexOption.IGNORE_CASE).find(text)
            match?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        }

        return null
    }

    private suspend fun speakResponse(text: String) {
        try {
            Log.d("AIChatFragment", "Starte Sprachausgabe: $text")
            withContext(Dispatchers.IO) {
                speechManager.speak(text)
            }
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler bei der Sprachausgabe", e)
        }
    }

    private fun stopSpeaking() {
        try {
            speechManager.stopSpeaking()
            speechManager.stopRecording()
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler beim Stoppen der Sprachausgabe", e)
        }
    }

    private fun handleChatAction(action: ChatAction) {
        Log.d("AIChatFragment", "Verarbeite ChatAction: $action")

        when (action) {
            is ChatAction.CreateTask -> {
                CoroutineScope(Dispatchers.Main).launch {
                    try {
                        if (action.title.isNotEmpty() && !action.title.contains("\"") && !action.title.contains("wurde erstellt")) {
                            Log.d("AIChatFragment", "Erstelle neue Aufgabe: ${action.title}")
                            val task = com.deepcore.kiytoapp.data.Task(
                                title = action.title,
                                description = action.description ?: "",
                                priority = com.deepcore.kiytoapp.data.Priority.MEDIUM
                            )
                            taskManager.createTask(task)
                            Log.d("AIChatFragment", "Aufgabe erfolgreich erstellt")
                        }
                    } catch (e: Exception) {
                        Log.e("AIChatFragment", "Fehler beim Erstellen der Aufgabe", e)
                        val errorMsg = ChatMessage(
                            "Die Aufgabe konnte nicht erstellt werden. Bitte versuchen Sie es erneut.",
                            false
                        )
                        adapter.addMessage(errorMsg)
                        chatManager.addMessage(errorMsg)
                    }
                }
            }

            is ChatAction.SetTimer -> {
                try {
                    Log.d("AIChatFragment", "Starte Timer für ${action.minutes} Minuten")
                    val mainActivity = activity as? MainActivity
                    if (mainActivity == null) {
                        Log.e("AIChatFragment", "MainActivity nicht gefunden")
                        throw Exception("MainActivity nicht gefunden")
                    }

                    // Navigiere zum FocusModeFragment
                    mainActivity.findViewById<BottomNavigationView>(R.id.bottomNavigation)?.selectedItemId = R.id.nav_focus

                    // Warte kurz, bis das Fragment gewechselt hat
                    view?.postDelayed({
                        val focusFragment = mainActivity.supportFragmentManager.findFragmentById(R.id.fragmentContainer) as? FocusModeFragment
                        if (focusFragment != null) {
                            // Timer direkt starten
                            val viewModel = focusFragment.getViewModelInstance()
                            viewModel.updatePomodoroLength(action.minutes.toLong())
                            viewModel.setMode(PomodoroViewModel.PomodoroMode.POMODORO)
                            viewModel.startTimer() // Timer wird sofort gestartet
                            Log.d("AIChatFragment", "Timer erfolgreich gestartet")
                        } else {
                            Log.e("AIChatFragment", "FocusModeFragment nicht gefunden")
                            // Statt Exception werfen, eine Fehlermeldung anzeigen
                            val errorMsg = ChatMessage(
                                "Der Timer konnte nicht gestartet werden. Bitte versuchen Sie es über den Fokus-Modus.",
                                false
                            )
                            adapter.addMessage(errorMsg)
                            chatManager.addMessage(errorMsg)
                        }
                    }, 500)
                } catch (e: Exception) {
                    Log.e("AIChatFragment", "Fehler beim Starten des Timers", e)
                    val errorMsg = ChatMessage(
                        "Der Timer konnte nicht gestartet werden. Bitte versuchen Sie es über den Fokus-Modus.",
                        false
                    )
                    adapter.addMessage(errorMsg)
                    chatManager.addMessage(errorMsg)
                }
            }

            is ChatAction.OpenCalendar -> {
                try {
                    val intent = Intent(Intent.ACTION_INSERT)
                        .setData(android.provider.CalendarContract.Events.CONTENT_URI)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                    action.eventTitle?.let { title ->
                        intent.putExtra(android.provider.CalendarContract.Events.TITLE, title)
                    }

                    // Sicherheitscheck ob Intent ausgeführt werden kann
                    if (intent.resolveActivity(requireContext().packageManager) != null) {
                        startActivity(intent)
                        // Keine Erfolgsmeldung mehr senden
                    } else {
                        val errorMsg = ChatMessage("Entschuldigung, es wurde keine Kalender-App gefunden.", false)
                        adapter.addMessage(errorMsg)
                        chatManager.addMessage(errorMsg)
                    }
                } catch (e: Exception) {
                    Log.e("AIChatFragment", "Fehler beim Vorbereiten des Kalender-Intents", e)
                    val errorMsg = ChatMessage("Der Kalender konnte leider nicht geöffnet werden.", false)
                    adapter.addMessage(errorMsg)
                    chatManager.addMessage(errorMsg)
                }
            }

            is ChatAction.PlaySpotify -> {
                try {
                    val spotifyPackage = "com.spotify.music"
                    val spotifyIntent = requireContext().packageManager
                        .getLaunchIntentForPackage(spotifyPackage)
                        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        ?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)

                    if (spotifyIntent != null) {
                        startActivity(spotifyIntent)
                        // Keine Erfolgsmeldung mehr senden
                    } else {
                        // Play Store Intent mit Sicherheitscheck
                        val playStoreIntent = Intent(Intent.ACTION_VIEW)
                            .setData(Uri.parse("market://details?id=$spotifyPackage"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                        if (playStoreIntent.resolveActivity(requireContext().packageManager) != null) {
                            startActivity(playStoreIntent)
                            val msg = ChatMessage("Spotify ist nicht installiert. Der Play Store wurde geöffnet.", false)
                            adapter.addMessage(msg)
                            chatManager.addMessage(msg)
                        } else {
                            val errorMsg = ChatMessage("Entschuldigung, weder Spotify noch der Play Store konnten geöffnet werden.", false)
                            adapter.addMessage(errorMsg)
                            chatManager.addMessage(errorMsg)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AIChatFragment", "Fehler beim Vorbereiten des Spotify-Intents", e)
                    val errorMsg = ChatMessage("Spotify konnte leider nicht geöffnet werden.", false)
                    adapter.addMessage(errorMsg)
                    chatManager.addMessage(errorMsg)
                }
            }

            is ChatAction.StartVoiceInput -> {
                try {
                    startVoiceRecognition()
                } catch (e: Exception) {
                    Log.e("AIChatFragment", "Fehler beim Starten der Spracherkennung", e)
                }
            }

            is ChatAction.ClearHistory -> {
                try {
                    MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Chat-Verlauf löschen")
                        .setMessage("Möchten Sie wirklich den gesamten Chat-Verlauf löschen?")
                        .setPositiveButton("Ja") { _, _ ->
                            adapter.clearMessages()
                            chatManager.clearHistory()
                            showWelcomeMessage()
                        }
                        .setNegativeButton("Nein", null)
                        .show()
                } catch (e: Exception) {
                    Log.e("AIChatFragment", "Fehler beim Löschen des Chat-Verlaufs", e)
                }
            }

            else -> {
                Log.e("AIChatFragment", "Unbekannte ChatAction: $action")
            }
        }
    }

    private fun showClearHistoryDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.clear_history)
            .setMessage(R.string.clear_history_confirmation)
            .setPositiveButton(R.string.clear) { _, _ ->
                try {
                    adapter.clearMessages()
                    chatManager.clearHistory()
                    showWelcomeMessage()
                } catch (e: Exception) {
                    Log.e("AIChatFragment", "Error clearing history", e)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun archiveCurrentChat() {
        if (adapter.itemCount <= 1) { // Nur Willkommensnachricht
            return
        }

        try {
            chatManager.archiveChat()
            adapter.clearMessages()
            showWelcomeMessage()
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Error archiving chat", e)
        }
    }

    private fun showArchivedChats() {
        val archivedChats = chatManager.getArchivedChats()
        if (archivedChats.isEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.archived_chats)
                .setMessage(R.string.no_archived_chats)
                .setPositiveButton(R.string.ok, null)
                .show()
            return
        }

        val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        val items = archivedChats.map { chat ->
            "${dateFormat.format(Date(chat.timestamp))} (${chat.messages.size} Nachrichten)"
        }.toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.archived_chats)
            .setItems(items) { _, position ->
                try {
                    val selectedChat = archivedChats[position]
                    showArchivedChatDetails(selectedChat)
                } catch (e: Exception) {
                    Log.e("AIChatFragment", "Error showing archived chat", e)
                }
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showArchivedChatDetails(archivedChat: ArchivedChat) {
        // Zeige den archivierten Chat in einem neuen Fragment an
        val fragment = ArchivedChatFragment.newInstance(archivedChat)
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroy() {
        super.onDestroy()
        speechManager.shutdown()
        
        // ToneGenerator-Ressourcen freigeben
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler beim Freigeben der ToneGenerator-Ressourcen", e)
        }
    }

    private fun extractTaskTitle(text: String): String {
        val input = text.trim().lowercase()
        
        // Entferne Befehlswörter am Anfang
        val cleanedInput = input
            .replace(Regex("^(erstelle|neue|eine|aufgabe|task|todo|to-do|hinzufügen|bitte|notiere|notieren|merke|merken|füge|schreibe)\\s+"), "")
            .trim()
            .replaceFirstChar { it.uppercase() }
        
        // Wenn der bereinigte Input leer ist oder nur aus Befehlswörtern besteht
        if (cleanedInput.isBlank() ||
            cleanedInput.lowercase() in listOf("aufgabe", "task", "todo", "to-do")) {
            return ""
        }
        
        return cleanedInput
    }

    private fun handleTaskCreation(title: String) {
        lifecycleScope.launch {
            try {
                if (title.isNotEmpty()) {
                    Log.d("AIChatFragment", "Erstelle neue Aufgabe: $title")
                    val task = com.deepcore.kiytoapp.data.Task(
                        title = title,
                        description = "",
                        priority = com.deepcore.kiytoapp.data.Priority.MEDIUM
                    )
                    taskManager.createTask(task)

                    val confirmationMessage = ChatMessage(
                        "✓ Neue Aufgabe erstellt: \"$title\"",
                        false
                    )
                    adapter.addMessage(confirmationMessage)
                    chatManager.addMessage(confirmationMessage)
                    scrollToBottom()

                    // Dashboard aktualisieren
                    (parentFragmentManager.findFragmentByTag("dashboard") as? DashboardFragment)?.refreshTasks()
                }
            } catch (e: Exception) {
                Log.e("AIChatFragment", "Fehler bei der Aufgabenerstellung", e)
                val errorMsg = ChatMessage(
                    "Die Aufgabe konnte nicht erstellt werden. Bitte versuchen Sie es erneut.",
                    false
                )
                adapter.addMessage(errorMsg)
                chatManager.addMessage(errorMsg)
            }
        }
    }

    override fun onApiKeySet(apiKey: String) {
        // Initialisiere OpenAIService neu mit dem aktualisierten API-Key
        taskAIService = TaskAIService(requireContext())
        val message = ChatMessage(
            "API-Einstellungen wurden aktualisiert. Sie können den Chat jetzt fortsetzen.",
            false
        )
        adapter.addMessage(message)
        chatManager.addMessage(message)
        scrollToBottom()
    }

    // Implementierung der abstrakten Methode aus dem Interface APISettingsDialog.OnApiKeySetListener
    override fun showPermissionDeniedDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Berechtigung verweigert")
            .setMessage("Für diese Funktion werden Berechtigungen benötigt. Bitte aktivieren Sie diese in den App-Einstellungen.")
            .setPositiveButton("App-Einstellungen") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showMicPermissionDeniedDialog() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("Mikrofonberechtigung verweigert")
            .setMessage("Die Spracherkennung kann ohne Mikrofonberechtigung nicht verwendet werden. Sie können die Berechtigung in den App-Einstellungen aktivieren.")
            .setPositiveButton("App-Einstellungen") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun showApiSettingsDialog() {
        val dialog = APISettingsDialog()
        dialog.show(parentFragmentManager, "api_settings")
    }

    private fun showMessageOptions(message: ChatMessage, position: Int) {
        val options = arrayOf(
            if (message.isPinned) getString(R.string.unpin_message) else getString(R.string.pin_message),
            getString(R.string.delete_message)
        )

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.message_options))
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        adapter.toggleMessagePin(position)
                        val snackbarMessage = if (message.isPinned) {
                            getString(R.string.message_pinned)
                        } else {
                            getString(R.string.message_unpinned)
                        }
                        com.google.android.material.snackbar.Snackbar.make(
                            requireView(),
                            snackbarMessage,
                            com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
                        )
                        .setAnchorView(requireActivity().findViewById(R.id.bottomNavigation))
                        .setBackgroundTint(resources.getColor(R.color.snackbar_background, null))
                        .setTextColor(resources.getColor(R.color.white, null))
                        .show()
                    }
                    1 -> {
                        adapter.removeMessage(position)
                        chatManager.removeMessage(position)
                        com.google.android.material.snackbar.Snackbar.make(
                            requireView(),
                            getString(R.string.message_deleted),
                            com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        )
                        .setAction(getString(R.string.undo)) {
                            adapter.addMessageAt(position, message)
                            chatManager.addMessageAt(position, message)
                        }
                        .setAnchorView(requireActivity().findViewById(R.id.bottomNavigation))
                        .setBackgroundTint(resources.getColor(R.color.snackbar_background, null))
                        .setActionTextColor(resources.getColor(R.color.snackbar_action, null))
                        .setTextColor(resources.getColor(R.color.white, null))
                        .show()
                    }
                }
            }
            .show()
    }

    private fun setupPinnedMessage() {
        pinnedMessageContainer.findViewById<ImageButton>(R.id.unpinButton).setOnClickListener {
            adapter.getPinnedMessage()?.let { message ->
                val position = adapter.currentList.indexOf(message)
                if (position != -1) {
                    adapter.toggleMessagePin(position)
                }
            }
            pinnedMessageContainer.visibility = View.GONE
        }
    }

    private fun updatePinnedMessage(message: ChatMessage?) {
        if (message != null && message.isPinned) {
            pinnedMessageContainer.visibility = View.VISIBLE
            pinnedMessageContainer.findViewById<TextView>(R.id.pinnedMessageText).text = message.content
        } else {
            pinnedMessageContainer.visibility = View.GONE
        }
    }

    private fun setupPlusMenu() {
        plusButton.setOnClickListener {
            togglePlusMenu()
        }

        // Klick außerhalb des Menüs schließt es
        view?.setOnClickListener {
            if (plusMenuContainer.visibility == View.VISIBLE) {
                plusMenuContainer.visibility = View.GONE
            }
        }

        // Optionen-Handler
        photosOption.setOnClickListener {
            openPhotoPicker()
            plusMenuContainer.visibility = View.GONE
        }

        cameraOption.setOnClickListener {
            openCamera()
            plusMenuContainer.visibility = View.GONE
        }

        filesOption.setOnClickListener {
            openFilePicker()
            plusMenuContainer.visibility = View.GONE
        }

        createImageOption.setOnClickListener {
            showImageGenerationDialog()
            plusMenuContainer.visibility = View.GONE
        }

        collectIdeasOption.setOnClickListener {
            startActivity(Intent(requireContext(), MindMapActivity::class.java))
            plusMenuContainer.visibility = View.GONE
        }

        analyzeImagesOption.setOnClickListener {
            openPhotoPicker()
            plusMenuContainer.visibility = View.GONE
        }

        moreOption.setOnClickListener {
            // TODO: Implementiere weitere Optionen
            showSnackbar("Weitere Optionen werden bald verfügbar sein")
            plusMenuContainer.visibility = View.GONE
        }
    }

    private fun togglePlusMenu() {
        if (plusMenuContainer.visibility == View.VISIBLE) {
            plusMenuContainer.visibility = View.GONE
        } else {
            plusMenuContainer.visibility = View.VISIBLE
        }
    }

    private fun openPhotoPicker() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        pickImageLauncher.launch(intent)
    }

    private fun openCamera() {
        // Prüfe zuerst, ob ein API-Key vorhanden ist
        val apiSettingsManager = APISettingsManager(requireContext())
        if (apiSettingsManager.getApiKey().isNullOrEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("API-Key erforderlich")
                .setMessage("Für die Bildanalyse wird ein OpenAI API-Key benötigt. Möchten Sie jetzt einen API-Key hinzufügen?")
                .setPositiveButton("Ja") { _, _ ->
                    showApiSettingsDialog()
                }
                .setNegativeButton("Nein", null)
                .show()
            return
        }

        // Prüfe Kamera-Berechtigung
        if (requireContext().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            showSnackbar("Kamera-Berechtigung fehlt. Bitte in den App-Einstellungen aktivieren.")
            return
        }

        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val photoFile = File.createTempFile(imageFileName, ".jpg", storageDir)
            
            currentPhotoUri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                photoFile
            )

            takePictureLauncher.launch(currentPhotoUri)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Öffnen der Kamera", e)
            showSnackbar("Fehler beim Öffnen der Kamera")
        }
    }

    private fun openFilePicker() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/pdf",
                    "text/plain",
                    "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                ))
            }
            pickFileLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Öffnen des Datei-Pickers", e)
            showSnackbar("Fehler beim Öffnen des Datei-Pickers")
        }
    }

    private fun processImage(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val file = copyUriToFile(uri)
                if (file != null) {
                    val response = taskAIService.analyzeImage(file)
                    withContext(Dispatchers.Main) {
                        addMessageToChat(ChatMessage(content = response, isUser = false))
                    }
                    file.delete() // Aufräumen nach der Analyse
                } else {
                    withContext(Dispatchers.Main) {
                        showSnackbar("Fehler beim Laden des Bildes")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei der Bildverarbeitung", e)
                withContext(Dispatchers.Main) {
                    showSnackbar("Fehler bei der Bildverarbeitung")
                }
            }
        }
    }

    private fun processFile(uri: Uri) {
        lifecycleScope.launch {
            try {
                showLoadingDialog("Datei wird verarbeitet...")
                
                val fileName = getFileName(uri) ?: throw Exception("Dateiname konnte nicht ermittelt werden")
                
                if (fileName.endsWith(".pdf", ignoreCase = true)) {
                    // PDF verarbeiten
                    val pdfContent = StringBuilder()
                    
                    try {
                        // Kopiere die PDF-Datei in eine temporäre Datei
                        val pdfFile = copyUriToFile(uri) ?: throw Exception("Konnte PDF-Datei nicht kopieren")
                        
                        // Extrahiere Text aus der PDF
                        val text = extractTextFromPdf(pdfFile)
                        pdfContent.append(text)
                        
                        if (pdfContent.isEmpty()) {
                            throw Exception("Keine Textinhalte in der PDF gefunden")
                        }
                        
                        // PDF-Inhalt an OpenAI senden
                        val response = taskAIService.analyzeFile(fileName, pdfContent.toString())
                        hideLoadingDialog()
                        
                        addMessageToChat(ChatMessage(content = response, isUser = false))
                        
                        // Temporäre Datei löschen
                        pdfFile.delete()
                        
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler bei der PDF-Verarbeitung", e)
                        hideLoadingDialog()
                        showSnackbar("Fehler bei der PDF-Verarbeitung: ${e.message}")
                    }
                } else {
                    // Andere Dateitypen verarbeiten
                    val content = readFileContent(uri)
                    if (content != null) {
                        val response = taskAIService.analyzeFile(fileName, content)
                        hideLoadingDialog()
                        
                        addMessageToChat(ChatMessage(content = response, isUser = false))
                    } else {
                        hideLoadingDialog()
                        showSnackbar("Fehler beim Lesen der Datei")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei der Dateiverarbeitung", e)
                hideLoadingDialog()
                showSnackbar("Fehler bei der Dateiverarbeitung: ${e.message}")
            }
        }
    }

    private fun saveBitmapToFile(bitmap: Bitmap): File? {
        return try {
            val file = File(requireContext().cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Speichern des Bildes", e)
            null
        }
    }

    private fun copyUriToFile(uri: Uri): File? {
        return try {
            val file = File(requireContext().cacheDir, "temp_file_${System.currentTimeMillis()}")
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(file).use { output ->
                    input.copyTo(output)
                }
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Kopieren der Datei", e)
            null
        }
    }

    private fun readFileContent(uri: Uri): String? {
        return try {
            requireContext().contentResolver.openInputStream(uri)?.use { input ->
                input.bufferedReader().use { it.readText() }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Lesen der Datei", e)
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        return try {
            requireContext().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Lesen des Dateinamens", e)
            null
        }
    }

    private fun showSnackbar(message: String) {
        Snackbar.make(
            requireView(),
            message,
            Snackbar.LENGTH_LONG
        )
        .setAnchorView(requireActivity().findViewById(R.id.bottomNavigation))
        .setBackgroundTint(resources.getColor(R.color.snackbar_background, null))
        .setTextColor(resources.getColor(R.color.white, null))
        .show()
    }

    private fun addMessageToChat(message: ChatMessage) {
        lifecycleScope.launch(Dispatchers.Main) {
            adapter.addMessage(message)
            chatManager.addMessage(message)
            scrollToBottom()
        }
    }

    private fun showWelcomeMessage() {
        val welcomeMessage = ChatMessage(
            content = getString(R.string.welcome_message),
            isUser = false
        )
        addMessageToChat(welcomeMessage)
        
        // Sprachausgabe für die Begrüßungsnachricht aktivieren
        lifecycleScope.launch {
            try {
                speechManager.speak(welcomeMessage.content)
            } catch (e: Exception) {
                Log.e("AIChatFragment", "Fehler bei der Sprachausgabe der Begrüßungsnachricht", e)
            }
        }
    }

    private fun showImageGenerationDialog() {
        // Prüfe zuerst, ob ein API-Key vorhanden ist
        val apiSettingsManager = APISettingsManager(requireContext())
        if (apiSettingsManager.getApiKey().isNullOrEmpty()) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("API-Key erforderlich")
                .setMessage("Für die Bildgenerierung wird ein OpenAI API-Key benötigt. Möchten Sie jetzt einen API-Key hinzufügen?")
                .setPositiveButton("Ja") { _, _ ->
                    showApiSettingsDialog()
                }
                .setNegativeButton("Nein", null)
                .show()
            return
        }

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_image_generation, null)
        
        val promptInput = dialogView.findViewById<EditText>(R.id.promptInput)
        val sizeSpinner = dialogView.findViewById<Spinner>(R.id.sizeSpinner)
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Bild generieren")
            .setView(dialogView)
            .setPositiveButton("Generieren") { _, _ ->
                val prompt = promptInput.text.toString()
                val size = sizeSpinner.selectedItem.toString()
                if (prompt.isNotEmpty()) {
                    generateImage(prompt, size)
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun generateImage(prompt: String, size: String) {
        lifecycleScope.launch {
            try {
                showLoadingDialog("Bild wird generiert...")
                
                val generatedImage = withContext(Dispatchers.IO) {
                    imageGenerationService.generateImage(prompt, size)
                }

                hideLoadingDialog()

                if (generatedImage != null) {
                    // Füge die Nachricht zum Chat hinzu
                    val imageUri = Uri.fromFile(generatedImage).toString()
                    Log.d(TAG, "Generiertes Bild URI: $imageUri")
                    val message = ChatMessage(
                        content = "Generiertes Bild für: \"$prompt\"",
                        isUser = false,
                        imageUri = imageUri
                    )
                    addMessageToChat(message)
                } else {
                    showSnackbar("Fehler bei der Bildgenerierung")
                }
            } catch (e: Exception) {
                hideLoadingDialog()
                Log.e(TAG, "Fehler bei der Bildgenerierung", e)
                showSnackbar("Fehler bei der Bildgenerierung")
            }
        }
    }

    private fun showLoadingDialog(message: String) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_loading, null)
        
        dialogView.findViewById<TextView>(R.id.loadingMessage).text = message
        
        loadingDialog = MaterialAlertDialogBuilder(requireContext())
            .setView(dialogView)
            .setCancelable(false)
            .create()
        
        loadingDialog.show()
    }
    
    private fun hideLoadingDialog() {
        if (::loadingDialog.isInitialized && loadingDialog.isShowing) {
            loadingDialog.dismiss()
        }
    }

    private fun cleanImageCache() {
        try {
            val cacheDir = requireContext().cacheDir
            val currentTime = System.currentTimeMillis()
            val maxAge = 24 * 60 * 60 * 1000 // 24 Stunden in Millisekunden

            cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("generated_image_") && 
                    currentTime - file.lastModified() > maxAge) {
                    if (file.delete()) {
                        Log.d("AIChatFragment", "Cache-Datei gelöscht: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler bei der Cache-Bereinigung", e)
        }
    }

    private fun checkAndRequestPermissions() {
        val prefs = requireContext().getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val permissionsRequested = prefs.getBoolean("permissions_requested", false)
        
        // Wenn Berechtigungen bereits angefragt wurden, nicht erneut anfragen
        if (permissionsRequested) {
            Log.d("AIChatFragment", "Berechtigungen wurden bereits angefragt")
            return
        }
        
        val permissions = arrayOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.POST_NOTIFICATIONS,
            android.Manifest.permission.READ_EXTERNAL_STORAGE,
            android.Manifest.permission.READ_CALENDAR,
            android.Manifest.permission.WRITE_CALENDAR,
            android.Manifest.permission.READ_MEDIA_AUDIO,
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_MEDIA_VIDEO,
            android.Manifest.permission.CAMERA
        )

        val permissionsToRequest = mutableListOf<String>()
        
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d("AIChatFragment", "Fordere Berechtigungen an: ${permissionsToRequest.joinToString()}")
            requestPermissions(
                permissionsToRequest.toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
            
            // Markiere Berechtigungen als angefragt
            prefs.edit().putBoolean("permissions_requested", true).apply()
            Log.d("AIChatFragment", "Berechtigungen als angefragt markiert")
        }
    }

    private fun showPermissionExplanationDialog(deniedPermissions: List<String>) {
        val message = buildString {
            append("Für die volle Funktionalität der App werden folgende Berechtigungen benötigt:\n\n")
            for (permission in deniedPermissions) {
                append("• ${getPermissionExplanation(permission)}\n")
            }
            append("\nBitte aktivieren Sie diese in den App-Einstellungen.")
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Berechtigungen erforderlich")
            .setMessage(message)
            .setPositiveButton("Zu den Einstellungen") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Später") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun openAppSettings() {
        val intent = Intent(
            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", requireContext().packageName, null)
        )
        startActivity(intent)
    }

    private fun getPermissionExplanation(permission: String): String {
        return when (permission) {
            android.Manifest.permission.RECORD_AUDIO -> "Mikrofon für Spracheingabe"
            android.Manifest.permission.POST_NOTIFICATIONS -> "Benachrichtigungen für wichtige Updates"
            android.Manifest.permission.READ_EXTERNAL_STORAGE -> "Zugriff auf Fotos und Videos"
            android.Manifest.permission.READ_CALENDAR -> "Kalenderzugriff für Terminverwaltung"
            android.Manifest.permission.WRITE_CALENDAR -> "Kalenderzugriff für Terminverwaltung"
            android.Manifest.permission.READ_MEDIA_AUDIO -> "Zugriff auf Musik und Audio"
            android.Manifest.permission.READ_MEDIA_IMAGES -> "Zugriff auf Fotos"
            android.Manifest.permission.READ_MEDIA_VIDEO -> "Zugriff auf Videos"
            android.Manifest.permission.CAMERA -> "Kamera für Aufnahmen"
            else -> permission
        }
    }

    private fun showErrorDialog(messageResId: Int) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.error)
            .setMessage(messageResId)
            .setPositiveButton(R.string.ok) { dialog: DialogInterface, _: Int ->
                dialog.dismiss()
            }
            .show()
    }

    private suspend fun extractTextFromPdf(pdfFile: File): String {
        return withContext(Dispatchers.IO) {
            try {
                // Verwende ML Kit Text Recognition für die PDF
                val recognizer = com.google.mlkit.vision.text.TextRecognition.getClient(
                    com.google.mlkit.vision.text.latin.TextRecognizerOptions.DEFAULT_OPTIONS
                )
                
                val text = StringBuilder()
                val inputStream = pdfFile.inputStream()
                val pdfRenderer = android.graphics.pdf.PdfRenderer(
                    android.os.ParcelFileDescriptor.open(
                        pdfFile,
                        android.os.ParcelFileDescriptor.MODE_READ_ONLY
                    )
                )
                
                try {
                    // Verarbeite jede Seite
                    for (pageIndex in 0 until pdfRenderer.pageCount) {
                        val page = pdfRenderer.openPage(pageIndex)
                        val bitmap = android.graphics.Bitmap.createBitmap(
                            page.width,
                            page.height,
                            android.graphics.Bitmap.Config.ARGB_8888
                        )
                        
                        page.render(
                            bitmap,
                            null,
                            null,
                            android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                        )
                        
                        // Erkenne Text auf der Seite
                        val image = com.google.mlkit.vision.common.InputImage.fromBitmap(bitmap, 0)
                        val result = suspendCoroutine<com.google.mlkit.vision.text.Text> { continuation ->
                            recognizer.process(image)
                                .addOnSuccessListener { continuation.resume(it) }
                                .addOnFailureListener { continuation.resumeWithException(it) }
                        }
                        
                        text.append(result.text).append("\n")
                        
                        page.close()
                        bitmap.recycle()
                    }
                    
                    text.toString()
                } finally {
                    pdfRenderer.close()
                    inputStream.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Extrahieren des Texts aus der PDF", e)
                throw e
            }
        }
    }

    private suspend fun <TResult> com.google.android.gms.tasks.Task<TResult>.await(): TResult =
        suspendCoroutine { continuation ->
            addOnSuccessListener { result ->
                continuation.resume(result)
            }.addOnFailureListener { exception ->
                continuation.resumeWithException(exception)
            }
        }

    companion object {
        private const val TAG = "AIChatFragment"
        private const val PERMISSION_REQUEST_CODE = 100
        private const val PERMISSIONS_REQUEST_CODE = 100
        private var currentPhotoUri: Uri? = null
    }
} 
