package com.deepcore.kiytoapp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
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
import com.deepcore.kiytoapp.ui.FocusModeFragment
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
                R.id.nav_assistant -> AIChatFragment()
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
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_saved_summaries -> {
                startActivity(SavedSummariesActivity.createIntent(this))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
} 