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
import android.media.session.MediaController
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
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
import com.deepcore.kiytoapp.ai.APISettingsDialog
import com.deepcore.kiytoapp.ai.APISettingsManager
import com.deepcore.kiytoapp.ai.ArchivedChat
import com.deepcore.kiytoapp.ai.ArchivedChatFragment
import com.deepcore.kiytoapp.ai.ChatAction
import com.deepcore.kiytoapp.ai.ChatAdapter
import com.deepcore.kiytoapp.ai.ChatManager
import com.deepcore.kiytoapp.ai.ChatMessage
import com.deepcore.kiytoapp.ai.ImageGenerationService
import com.deepcore.kiytoapp.ai.OpenAIService
import com.deepcore.kiytoapp.ai.SpeechManager
import com.deepcore.kiytoapp.ai.TaskAIService
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import com.deepcore.kiytoapp.ai.ActionItem
import android.widget.Toast

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
    private var isPlayingWelcomeMessage = false
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

    private val RECORDING_DURATION_MS = 2000 // auf 2000 ms reduzieren

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
                
                // Prüfe, ob die Spracheingabe aktiviert werden soll
                arguments?.let { args ->
                    if (args.getBoolean("activate_voice", false)) {
                        Log.d("AIChatFragment", "Wake Word aktiviert Spracheingabe automatisch")
                        // Füge begrüßungsnachricht hinzu
                        val welcomeMessage = ChatMessage("Ich höre dir zu. Was kann ich für dich tun?", false)
                        adapter.addMessage(welcomeMessage)
                        chatManager.addMessage(welcomeMessage)
                        scrollToBottom()
                        
                        // Kurze Verzögerung, damit die UI-Updates abgeschlossen sind
                        Handler(Looper.getMainLooper()).postDelayed({
                            // Starte die Sprachausgabe in einem Coroutine-Kontext
                            if (isAdded && activity != null && !isPlayingWelcomeMessage) { // Überprüfung, ob Fragment noch angehängt ist und Sprachausgabe nicht bereits läuft
                                isPlayingWelcomeMessage = true // Markiere, dass wir die Willkommensnachricht abspielen
                                CoroutineScope(Dispatchers.Main).launch {
                                    try {
                                        speakResponse("Ich höre dir zu. Was kann ich für dich tun?") 
                                        
                                        // Nach der Spracheingabe automatisch die Audioeingabe starten
                                        delay(2000) // 2 Sekunden warten bis nach der Sprachausgabe
                                        if (isAdded && activity != null) { // Nochmals prüfen vor der Spracherkennung
                                            startVoiceRecognition()
                                        } else {
                                            Log.e("AIChatFragment", "Fragment nicht mehr an Activity angehängt - Spracherkennung abgebrochen")
                                        }
                                        isPlayingWelcomeMessage = false // Zurücksetzen nach Abschluss
                                    } catch (e: Exception) {
                                        isPlayingWelcomeMessage = false // Zurücksetzen im Fehlerfall
                                        Log.e("AIChatFragment", "Fehler bei automatischer Sprachaktivierung", e)
                                    }
                                }
                            } else {
                                Log.d("AIChatFragment", "Fragment nicht angehängt oder Willkommensnachricht bereits aktiv")
                            }
                        }, 500)
                    }
                }
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
                        
                        Log.d("AIChatFragment", "Sprachbefehl erkannt: \"$command\"")
                        
                        // Timer-Befehle - Direkte Ausführung - VERBESSERT
                        if (command.contains("timer") || command.contains("fokus") || 
                            (command.contains("setze") && command.contains("minute")) ||
                            (command.contains("starte") && command.contains("minute")) ||
                            (command.contains("stelle") && command.contains("minute"))) {
                            
                            val minutes = extractTimerMinutes(spokenText)
                            if (minutes != null) {
                                Log.d("AIChatFragment", "Timer-Befehl erkannt: $minutes Minuten")
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
                        // Kalender öffnen - Direkte Ausführung - VERBESSERT
                        else if ((command.contains("öffne") && command.contains("kalender")) ||
                                (command.contains("kalender") && command.contains("öffnen")) ||
                                (command.contains("zeige") && command.contains("kalender")) ||
                                (command.contains("kalender") && command.contains("anzeigen"))) {
                            Log.d("AIChatFragment", "Kalender-Befehl erkannt")
                            handleChatAction(ChatAction.OpenCalendar())
                            commandExecuted = true
                        }
                        // Spotify/Musik - Direkte Ausführung - VERBESSERT
                        else if (command.contains("spotify") || 
                                command.contains("musik") || 
                                (command.contains("spiele") && command.contains("musik")) ||
                                (command.contains("öffne") && command.contains("spotify")) ||
                                command.contains("abspielen")) {
                            Log.d("AIChatFragment", "Musik/Spotify-Befehl erkannt")
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
        // Speicherberechtigungen prüfen
        val storagePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        
        // Wenn die Berechtigung bereits geprüft wurde, nicht erneut prüfen
        if (com.deepcore.kiytoapp.utils.PermissionManager.isPermissionChecked(requireContext(), storagePermission)) {
            Log.d("AIChatFragment", "Speicherberechtigung wurde bereits geprüft")
            return
        }
        
        val requiredPermissions = mutableListOf<String>()
        
        // Prüfe Kamera-Berechtigung
        if (requireContext().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(Manifest.permission.CAMERA)
        }
        
        // Prüfe Speicher-Berechtigung
        if (requireContext().checkSelfPermission(storagePermission) != PackageManager.PERMISSION_GRANTED) {
            requiredPermissions.add(storagePermission)
        }
        
        if (requiredPermissions.isNotEmpty()) {
            requestMultiplePermissionsLauncher.launch(requiredPermissions.toTypedArray())
        }
        
        // Markiere die Speicherberechtigung als geprüft, unabhängig vom Ergebnis
        com.deepcore.kiytoapp.utils.PermissionManager.markPermissionChecked(requireContext(), storagePermission)
        Log.d("AIChatFragment", "Speicherberechtigung als geprüft markiert")
    }

    private fun setupRecyclerView() {
        recyclerView = binding.recyclerView
        adapter = ChatAdapter(
            onActionClicked = { action ->
                handleChatAction(action)
            },
            onActionItemClicked = { actionItem ->
                handleActionItemClick(actionItem)
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
                                    created = Date(),
                                    completedDate = null
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
                                        created = Date(),
                                        completedDate = null
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
            "fokus\\s*(?:für)?\\s*(\\d+)",   // z.B. "fokus 25" oder "fokus für 25"
            "(?:setze|stelle|starte)\\s*(?:einen|den)?\\s*timer\\s*(?:auf|für)?\\s*(\\d+)", // z.B. "setze timer auf 25"
            "(?:setze|stelle|starte)\\s*(?:einen|den)?\\s*(?:auf|für)?\\s*(\\d+)\\s*(?:minute[n]?|min)", // z.B. "setze auf 25 minuten"
            "(?:timer|fokus)\\s*(?:von|mit)?\\s*(\\d+)\\s*(?:minute[n]?|min)" // z.B. "timer von 25 minuten"
        )

        for (pattern in patterns) {
            val match = Regex(pattern, RegexOption.IGNORE_CASE).find(text)
            match?.groupValues?.get(1)?.toIntOrNull()?.let { 
                Log.d("AIChatFragment", "Timer-Minuten erkannt mit Pattern '$pattern': $it Minuten")
                return it 
            }
        }

        // Fallback: Suche einfach nach Zahlen, wenn keine der Patterns passt
        val numberPattern = "\\b(\\d+)\\b"
        val match = Regex(numberPattern).find(text)
        match?.groupValues?.get(1)?.toIntOrNull()?.let {
            if (it > 0 && it < 120) { // Plausibilitätsprüfung: Zwischen 1 und 120 Minuten
                Log.d("AIChatFragment", "Timer-Minuten mit Fallback erkannt: $it Minuten")
                return it
            }
        }

        return null
    }

    private suspend fun speakResponse(text: String) {
        try {
            Log.d("AIChatFragment", "Starte Sprachausgabe: $text")
            if (!isAdded || activity == null) {
                Log.e("AIChatFragment", "Fragment nicht angehängt - Sprachausgabe abgebrochen")
                return
            }
            
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
        Log.d(TAG, "ChatAction geklickt: ${action}")
        
        // Behandle Sealed-Class ChatAction Typen wie bisher
        when (action) {
            is ChatAction.CreateTask -> {
                val title = action.title
                if (title.isNotEmpty()) {
                    handleTaskCreation(title)
                }
            }
            is ChatAction.SetTimer -> {
                val minutes = action.minutes
                Log.d(TAG, "Timer starten mit $minutes Minuten")
                
                // Begrenze den Wert auf maximal 60 Minuten
                val limitedMinutes = if (minutes > 60) 60 else minutes
                
                if (limitedMinutes != minutes) {
                    Log.d(TAG, "Timer-Dauer auf 60 Minuten begrenzt (ursprünglich $minutes)")
                    // Zeige eine Benachrichtigung an, dass der Wert begrenzt wurde
                    Toast.makeText(
                        requireContext(),
                        "Timer auf maximal 60 Minuten begrenzt",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                // Navigiere zum FocusModeFragment und starte den Timer automatisch
                try {
                    val mainActivity = activity as? MainActivity
                    if (mainActivity != null) {
                        // Wechsle zum Fokus-Tab
                        val bottomNavigation = mainActivity.findViewById<BottomNavigationView>(R.id.bottomNavigation)
                        bottomNavigation.selectedItemId = R.id.nav_focus
                        
                        Log.d(TAG, "Zum Fokus-Tab gewechselt, warte auf Fragment-Initialisierung...")
                        
                        // Längere Verzögerung, um sicherzustellen, dass das Fragment vollständig geladen ist
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                // Finde das FocusModeFragment
                                val fragmentManager = mainActivity.supportFragmentManager
                                val currentFragment = fragmentManager.findFragmentById(R.id.fragmentContainer)
                                
                                Log.d(TAG, "Aktuelles Fragment: ${currentFragment?.javaClass?.simpleName}")
                                
                                val focusFragment = currentFragment as? FocusModeFragment
                                
                                // Starte den Timer automatisch
                                focusFragment?.let {
                                    Log.d(TAG, "FocusModeFragment gefunden, setze Timer-Dauer auf $limitedMinutes Minuten")
                                    
                                    // Setze die Timer-Dauer
                                    it.setTimerDuration(limitedMinutes)
                                    
                                    // Kurze Verzögerung vor dem Starten des Timers
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        // Starte den Timer automatisch
                                        it.startTimer()
                                        Log.d(TAG, "Timer erfolgreich gestartet: $limitedMinutes Minuten")
                                    }, 500) // 500ms Verzögerung vor dem Starten
                                    
                                } ?: run {
                                    Log.e(TAG, "FocusModeFragment nicht gefunden, versuche direkten Zugriff")
                                    
                                    // Alternativer Ansatz: Versuche, das Fragment direkt zu finden
                                    val fragments = fragmentManager.fragments
                                    for (fragment in fragments) {
                                        Log.d(TAG, "Fragment in Liste: ${fragment.javaClass.simpleName}")
                                        if (fragment is FocusModeFragment) {
                                            Log.d(TAG, "FocusModeFragment in Liste gefunden")
                                            fragment.setTimerDuration(limitedMinutes)
                                            Handler(Looper.getMainLooper()).postDelayed({
                                                fragment.startTimer()
                                                Log.d(TAG, "Timer erfolgreich gestartet (alternativer Weg): $limitedMinutes Minuten")
                                            }, 500)
                                            break
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Fehler beim Starten des Timers", e)
                            }
                        }, 1000) // 1000ms Verzögerung (erhöht von 500ms)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler beim Navigieren zum Timer", e)
                }
            }
            is ChatAction.OpenCalendar -> {
                val intent = Intent(Intent.ACTION_VIEW)
                intent.data = Uri.parse("content://com.android.calendar/time")
                startActivity(intent)
            }
            is ChatAction.PlaySpotify -> {
                try {
                    val spotifyPackageName = "com.spotify.music"
                    
                    // Versuche zuerst, Spotify mit einem speziellen Intent zu starten, der die Wiedergabe beginnt
                    try {
                        Log.d(TAG, "Versuche Spotify mit Wiedergabe-Intent zu starten")
                        
                        // Methode 1: Verwende einen speziellen URI, der die Wiedergabe startet
                        val playIntent = Intent(Intent.ACTION_VIEW).apply {
                            setData(Uri.parse("spotify:"))
                            setPackage(spotifyPackageName)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            putExtra("play", true)
                            putExtra("playback", true)
                            putExtra("autoplay", true)
                        }
                        startActivity(playIntent)
                        
                        // Kurze Verzögerung, dann sende Wiedergabebefehl
                        Handler(Looper.getMainLooper()).postDelayed({
                            try {
                                // Sende mehrere Wiedergabebefehle für höhere Erfolgsrate
                                sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
                                sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                
                                // Sende auch einen Broadcast an Spotify direkt
                                val playbackIntent = Intent("com.spotify.mobile.android.ui.widget.PLAY")
                                playbackIntent.setPackage(spotifyPackageName)
                                requireContext().sendBroadcast(playbackIntent)
                                
                                // Zeige eine Benachrichtigung an
                                Toast.makeText(
                                    requireContext(),
                                    "Starte Spotify-Wiedergabe",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                Log.e(TAG, "Fehler beim Senden der Wiedergabebefehle: ${e.message}")
                            }
                        }, 1500) // 1.5 Sekunden Verzögerung
                        
                        return@handleChatAction
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler beim Starten der Spotify-Wiedergabe mit URI: ${e.message}")
                        // Wenn der erste Ansatz fehlschlägt, versuchen wir den zweiten
                    }
                    
                    // Methode 2: Versuche, die Spotify-App zu öffnen und dann einen Medien-Intent zu senden
                    try {
                        Log.d(TAG, "Versuche Spotify normal zu öffnen")
                        
                        // Starte zuerst die Spotify-App
                        val spotifyIntent = requireContext().packageManager.getLaunchIntentForPackage(spotifyPackageName)
                        if (spotifyIntent != null) {
                            spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            spotifyIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
                            startActivity(spotifyIntent)
                            
                            // Längere Verzögerung für den Fallback
                            Handler(Looper.getMainLooper()).postDelayed({
                                try {
                                    // Sende mehrere Wiedergabebefehle für höhere Erfolgsrate
                                    sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY)
                                    sendMediaButtonEvent(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                                    
                                    // Sende auch einen Broadcast an Spotify direkt
                                    val playbackIntent = Intent("com.spotify.mobile.android.ui.widget.PLAY")
                                    playbackIntent.setPackage(spotifyPackageName)
                                    requireContext().sendBroadcast(playbackIntent)
                                    
                                    Log.d(TAG, "Spotify-Wiedergabebefehl gesendet")
                                } catch (e: Exception) {
                                    Log.e(TAG, "Fehler beim Senden des Wiedergabebefehls: ${e.message}")
                                }
                            }, 2000) // 2 Sekunden Verzögerung
                        } else {
                            // Spotify ist nicht installiert, öffne den Play Store
                            val marketIntent = Intent(Intent.ACTION_VIEW)
                            marketIntent.data = Uri.parse("market://details?id=$spotifyPackageName")
                            startActivity(marketIntent)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler beim Öffnen von Spotify", e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Allgemeiner Fehler bei der Spotify-Aktion", e)
                }
            }
            is ChatAction.StartVoiceInput -> {
                startVoiceRecognition()
            }
            is ChatAction.ClearHistory -> {
                showClearHistoryDialog()
            }
            else -> {
                Log.e(TAG, "Unbekannte ChatAction: $action")
            }
        }
    }
    
    /**
     * Behandelt Klicks auf ActionItem-Elemente in Chat-Nachrichten
     */
    private fun handleActionItemClick(action: ActionItem) {
        Log.d(TAG, "ActionItem geklickt: ${action.label} (${action.id})")
        
        when (action.id) {
            "save_ideas" -> saveGeneratedIdeas()
            "to_mindmap" -> transferIdeasToMindMap()
            "more_ideas" -> showIdeaCollectionDialog()
            // Weitere Aktionen können hier hinzugefügt werden
            else -> {
                // Wenn nichts passt, einfach den Text in die Eingabe einfügen
                messageInput.setText(action.label)
                messageInput.setSelection(messageInput.text.length)
            }
        }
    }

    private fun saveGeneratedIdeas() {
        // Finde die letzte KI-Nachricht mit den Ideen
        val lastAssistantMessage = findLastAssistantMessageWithIdeas()
        
        if (lastAssistantMessage != null) {
            // Zeige Bestätigungsdialog
            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Ideen speichern")
            builder.setMessage("Möchtest du diese Ideen in deinen gespeicherten Notizen archivieren?")
            
            builder.setPositiveButton("Speichern") { _, _ ->
                try {
                    // Speichere in SharedPreferences
                    val sharedPreferences = requireContext().getSharedPreferences("saved_ideas", Context.MODE_PRIVATE)
                    val savedIdeas = sharedPreferences.getStringSet("ideas_list", mutableSetOf())?.toMutableSet() ?: mutableSetOf()
                    
                    // Generiere einen Zeitstempel für den Eintrag
                    val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                    val ideaEntry = "$timestamp: ${lastAssistantMessage.content}"
                    
                    savedIdeas.add(ideaEntry)
                    sharedPreferences.edit().putStringSet("ideas_list", savedIdeas).apply()
                    
                    showSnackbar("Ideen wurden gespeichert")
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler beim Speichern der Ideen", e)
                    showSnackbar("Fehler beim Speichern der Ideen")
                }
            }
            
            builder.setNegativeButton("Abbrechen", null)
            builder.show()
        } else {
            showSnackbar("Keine Ideen zum Speichern gefunden")
        }
    }
    
    private fun transferIdeasToMindMap() {
        // Finde die letzte KI-Nachricht mit den Ideen
        val lastAssistantMessage = findLastAssistantMessageWithIdeas()
        
        if (lastAssistantMessage != null) {
            try {
                // Starte die MindMap-Aktivität mit den Ideen als Extra
                val intent = Intent(requireContext(), MindMapActivity::class.java)
                intent.putExtra("generated_ideas", lastAssistantMessage.content)
                startActivity(intent)
                
                showSnackbar("Ideen an MindMap übertragen")
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Übertragen der Ideen zur MindMap", e)
                showSnackbar("Fehler beim Übertragen zur MindMap")
            }
        } else {
            showSnackbar("Keine Ideen zum Übertragen gefunden")
        }
    }
    
    private fun findLastAssistantMessageWithIdeas(): ChatMessage? {
        // Durchlaufe die Chatnachrichten rückwärts und finde die letzte KI-Nachricht
        val messages = chatManager.getMessages()
        for (i in messages.size - 1 downTo 0) {
            val message = messages[i]
            if (!message.isUser && !message.isTyping && message.content.isNotEmpty() && 
                (message.content.contains("1.") || message.content.contains("Idee"))) {
                return message
            }
        }
        return null
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
        
        // Stoppe alle laufenden Spracherkennungs- und Sprachausgabeprozesse
        try {
            stopSpeaking()
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler beim Stoppen der Sprachprozesse", e)
        }
        
        // SpeechManager herunterfahren
        try {
            speechManager.shutdown()
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler beim Herunterfahren des SpeechManagers", e)
        }
        
        // ToneGenerator-Ressourcen freigeben
        try {
            toneGenerator?.release()
            toneGenerator = null
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler beim Freigeben der ToneGenerator-Ressourcen", e)
        }
        
        Log.d("AIChatFragment", "Fragment-Ressourcen erfolgreich freigegeben")
    }
    
    override fun onPause() {
        super.onPause()
        
        // Stoppe die Spracherkennung, wenn das Fragment pausiert wird
        try {
            if (isSpeaking) {
                stopSpeaking()
                Log.d("AIChatFragment", "Spracherkennung bei Pause gestoppt")
            }
        } catch (e: Exception) {
            Log.e("AIChatFragment", "Fehler beim Stoppen der Spracherkennung in onPause", e)
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
                        priority = com.deepcore.kiytoapp.data.Priority.MEDIUM,
                        created = Date(),
                        completedDate = null
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
            showIdeaCollectionDialog()
            plusMenuContainer.visibility = View.GONE
        }

        analyzeImagesOption.setOnClickListener {
            openPhotoPicker()
            plusMenuContainer.visibility = View.GONE
        }

        moreOption.setOnClickListener {
            // Wake Word und Sprachbefehlsoptionen anzeigen
            showMoreOptionsDialog()
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

    /**
     * Wird aufgerufen, wenn das Wake-Word "Hei Kiyto" erkannt wurde
     */
    fun onWakeWordDetected() {
        // UI-Thread sicherstellen
        activity?.runOnUiThread {
            // Visuelle Bestätigung, dass Wake-Word erkannt wurde
            // Kurz aufleuchten lassen durch Änderung des Bildmaterials
            voiceButton.setImageResource(R.drawable.ic_mic_active)
            
            // Nach kurzer Verzögerung zum ursprünglichen Zustand zurückkehren
            Handler(Looper.getMainLooper()).postDelayed({
                voiceButton.setImageResource(R.drawable.ic_mic)
            }, 500)
            
            // Spracherkennung starten
            if (!isSpeaking) {
                // Start-Sound abspielen
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150) // 150ms Ton
                } catch (e: Exception) {
                    Log.e("AIChatFragment", "Fehler beim Abspielen des Start-Sounds", e)
                }
                startVoiceRecognition()
            }
        }
    }

    /**
     * Zeigt einen Dialog mit erweiterten Optionen an, einschließlich Wake Word-Steuerung
     */
    private fun showMoreOptionsDialog() {
        // Dialog erstellen
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Sprachsteuerung & KI-Funktionen")
        
        // Optionen für den Dialog definieren
        val options = arrayOf(
            "Wake Word aktivieren/deaktivieren",
            "Wake Word Empfindlichkeit einstellen",
            "Individuelle Sprachbefehle einrichten",
            "Sprachbefehl-Übersicht anzeigen",
            "KI-Training starten"
        )
        
        builder.setItems(options) { dialog, which ->
            when (which) {
                0 -> toggleWakeWordService()
                1 -> showWakeWordSensitivityDialog()
                2 -> showCustomCommandsDialog()
                3 -> showVoiceCommandsOverview()
                4 -> startAITraining()
            }
        }
        
        // Abbrechen-Button hinzufügen
        builder.setNegativeButton("Abbrechen") { dialog, _ ->
            dialog.dismiss()
        }
        
        // Dialog anzeigen
        builder.show()
    }
    
    /**
     * Aktiviert oder deaktiviert den Wake Word Service
     */
    private fun toggleWakeWordService() {
        val mainActivity = activity as? MainActivity
        mainActivity?.let {
            // Prüfen, ob der Service bereits läuft
            val isRunning = it.isWakeWordServiceRunning()
            
            if (isRunning) {
                // Service deaktivieren
                it.stopWakeWordService()
                showSnackbar("Wake Word-Erkennung deaktiviert")
            } else {
                // Service aktivieren
                it.startWakeWordService()
                showSnackbar("Wake Word-Erkennung aktiviert")
            }
        }
    }
    
    /**
     * Zeigt einen Dialog zur Einstellung der Wake Word-Empfindlichkeit
     */
    private fun showWakeWordSensitivityDialog() {
        // Platzhalter für zukünftige Funktionalität
        showSnackbar("Diese Funktion wird in Kürze verfügbar sein")
    }
    
    /**
     * Zeigt einen Dialog zum Einrichten individueller Sprachbefehle
     */
    private fun showCustomCommandsDialog() {
        // Platzhalter für zukünftige Funktionalität
        showSnackbar("Diese Funktion wird in Kürze verfügbar sein")
    }
    
    /**
     * Zeigt eine Übersicht aller verfügbaren Sprachbefehle
     */
    private fun showVoiceCommandsOverview() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Verfügbare Sprachbefehle")
        
        val commands = """
            • "Hei Kiyto" - Aktiviert die Spracherkennung
            • "Erstelle eine Notiz" - Öffnet die Notizfunktion
            • "Erstelle eine Aufgabe" - Erstellt eine neue Aufgabe
            • "Öffne den Kalender" - Öffnet den Kalender
            • "Zeige meine Aufgaben" - Zeigt alle Aufgaben an
            • "Erzeuge ein Bild" - Startet die Bildgenerierung
        """.trimIndent()
        
        builder.setMessage(commands)
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }
    
    /**
     * Startet das KI-Training für bessere Spracherkennung
     */
    private fun startAITraining() {
        // Platzhalter für zukünftige Funktionalität
        showSnackbar("Diese Funktion wird in Kürze verfügbar sein")
    }

    private fun showIdeaCollectionDialog() {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle("Ideen sammeln")
        
        // Layout für die Themeneingabe erstellen
        val inputLayout = LinearLayout(requireContext())
        inputLayout.orientation = LinearLayout.VERTICAL
        inputLayout.setPadding(32, 16, 32, 16)
        
        val themeLabel = TextView(requireContext())
        themeLabel.text = "Zu welchem Thema möchtest du Ideen sammeln?"
        themeLabel.setPadding(0, 0, 0, 16)
        
        val themeInput = EditText(requireContext())
        themeInput.hint = "z.B. Produktentwicklung, Marketingstrategien, Projektplanung..."
        themeInput.inputType = InputType.TYPE_CLASS_TEXT
        
        val quantityLabel = TextView(requireContext())
        quantityLabel.text = "Wie viele Ideen werden benötigt?"
        quantityLabel.setPadding(0, 24, 0, 16)
        
        val quantitySlider = SeekBar(requireContext())
        quantitySlider.max = 9 // 1-10 Ideen
        quantitySlider.progress = 4 // Standard: 5 Ideen
        
        val quantityText = TextView(requireContext())
        quantityText.text = "5 Ideen"
        quantityText.gravity = Gravity.CENTER
        
        // SeekBar Listener
        quantitySlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                quantityText.text = "${progress + 1} Ideen"
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Layout zusammenbauen
        inputLayout.addView(themeLabel)
        inputLayout.addView(themeInput)
        inputLayout.addView(quantityLabel)
        inputLayout.addView(quantitySlider)
        inputLayout.addView(quantityText)
        
        builder.setView(inputLayout)
        
        // Buttons hinzufügen
        builder.setPositiveButton("Ideen generieren") { dialog, _ ->
            val theme = themeInput.text.toString().trim()
            if (theme.isNotEmpty()) {
                generateIdeasForTheme(theme, quantitySlider.progress + 1)
            } else {
                showSnackbar("Bitte gib ein Thema ein")
            }
            dialog.dismiss()
        }
        
        builder.setNegativeButton("Abbrechen") { dialog, _ ->
            dialog.dismiss()
        }
        
        // Dialog anzeigen
        builder.show()
    }
    
    private fun generateIdeasForTheme(theme: String, quantity: Int) {
        // Füge eine Benutzernachricht zum Chat hinzu
        val userMessage = ChatMessage("Generiere $quantity Ideen zum Thema: $theme", true)
        adapter.addMessage(userMessage)
        chatManager.addMessage(userMessage)
        
        // Zeige "Assistent schreibt..." an
        val typingMessage = ChatMessage("", false, isTyping = true)
        adapter.addMessage(typingMessage)
        scrollToBottom()
        
        // Starte Coroutine für die API-Anfrage
        lifecycleScope.launch {
            try {
                // Erstelle den Prompt für die API
                val prompt = """
                    Bitte generiere $quantity kreative und umsetzbare Ideen zum Thema "$theme".
                    Formatiere die Antwort als nummerierte Liste mit kurzen Erklärungen zu jeder Idee.
                    Die Ideen sollten innovativ, praktisch und gut durchdacht sein.
                """.trimIndent()
                
                // Rufe die OpenAI API auf, um Ideen zu generieren
                val response = withContext(Dispatchers.IO) {
                    com.deepcore.kiytoapp.ai.OpenAIService.generateChatResponse(prompt)
                }
                
                // Entferne die "Assistent schreibt..." Nachricht
                adapter.removeLastMessage()
                
                // Füge die Antwort des Assistenten hinzu
                val assistantMessage = ChatMessage(response ?: "Entschuldigung, ich konnte keine Ideen generieren. Bitte versuche es später erneut.", false)
                adapter.addMessage(assistantMessage)
                chatManager.addMessage(assistantMessage)
                
                // Scrollen, um die neuen Nachrichten anzuzeigen
                scrollToBottom()
                
                // Füge Aktionen für die generierten Ideen hinzu
                addIdeaActions()
            } catch (e: Exception) {
                // Fehlerbehandlung
                adapter.removeLastMessage()
                val errorMessage = ChatMessage("Entschuldigung, bei der Ideengenerierung ist ein Fehler aufgetreten: ${e.message}", false)
                adapter.addMessage(errorMessage)
                chatManager.addMessage(errorMessage)
                scrollToBottom()
                Log.e(TAG, "Fehler bei der Ideengenerierung", e)
            }
        }
    }
    
    private fun addIdeaActions() {
        // Füge Aktionschips hinzu, um mit den generierten Ideen zu arbeiten
        val actionMessage = ChatMessage(
            content = "", 
            isUser = false, 
            chatActions = listOf(
                ActionItem("Ideen speichern", "save_ideas"),
                ActionItem("In MindMap übertragen", "to_mindmap"),
                ActionItem("Weitere Ideen", "more_ideas")
            )
        )
        adapter.addMessage(actionMessage)
        scrollToBottom()
    }

    companion object {
        private const val TAG = "AIChatFragment"
        private const val PERMISSION_REQUEST_CODE = 100
        private const val PERMISSIONS_REQUEST_CODE = 100
        private var currentPhotoUri: Uri? = null
    }

    // Hilfsmethode zum Senden von Media Button Events
    private fun sendMediaButtonEvent(keyCode: Int) {
        try {
            val audioManager = requireContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
            
            // Erstelle einen KeyEvent für den Media Button
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            
            // Sende den KeyEvent als Media Button Intent
            val downIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
            downIntent.putExtra(Intent.EXTRA_KEY_EVENT, downEvent)
            requireContext().sendBroadcast(downIntent)
            
            val upIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
            upIntent.putExtra(Intent.EXTRA_KEY_EVENT, upEvent)
            requireContext().sendBroadcast(upIntent)
            
            Log.d(TAG, "Media Button Event für Keycode $keyCode gesendet")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Senden des Media Button Events", e)
        }
    }
} 