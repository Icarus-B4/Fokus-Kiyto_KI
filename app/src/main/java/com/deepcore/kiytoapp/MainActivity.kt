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
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.fragmentContainer, DashboardFragment(), "dashboard")
                        .commit()
                }

                setupBottomNavigation()
            }
        }

        // UI-Elemente sofort initialisieren
        binding.menuButton.setOnClickListener { button ->
            showCustomPopupMenu(button)
        }

        updateManager = UpdateManager(this)
        checkForUpdates()

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
        popupView.findViewById<TextView>(R.id.menu_wake_word_test)?.setOnClickListener {
            Toast.makeText(this, "Wake Word Erkennung wird gestartet...", Toast.LENGTH_SHORT).show()
            checkAndRequestPermissions()
            popupWindow.dismiss()
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
                if (updateManager.checkForUpdates()) {
                    updateManager.updateDescription?.let { description ->
                        updateManager.updateUrl?.let { url ->
                            val dialog = UpdateDialog.newInstance(description, url)
                            dialog.show(supportFragmentManager, "update_dialog")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Fehler beim Prüfen auf Updates", e)
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
    
    private fun startWakeWordService() {
        if (!isWakeWordServiceRunning) {
            WakeWordService.startService(this)
            isWakeWordServiceRunning = true
        }
    }
    
    private fun stopWakeWordService() {
        if (isWakeWordServiceRunning) {
            WakeWordService.stopService(this)
            isWakeWordServiceRunning = false
        }
    }
    
    private fun handleWakeWordDetected() {
        // Vereinfachte Implementierung, um Absturz zu vermeiden
        try {
            Log.d("MainActivity", "handleWakeWordDetected wurde aufgerufen")
            
            // Sicherstellen, dass wir auf dem Hauptthread arbeiten
            runOnUiThread {
                // Einfach zum Chat-Tab wechseln
                if (::bottomNavigation.isInitialized) {
                    Log.d("MainActivity", "Wechsle zum Assistenten-Tab")
                    
                    // Bundle mit Extra-Parameter erstellen, das an das Fragment übergeben wird
                    val bundle = Bundle()
                    bundle.putBoolean("activate_voice", true)
                    
                    // Starte hier nicht direkt das Fragment, sondern setze eine Variable, 
                    // die später vom NavItemSelectedListener ausgewertet wird
                    pendingVoiceActivation = true
                    
                    // Zum Assistenten-Tab wechseln
                    bottomNavigation.selectedItemId = R.id.nav_assistant
                    
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
        } catch (e: Exception) {
            Log.e("MainActivity", "Fehler bei der Behandlung des Wake Words", e)
        }
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
        
        // Prüfen, ob es sich um einen Wake-Word-Intent handelt
        if (intent?.action == WakeWordService.ACTION_WAKE_WORD_DETECTED) {
            Log.d("MainActivity", "Wake Word erkannt via direkten Intent")
            // Intent speichern, damit es für Fragment-Erstellung verfügbar ist
            setIntent(intent)
            handleWakeWordDetected()
        }
    }

    override fun onResume() {
        super.onResume()
        
        // Wake-Word-Service neu starten, falls er nicht mehr läuft
        if (!isWakeWordServiceRunning) {
            // Kurze Verzögerung, um sicherzustellen, dass die App vollständig geladen ist
            Handler(Looper.getMainLooper()).postDelayed({
                checkAndRequestPermissions()
            }, 1000)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
} 