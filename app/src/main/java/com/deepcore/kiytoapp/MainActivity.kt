package com.deepcore.kiytoapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import com.deepcore.kiytoapp.ai.AIRecommendationsActivity
import com.deepcore.kiytoapp.ai.ChatAdapter
import com.deepcore.kiytoapp.ai.ChatManager
import com.deepcore.kiytoapp.ai.ChatMessage
import com.deepcore.kiytoapp.ai.SpeechManager
import com.deepcore.kiytoapp.auth.AuthManager
import com.deepcore.kiytoapp.auth.LoginActivity
import com.deepcore.kiytoapp.base.BaseActivity
import com.deepcore.kiytoapp.databinding.ActivityMainBinding
import com.deepcore.kiytoapp.services.WakeWordService
import com.deepcore.kiytoapp.ui.FocusModeFragment
import com.deepcore.kiytoapp.update.UpdateDialog
import com.deepcore.kiytoapp.update.UpdateManager
import com.deepcore.kiytoapp.update.UpdateSystemFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun Int.dpToPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}

class MainActivity : BaseActivity() {
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var voiceButton: FloatingActionButton
    private lateinit var speechManager: SpeechManager
    private lateinit var chatManager: ChatManager
    private lateinit var adapter: ChatAdapter
    private lateinit var binding: ActivityMainBinding
    private var isSpeaking: Boolean = false
    private lateinit var updateManager: UpdateManager
    private var wakeWordReceiver: BroadcastReceiver? = null
    private val RECORD_AUDIO_PERMISSION_CODE = 1001
    private var isWakeWordServiceRunning = false
    private var pendingVoiceActivation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialisiere Services im Hintergrund
        lifecycleScope.launch(Dispatchers.IO) {
            // Initialisiere OpenAI Service
            com.deepcore.kiytoapp.ai.OpenAIService.initialize(this@MainActivity)
            
            withContext(Dispatchers.Main) {
                // Initialisiere Speech Manager
                speechManager = SpeechManager(this@MainActivity, com.deepcore.kiytoapp.ai.OpenAIService)
                chatManager = ChatManager(this@MainActivity)

                if (savedInstanceState == null) {
                    // Prüfe, ob ein Update angezeigt werden soll
                    if (intent.getBooleanExtra("show_update", false)) {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, UpdateSystemFragment(), "update_system")
                            .commit()
                    } else {
                        supportFragmentManager.beginTransaction()
                            .replace(R.id.fragmentContainer, DashboardFragment(), "dashboard")
                            .commit()
                    }
                }

                setupBottomNavigation()
            }
        }

        // UI-Elemente sofort initialisieren
        binding.menuButton.setOnClickListener { button ->
            showCustomPopupMenu(button)
        }

        updateManager = UpdateManager(this)
        
        // Prüfe auf Updates, wenn nicht bereits durch die SplashActivity angezeigt
        if (!intent.getBooleanExtra("show_update", false)) {
            checkForUpdates()
        }

        // Wake-Word-Erkennung initialisieren
        setupWakeWordDetection()
        
        // Automatisch Wake-Word-Service starten, sobald die App geöffnet wird
        checkAndRequestPermissions()
    }

    private fun setupBottomNavigation() {
        bottomNavigation = findViewById(R.id.bottomNavigation)
        
        // Menüpunkte basierend auf Login-Status aktivieren/deaktivieren
        val authManager = AuthManager(this)
        val menu = bottomNavigation.menu
        menu.findItem(R.id.nav_tasks)?.isEnabled = authManager.isLoggedIn()
        menu.findItem(R.id.nav_focus)?.isEnabled = authManager.isLoggedIn()
        menu.findItem(R.id.nav_assistant)?.isEnabled = authManager.isLoggedIn()
        menu.findItem(R.id.nav_day_visualizer)?.isEnabled = authManager.isLoggedIn()
        
        // Deaktiviere den Standard-Farbselektor
        bottomNavigation.itemIconTintList = null
        
        // Setze alle Icons auf inaktiv
        menu.findItem(R.id.nav_dashboard).icon = getDrawable(R.drawable.ic_dashboard_inactive)
        menu.findItem(R.id.nav_day_visualizer).icon = getDrawable(R.drawable.ic_timeline_inactive)
        menu.findItem(R.id.nav_focus).icon = getDrawable(R.drawable.ic_focus_inactive)
        menu.findItem(R.id.nav_assistant).icon = getDrawable(R.drawable.ic_assistant_inactive)
        menu.findItem(R.id.nav_tasks).icon = getDrawable(R.drawable.ic_tasks_inactive)
        
        // Setze das Dashboard-Icon auf farbig, da es beim Start ausgewählt ist
        menu.findItem(R.id.nav_dashboard).icon = getDrawable(R.drawable.ic_dashboard_colored)
        
        // Variable zum Speichern des letzten ausgewählten Menüpunkts
        var lastSelectedItemId = R.id.nav_dashboard
        
        bottomNavigation.setOnItemSelectedListener { item ->
            if (!authManager.isLoggedIn() && item.itemId != R.id.nav_dashboard) {
                // Wenn nicht eingeloggt und nicht Dashboard, zum Login weiterleiten
                startActivity(Intent(this, LoginActivity::class.java))
                return@setOnItemSelectedListener false
            }
            
            // Aktualisiere die Icons - setze alle auf inaktiv
            menu.findItem(R.id.nav_dashboard).icon = getDrawable(R.drawable.ic_dashboard_inactive)
            menu.findItem(R.id.nav_day_visualizer).icon = getDrawable(R.drawable.ic_timeline_inactive)
            menu.findItem(R.id.nav_focus).icon = getDrawable(R.drawable.ic_focus_inactive)
            menu.findItem(R.id.nav_assistant).icon = getDrawable(R.drawable.ic_assistant_inactive)
            menu.findItem(R.id.nav_tasks).icon = getDrawable(R.drawable.ic_tasks_inactive)
            
            // Setze das ausgewählte Icon auf farbig
            when (item.itemId) {
                R.id.nav_dashboard -> item.icon = getDrawable(R.drawable.ic_dashboard_colored)
                R.id.nav_day_visualizer -> item.icon = getDrawable(R.drawable.ic_timeline_colored)
                R.id.nav_focus -> item.icon = getDrawable(R.drawable.ic_focus_colored)
                R.id.nav_assistant -> item.icon = getDrawable(R.drawable.ic_assistant_colored)
                R.id.nav_tasks -> item.icon = getDrawable(R.drawable.ic_tasks_colored)
            }
            
            // Prüfen, ob der Benutzer zweimal auf den gleichen Menüpunkt geklickt hat
            val isReselected = item.itemId == lastSelectedItemId && item.itemId == R.id.nav_day_visualizer
            
            // Alle vorherigen Fragments aus dem Back Stack entfernen
            supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
            
            val fragment = when (item.itemId) {
                R.id.nav_dashboard -> DashboardFragment()
                R.id.nav_tasks -> TaskFragment()
                R.id.nav_focus -> FocusModeFragment()
                R.id.nav_assistant -> {
                    val chatFragment = AIChatFragment()
                    
                    // Wenn pendingVoiceActivation gesetzt ist, das an das Fragment weitergeben
                    if (pendingVoiceActivation) {
                        val args = Bundle()
                        args.putBoolean("activate_voice", true)
                        chatFragment.arguments = args
                        pendingVoiceActivation = false // Zurücksetzen nach Verwendung
                        
                        Log.d("MainActivity", "AIChatFragment wird mit activate_voice=true erstellt")
                    }
                    
                    chatFragment
                }
                R.id.nav_day_visualizer -> {
                    val dayVisualizerFragment = com.deepcore.kiytoapp.ui.DayVisualizerFragment()
                    // Wenn der Benutzer zweimal auf den Tagesplaner geklickt hat, setze ein Argument
                    if (isReselected) {
                        val args = Bundle()
                        args.putBoolean("toggle_action_bar", true)
                        dayVisualizerFragment.arguments = args
                    }
                    dayVisualizerFragment
                }
                else -> return@setOnItemSelectedListener false
            }
            
            // Aktualisiere den letzten ausgewählten Menüpunkt
            lastSelectedItemId = item.itemId
            
            // Fragment mit Tag ersetzen wenn es das Dashboard ist
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.fade_in,
                    R.anim.fade_out
                )
                .setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, fragment, 
                    if (item.itemId == R.id.nav_dashboard) "dashboard" else null)
                .commit()
            
            true
        }
    }

    override fun onBackPressed() {
        // Wenn wir uns nicht im Dashboard befinden und zurück drücken,
        // zum Dashboard navigieren
        if (bottomNavigation.selectedItemId != R.id.nav_dashboard) {
            bottomNavigation.selectedItemId = R.id.nav_dashboard
        } else {
            super.onBackPressed()
        }
    }

    private fun checkPermission(): Boolean {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val permission = android.Manifest.permission.RECORD_AUDIO
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), PERMISSION_REQUEST_CODE)
                return false
            }
        }
        return true
    }

    private fun startSpeechToSpeechDialog() {
        if (checkPermission()) {
            isSpeaking = true
            voiceButton.setImageResource(R.drawable.ic_mic_active)
            
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    speechManager.startSpeechToSpeech { response ->
                        lifecycleScope.launch(Dispatchers.Main) {
                            // Fügen Sie die AI-Antwort zum Chat hinzu
                            val aiMessage = ChatMessage(response, false)
                            adapter.addMessage(aiMessage)
                            chatManager.addMessage(aiMessage)
                            scrollToBottom()
                        }
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isSpeaking = false
                        voiceButton.setImageResource(R.drawable.ic_mic)
                    }
                }
            }
        }
    }

    private fun scrollToBottom() {
        // Implementierung hier...
    }

    private fun showCustomPopupMenu(anchorView: View) {
        val popupView = layoutInflater.inflate(R.layout.custom_popup_menu, null)
        
        // Feste Breite statt WRAP_CONTENT
        val popupWindow = PopupWindow(
            popupView,
            200.dpToPx(this),  // Verwendet die Extension-Funktion
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        )
        
        // Position des Popup-Fensters anpassen
        popupWindow.elevation = 8f
        popupWindow.setBackgroundDrawable(null)
        
        // Zeige das Popup an der richtigen Position an
        popupWindow.showAsDropDown(
            anchorView,
            -200.dpToPx(this) + anchorView.width,  // X-Offset
            0  // Y-Offset
        )

        val authManager = AuthManager(this)
        val isLoggedIn = authManager.isLoggedIn()
        
        // Einstellungen
        popupView.findViewById<TextView>(R.id.menu_settings)?.apply {
            visibility = if (isLoggedIn) View.VISIBLE else View.GONE
            setOnClickListener {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    .setReorderingAllowed(true)
                    .replace(R.id.fragmentContainer, SettingsFragment())
                    .addToBackStack(null)
                    .commit()
                popupWindow.dismiss()
            }
        }
        
        // Hinweise
        popupView.findViewById<TextView>(R.id.menu_hints)?.apply {
            visibility = if (isLoggedIn) View.VISIBLE else View.GONE
            setOnClickListener {
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    .setReorderingAllowed(true)
                    .replace(R.id.fragmentContainer, HintsFragment())
                    .addToBackStack(null)
                    .commit()
                popupWindow.dismiss()
            }
        }
        
        // Kontakt
        val contactMenuItem = popupView.findViewById<TextView>(R.id.menu_contact)
        contactMenuItem.setOnClickListener {
            popupWindow.dismiss()
            val intent = Intent(this, AboutMeActivity::class.java)
            startActivity(intent)
        }
        
        // KI-Empfehlungen
        popupView.findViewById<TextView>(R.id.menu_ai_recommendations)?.setOnClickListener {
            startActivity(Intent(this, AIRecommendationsActivity::class.java))
            popupWindow.dismiss()
        }

        // Update System
        popupView.findViewById<TextView>(R.id.menu_update)?.setOnClickListener {
            supportFragmentManager.beginTransaction()
                .setCustomAnimations(
                    R.anim.fade_in,
                    R.anim.fade_out
                )
                .setReorderingAllowed(true)
                .replace(R.id.fragmentContainer, UpdateSystemFragment())
                .addToBackStack(null)
                .commit()
            popupWindow.dismiss()
        }
        
        // Wake Word Test
        val wakeWordMenuItem = popupView.findViewById<TextView>(R.id.menu_wake_word_test)
        wakeWordMenuItem?.let {
            // Text basierend auf dem aktuellen Status setzen
            if (isWakeWordServiceRunning) {
                it.text = getString(R.string.wake_word_disable)
                
                // Versuche, das mic_off-Icon zu verwenden, falls vorhanden
                try {
                    it.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_mic_off, 0, 0, 0)
                } catch (e: Exception) {
                    // Fallback: Verwende das normale mic-Icon
                    it.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_mic, 0, 0, 0)
                    // Farbe oder Alpha des Icons ändern, um deaktiviert anzuzeigen
                    it.compoundDrawables[0]?.alpha = 128 // 50% Alpha für "deaktiviert" Look
                }
            } else {
                it.text = getString(R.string.wake_word_enable)
                it.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_mic, 0, 0, 0)
                // Sicherstellen, dass das Icon volle Opazität hat
                it.compoundDrawables[0]?.alpha = 255
            }

            // OnClickListener setzen
            it.setOnClickListener {
                if (isWakeWordServiceRunning) {
                    // Wake Word Service stoppen
                    Toast.makeText(this, "Wake Word Erkennung wird deaktiviert...", Toast.LENGTH_SHORT).show()
                    stopWakeWordService()
                    // Bestätigung nach erfolgreichem Stoppen
                    Handler(Looper.getMainLooper()).postDelayed({
                        Toast.makeText(this, "Wake Word Erkennung deaktiviert", Toast.LENGTH_SHORT).show()
                    }, 500)
                } else {
                    // Wake Word Service starten
                    Toast.makeText(this, "Wake Word Erkennung wird aktiviert...", Toast.LENGTH_SHORT).show()
                    checkAndRequestPermissions()
                    // Bestätigung wird bereits im onRequestPermissionsResult oder startWakeWordService gegeben
                }
                popupWindow.dismiss()
            }
        }
        
        // Logout/Login Button anpassen
        val logoutButton = popupView.findViewById<TextView>(R.id.menu_logout)
        if (isLoggedIn) {
            logoutButton.text = getString(R.string.logout)
            logoutButton.setOnClickListener {
                showLogoutConfirmationDialog()
                popupWindow.dismiss()
            }
        } else {
            logoutButton.text = getString(R.string.login)
            logoutButton.setOnClickListener {
                startActivity(Intent(this, LoginActivity::class.java))
                popupWindow.dismiss()
            }
        }
    }

    private fun showLogoutConfirmationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirmation)
            .setPositiveButton(R.string.logout) { _, _ ->
                logout()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun logout() {
        val authManager = AuthManager(this)
        authManager.logout()
        
        // Zeige Erfolgsmeldung
        Toast.makeText(this, R.string.logout_success, Toast.LENGTH_SHORT).show()
        
        // Zum Dashboard zurückkehren und Stack leeren
        bottomNavigation.selectedItemId = R.id.nav_dashboard
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        
        // Dashboard-Fragment neu laden
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(
                R.anim.fade_in,
                R.anim.fade_out
            )
            .setReorderingAllowed(true)
            .replace(R.id.fragmentContainer, DashboardFragment(), "dashboard")
            .commit()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here.
        return when (item.itemId) {
            R.id.action_settings -> {
                // SettingsFragment statt SettingsActivity verwenden
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    .setReorderingAllowed(true)
                    .replace(R.id.fragmentContainer, SettingsFragment())
                    .addToBackStack(null)
                    .commit()
                true
            }
            R.id.menu_wake_word_test -> {
                // Manueller WakeWord-Service Test
                Toast.makeText(this, "Wake Word Service wird gestartet...", Toast.LENGTH_SHORT).show()
                checkAndRequestPermissions()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun checkForUpdates() {
        lifecycleScope.launch {
            try {
                // Zeige einen Toast, dass nach Updates gesucht wird
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Suche nach Updates...", Toast.LENGTH_SHORT).show()
                }
                
                // Prüfe auf Updates
                if (updateManager.checkForUpdates()) {
                    // Wenn ein Update verfügbar ist, zeige den UpdateSystemFragment an
                    withContext(Dispatchers.Main) {
                        supportFragmentManager.beginTransaction()
                            .setCustomAnimations(
                                R.anim.fade_in,
                                R.anim.fade_out
                            )
                            .setReorderingAllowed(true)
                            .replace(R.id.fragmentContainer, UpdateSystemFragment())
                            .addToBackStack(null)
                            .commit()
                    }
                } else {
                    // Wenn kein Update verfügbar ist, zeige einen Toast
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Keine Updates verfügbar", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Fehler beim Prüfen auf Updates", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Fehler beim Prüfen auf Updates: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun setupWakeWordDetection() {
        try {
            // BroadcastReceiver für Wake-Word-Erkennung registrieren
            wakeWordReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    Log.d("MainActivity", "BroadcastReceiver.onReceive aufgerufen mit Intent: ${intent?.action}")
                    if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
                        Log.d("MainActivity", "Wake Word erkannt - verarbeite Aktion")
                        handleWakeWordDetected()
                    }
                }
            }
            
            // IntentFilter für den Receiver erstellen
            val intentFilter = IntentFilter(WakeWordService.ACTION_WAKE_WORD_DETECTED)
            intentFilter.addCategory(Intent.CATEGORY_DEFAULT)
            
            // Ab Android 14 (API 34, UPSIDE_DOWN_CAKE) muss man Flags setzen
            if (Build.VERSION.SDK_INT >= 34) { // Direkte API-Nummer verwenden anstelle von Build.VERSION_CODES
                val flags = Context.RECEIVER_NOT_EXPORTED
                registerReceiver(wakeWordReceiver, intentFilter, flags)
                Log.d("MainActivity", "BroadcastReceiver mit RECEIVER_NOT_EXPORTED registriert")
            } else {
                registerReceiver(wakeWordReceiver, intentFilter)
                Log.d("MainActivity", "BroadcastReceiver ohne Flags registriert")
            }
            
            // Erstmal nicht automatisch starten, um sicherzugehen, dass die App startet
            // checkAndRequestPermissions()
            
            // Stattdessen einen Toast anzeigen
            Toast.makeText(
                this,
                "Wake Word Erkennung wird beim App-Start automatisch aktiviert",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Log.e("MainActivity", "Fehler beim Einrichten der Wake-Word-Erkennung", e)
        }
    }
    
    private fun checkAndRequestPermissions() {
        Log.d("MainActivity", "Prüfe und fordere Berechtigungen für Wake-Word-Service an")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                != PackageManager.PERMISSION_GRANTED) {
                // Berechtigung anfordern
                Log.d("MainActivity", "Mikrofon-Berechtigung fehlt, fordere an...")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    RECORD_AUDIO_PERMISSION_CODE
                )
            } else {
                // Berechtigung bereits vorhanden, Dienst starten
                Log.d("MainActivity", "Mikrofon-Berechtigung vorhanden, starte Wake-Word-Service")
                startWakeWordService()
            }
        } else {
            // Für ältere Android-Versionen direkt starten
            Log.d("MainActivity", "Android-Version < M, starte Wake-Word-Service direkt")
            startWakeWordService()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Berechtigung erteilt, Dienst starten
                startWakeWordService()
            } else {
                // Berechtigung verweigert
                Toast.makeText(
                    this,
                    "Mikrofonberechtigung wird für die Wake-Word-Erkennung benötigt",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    
    /**
     * Startet den Wake Word Service nach Berechtigungsprüfung
     */
    fun startWakeWordService() {
        if (!isWakeWordServiceRunning) {
            WakeWordService.startService(this)
            isWakeWordServiceRunning = true
            updateUIForWakeWordStatus()
            Toast.makeText(this, "Wake Word Erkennung aktiviert", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Stoppt den Wake Word Service
     */
    fun stopWakeWordService() {
        WakeWordService.stopService(this)
        isWakeWordServiceRunning = false
        updateUIForWakeWordStatus()
    }
    
    private fun handleWakeWordDetected() {
        // Vereinfachte Implementierung, um Absturz zu vermeiden
        try {
            Log.d("MainActivity", "handleWakeWordDetected wurde aufgerufen")
            
            // Sicherstellen, dass wir auf dem Hauptthread arbeiten
            runOnUiThread {
                try {
                    // Prüfe, ob die Aktivität sich in einem gültigen Zustand befindet
                    if (isFinishing || isDestroyed) {
                        Log.d("MainActivity", "Aktivität wird beendet oder ist bereits zerstört, ignoriere Wake Word")
                        return@runOnUiThread
                    }
                    
                    // Prüfe, ob die Aktivität ihren Zustand bereits gespeichert hat
                    if (!isActivityStateSafe()) {
                        Log.d("MainActivity", "Aktivität hat ihren Zustand bereits gespeichert, verzögere Wake Word-Verarbeitung")
                        // Verzögere die Verarbeitung, bis die Aktivität wieder in einem sicheren Zustand ist
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (!isFinishing && !isDestroyed) {
                                handleWakeWordDetectedSafely()
                            }
                        }, 500) // Kurze Verzögerung
                        return@runOnUiThread
                    }
                    
                    // Wenn alles in Ordnung ist, führe die Aktion sicher aus
                    handleWakeWordDetectedSafely()
                } catch (e: Exception) {
                    Log.e("MainActivity", "Fehler bei der Behandlung des Wake Words", e)
                    Toast.makeText(
                        this@MainActivity,
                        "Fehler bei der Spracherkennung",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Fehler bei der Behandlung des Wake Words (Ask Gemini)", e)
        }
    }
    
    private fun handleWakeWordDetectedSafely() {
        // Einfach zum Chat-Tab wechseln
        if (::bottomNavigation.isInitialized) {
            Log.d("MainActivity", "Wechsle zum Assistenten-Tab")
            
            // Bundle mit Extra-Parameter erstellen, das an das Fragment übergeben wird
            val bundle = Bundle()
            bundle.putBoolean("activate_voice", true)
            
            // Starte hier nicht direkt das Fragment, sondern setze eine Variable, 
            // die später vom NavItemSelectedListener ausgewertet wird
            pendingVoiceActivation = true
            
            try {
                // Versuche die Navigation auf sichere Weise
                if (isActivityStateSafe()) {
                    // Zum Assistenten-Tab wechseln
                    bottomNavigation.selectedItemId = R.id.nav_assistant
                } else {
                    Log.d("MainActivity", "Aktivität ist nicht in einem sicheren Zustand für Fragment-Transaktionen. Verwende verzögerte Navigation.")
                    
                    // Erstelle einen Intent zum Neustarten der MainActivity
                    val intent = Intent(this, MainActivity::class.java).apply {
                        // Flaggen setzen, um die aktuelle Instance zu ersetzen
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        // Aktion für Wake Word setzen
                        action = WakeWordService.ACTION_WAKE_WORD_DETECTED
                        // Extra für das direkte Öffnen des Assistenten-Tabs
                        putExtra("direct_assistant", true)
                    }
                    
                    // Starte die Aktivität neu
                    startActivity(intent)
                    return
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Fehler beim Navigieren zum Assistenten-Tab", e)
                
                // Fallback-Methode: Verzögerte Ausführung nach Neuerstellung der Aktivität
                Handler(Looper.getMainLooper()).postDelayed({
                    try {
                        // Nur ausführen, wenn die Aktivität noch aktiv ist
                        if (!isFinishing && !isDestroyed) {
                            Log.d("MainActivity", "Verzögerter Versuch, zum Assistenten-Tab zu navigieren")
                            bottomNavigation.selectedItemId = R.id.nav_assistant
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Fehler beim verzögerten Navigieren zum Assistenten-Tab", e)
                    }
                }, 1000) // Längere Verzögerung
            }
            
            // Toast-Meldung anzeigen
            Toast.makeText(
                this,
                "Wake Word erkannt: 'Hei Kiyto'",
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Log.e("MainActivity", "bottomNavigation ist nicht initialisiert")
        }
    }
    
    /**
     * Prüft, ob die Aktivität sich in einem Zustand befindet, in dem Fragment-Transaktionen sicher sind
     */
    private fun isActivityStateSafe(): Boolean {
        val isInResumedState = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        val isStateSaved = supportFragmentManager.isStateSaved
        
        val isSafe = !isFinishing && !isDestroyed && !isStateSaved && isInResumedState
        
        Log.d("MainActivity", "Activity-Zustand: " +
                "isFinishing=$isFinishing, " +
                "isDestroyed=$isDestroyed, " +
                "isStateSaved=$isStateSaved, " +
                "isInResumedState=$isInResumedState, " +
                "ERGEBNIS=$isSafe")
        
        return isSafe
    }
    
    override fun onDestroy() {
        // BroadcastReceiver abmelden
        wakeWordReceiver?.let {
            unregisterReceiver(it)
        }
        
        // Wake-Word-Dienst beenden
        stopWakeWordService()
        
        super.onDestroy()
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("MainActivity", "onNewIntent aufgerufen mit Action: ${intent?.action}")
        
        // Intent speichern, damit es für Fragment-Erstellung verfügbar ist
        setIntent(intent)

        // Prüfen, ob es sich um einen Wake-Word-Intent handelt
        if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
            Log.d("MainActivity", "Wake Word erkannt via direkten Intent")
            handleWakeWordDetected()
        } 
        // Prüfen, ob direkt zum Assistenten-Tab navigiert werden soll
        else if (intent?.getBooleanExtra("direct_assistant", false) == true) {
            Log.d("MainActivity", "Direkter Wechsel zum Assistenten-Tab über Intent")
            
            // Verzögere die Navigation leicht, um sicherzustellen, dass die Activity vollständig wiederhergestellt ist
            Handler(Looper.getMainLooper()).postDelayed({
                try {
                    if (!isFinishing && !isDestroyed) {
                        pendingVoiceActivation = true
                        bottomNavigation.selectedItemId = R.id.nav_assistant
                        
                        Toast.makeText(
                            this,
                            "Wake Word erkannt: 'Hei Kiyto'",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    Log.e("MainActivity", "Fehler beim Navigieren zum Assistenten-Tab in onNewIntent", e)
                }
            }, 300)
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Aktualisiere die UI-Elemente basierend auf dem aktuellen Status
        updateUIForWakeWordStatus()
        
        // HINWEIS: Wir starten den Wake Word Service nicht mehr automatisch,
        // da der Benutzer ihn jetzt manuell über das Menü ein-/ausschalten kann.
        
        // Nur noch bei Bedarf prüfen, ob der Service tatsächlich läuft
        if (isWakeWordServiceRunning) {
            // Prüfe, ob der Service tatsächlich noch läuft (könnte durch System beendet worden sein)
            // Falls er nicht mehr läuft, setzen wir den Status zurück
            // Dies könnte über ServiceConnection oder einen Ping-Mechanismus implementiert werden
        }
    }

    /**
     * Aktualisiert UI-Elemente basierend auf dem aktuellen Status des Wake Word Service
     */
    private fun updateUIForWakeWordStatus() {
        // Hier könnten weitere UI-Elemente aktualisiert werden, falls nötig
        Log.d("MainActivity", "Wake Word Service Status: ${if (isWakeWordServiceRunning) "Aktiv" else "Inaktiv"}")
    }

    /**
     * Gibt zurück, ob der Wake Word Service derzeit aktiv ist
     */
    fun isWakeWordServiceRunning(): Boolean {
        return isWakeWordServiceRunning
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
} 