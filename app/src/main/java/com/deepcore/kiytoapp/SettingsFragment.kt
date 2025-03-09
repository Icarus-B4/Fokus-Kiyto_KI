package com.deepcore.kiytoapp

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.deepcore.kiytoapp.BuildConfig
import com.deepcore.kiytoapp.ai.APISettingsDialog
import com.deepcore.kiytoapp.debug.DebugActivity
import com.deepcore.kiytoapp.settings.NotificationSettingsManager
import com.deepcore.kiytoapp.util.LogUtils
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.deepcore.kiytoapp.databinding.FragmentSettingsBinding
import com.deepcore.kiytoapp.util.NotificationHelper
import com.deepcore.kiytoapp.base.BaseFragment

class SettingsFragment : BaseFragment() {
    private lateinit var notificationSettingsManager: NotificationSettingsManager
    private lateinit var notificationHelper: NotificationHelper

    private val pickSound = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        LogUtils.debug(this, "Ton-Picker Ergebnis: ${result.resultCode}")
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            LogUtils.debug(this, "Ausgewählter Ton URI: $uri")
            
            if (uri == null) {
                LogUtils.warn(this, "Kein Ton ausgewählt (URI ist null)")
                return@registerForActivityResult
            }
            
            try {
                // Überprüfe, ob der URI gültig ist
                val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
                if (cursor == null) {
                    LogUtils.warn(this, "Ausgewählter URI ist ungültig")
                    Toast.makeText(
                        requireContext(),
                        "Ungültiger Benachrichtigungston ausgewählt",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@registerForActivityResult
                } else {
                    cursor.close()
                }
                
                // Speichere und aktualisiere den Ton
                notificationHelper.updateNotificationSound(uri)
                
                // Aktualisiere die Anzeige des ausgewählten Tons
                updateSoundSettingDisplay()
                
                // Überprüfe, ob der Ton korrekt gespeichert wurde
                val savedUri = notificationSettingsManager.notificationSound
                LogUtils.debug(this, "Gespeicherter Ton nach dem Setzen: $savedUri")
                
                if (savedUri.toString() != uri.toString()) {
                    LogUtils.error(this, "Fehler: Gespeicherter Ton stimmt nicht mit dem ausgewählten Ton überein")
                    Toast.makeText(
                        requireContext(),
                        "Fehler beim Speichern des Tons. Bitte versuchen Sie es erneut.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                // Fehlerbehandlung
                LogUtils.error(this, "Fehler beim Aktualisieren des Benachrichtigungstons", e)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.notification_sound_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            LogUtils.debug(this, "Ton-Auswahl abgebrochen oder fehlgeschlagen")
        }
    }

    private val pickImage = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        LogUtils.debug(this, "Bild-Picker Ergebnis: $uri")
        uri?.let {
            try {
                LogUtils.debug(this, "Speichere Hintergrundbild: $it")
                notificationSettingsManager.backgroundImagePath = it.toString()
                notificationHelper.createNotificationChannel()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.notification_background_updated),
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                LogUtils.error(this, "Fehler beim Aktualisieren des Hintergrundbilds", e)
                Toast.makeText(
                    requireContext(),
                    getString(R.string.notification_background_error),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } ?: run {
            LogUtils.warn(this, "Kein Bild ausgewählt (URI ist null)")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        LogUtils.debug(this, "onCreateView aufgerufen")
        return inflater.inflate(R.layout.fragment_settings, null)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LogUtils.debug(this, "onViewCreated aufgerufen")
        
        notificationSettingsManager = NotificationSettingsManager(requireContext())
        notificationHelper = NotificationHelper(requireContext())

        // Zeige aktuelle Einstellungen in Logs
        showCurrentSettings()

        // API-Einstellungen
        view.findViewById<MaterialCardView>(R.id.apiSettingsCard).setOnClickListener {
            LogUtils.debug(this, "API-Einstellungen Card geklickt")
            val dialog = APISettingsDialog.newInstance()
            dialog.show(parentFragmentManager, "api_settings")
        }

        // Benachrichtigungseinstellungen
        setupNotificationSettings(view)

        // Themeneinstellungen
        view.findViewById<MaterialCardView>(R.id.themeSettingsCard).setOnClickListener {
            LogUtils.debug(this, "Themeneinstellungen Card geklickt")
            // TODO: Implementierung der Themeneinstellungen
        }
        
        // Debug-Menüpunkt hinzufügen (nur im Debug-Modus)
        if (BuildConfig.DEBUG) {
            val debugCard = MaterialCardView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                radius = resources.getDimension(R.dimen.card_corner_radius)
                elevation = resources.getDimension(R.dimen.card_elevation)
                setCardBackgroundColor(resources.getColor(android.R.color.darker_gray, null))
                setContentPadding(16, 16, 16, 16)
            }
            
            val debugLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                setPadding(16, 16, 16, 16)
            }
            
            val debugIcon = androidx.appcompat.widget.AppCompatImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.icon_size),
                    resources.getDimensionPixelSize(R.dimen.icon_size)
                )
                setImageResource(android.R.drawable.ic_menu_info_details)
                setColorFilter(resources.getColor(android.R.color.white, null))
            }
            
            val debugText = androidx.appcompat.widget.AppCompatTextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 16
                }
                text = "Debug Logs"
                setTextColor(resources.getColor(android.R.color.white, null))
                textSize = 16f
            }
            
            debugLayout.addView(debugIcon)
            debugLayout.addView(debugText)
            debugCard.addView(debugLayout)
            
            debugCard.setOnClickListener {
                LogUtils.debug(this, "Debug Card geklickt")
                startActivity(Intent(requireContext(), DebugActivity::class.java))
            }
            
            // Füge die Debug-Card zum Layout hinzu
            val settingsLayout = view.findViewById<LinearLayout>(R.id.settingsLayout)
            settingsLayout?.addView(debugCard)
        }
    }

    private fun setupNotificationSettings(view: View) {
        LogUtils.debug(this, "Richte Benachrichtigungseinstellungen ein")
        
        // Hintergrund-Einstellung
        view.findViewById<LinearLayout>(R.id.notification_background_setting)?.let { backgroundSetting ->
            LogUtils.debug(this, "Hintergrund-Einstellung gefunden")
            backgroundSetting.setOnClickListener {
                LogUtils.debug(this, "Hintergrund-Einstellung geklickt, starte Bild-Picker")
                pickImage.launch("image/*")
            }
        } ?: run {
            LogUtils.error(this, "Hintergrund-Einstellung nicht gefunden (notification_background_setting)")
        }

        // Melodie-Einstellung
        view.findViewById<LinearLayout>(R.id.notification_sound_setting)?.let { soundSetting ->
            LogUtils.debug(this, "Melodie-Einstellung gefunden")
            
            // Aktualisiere die Anzeige des ausgewählten Tons
            updateSoundSettingDisplay()
            
            soundSetting.setOnClickListener {
                LogUtils.debug(this, "Melodie-Einstellung geklickt, starte Ton-Picker")
                try {
                    val currentSound = notificationSettingsManager.notificationSound
                    LogUtils.debug(this, "Aktueller Benachrichtigungston: $currentSound")
                    
                    // Überprüfe, ob der aktuelle Sound gültig ist
                    var validCurrentSound = currentSound
                    try {
                        val cursor = requireContext().contentResolver.query(currentSound, null, null, null, null)
                        if (cursor == null) {
                            LogUtils.warn(this, "Aktueller Sound URI ist ungültig, verwende Standard-Benachrichtigungston")
                            validCurrentSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        } else {
                            cursor.close()
                        }
                    } catch (e: Exception) {
                        LogUtils.error(this, "Fehler beim Überprüfen des aktuellen Sound URI", e)
                        validCurrentSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    }
                    
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.choose_notification_sound))
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, validCurrentSound)
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                    
                    // Stelle sicher, dass der Intent korrekt aufgelöst werden kann
                    if (intent.resolveActivity(requireContext().packageManager) != null) {
                        pickSound.launch(intent)
                    } else {
                        LogUtils.error(this@SettingsFragment, "Kein Ton-Picker verfügbar")
                        Toast.makeText(
                            requireContext(),
                            "Kein Ton-Picker verfügbar",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    LogUtils.error(this, "Fehler beim Starten des Ton-Pickers", e)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.notification_sound_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } ?: run {
            LogUtils.error(this, "Melodie-Einstellung nicht gefunden (notification_sound_setting)")
        }

        // Zeitplan-Einstellung
        view.findViewById<LinearLayout>(R.id.notification_schedule_setting)?.let { scheduleLayout ->
            LogUtils.debug(this, "Zeitplan-Einstellung gefunden")
            val scheduleSwitch = SwitchMaterial(requireContext()).apply {
                isChecked = notificationSettingsManager.notificationScheduleEnabled
                LogUtils.debug(this@SettingsFragment, "Zeitplan-Switch initialisiert mit Wert: $isChecked")
                
                setOnCheckedChangeListener { _, isChecked ->
                    LogUtils.debug(this@SettingsFragment, "Zeitplan-Switch geändert auf: $isChecked")
                    notificationSettingsManager.notificationScheduleEnabled = isChecked
                    if (isChecked) {
                        showTimeRangePicker()
                    }
                }
            }
            scheduleLayout.addView(scheduleSwitch)
        } ?: run {
            LogUtils.error(this, "Zeitplan-Einstellung nicht gefunden (notification_schedule_setting)")
        }
    }

    private fun showTimeRangePicker() {
        LogUtils.debug(this, "Zeige Zeitbereich-Picker")
        val startTime = LocalTime.parse(notificationSettingsManager.scheduleStartTime)
        val endTime = LocalTime.parse(notificationSettingsManager.scheduleEndTime)
        
        LogUtils.debug(this, "Aktuelle Startzeit: $startTime, Endzeit: $endTime")
        
        // Startzeit-Picker
        TimePickerDialog(
            requireContext(),
            { _, hourOfDay, minute ->
                val newStartTime = LocalTime.of(hourOfDay, minute)
                LogUtils.debug(this, "Neue Startzeit ausgewählt: $newStartTime")
                notificationSettingsManager.scheduleStartTime = newStartTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                
                // Endzeit-Picker
                TimePickerDialog(
                    requireContext(),
                    { _, endHour, endMinute ->
                        val newEndTime = LocalTime.of(endHour, endMinute)
                        LogUtils.debug(this, "Neue Endzeit ausgewählt: $newEndTime")
                        notificationSettingsManager.scheduleEndTime = newEndTime.format(DateTimeFormatter.ofPattern("HH:mm"))
                    },
                    endTime.hour,
                    endTime.minute,
                    true
                ).show()
            },
            startTime.hour,
            startTime.minute,
            true
        ).show()
    }

    private fun showCurrentSettings() {
        try {
            val soundUri = notificationSettingsManager.notificationSound
            val backgroundPath = notificationSettingsManager.backgroundImagePath
            val scheduleEnabled = notificationSettingsManager.notificationScheduleEnabled
            val startTime = notificationSettingsManager.scheduleStartTime
            val endTime = notificationSettingsManager.scheduleEndTime
            
            LogUtils.info(this, """
                Aktuelle Benachrichtigungseinstellungen:
                - Sound URI: $soundUri
                - Hintergrundbild: $backgroundPath
                - Zeitplan aktiviert: $scheduleEnabled
                - Startzeit: $startTime
                - Endzeit: $endTime
            """.trimIndent())
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Anzeigen der aktuellen Einstellungen", e)
        }
    }

    /**
     * Aktualisiert die Anzeige des ausgewählten Benachrichtigungstons in der Benutzeroberfläche
     */
    private fun updateSoundSettingDisplay() {
        try {
            view?.findViewById<LinearLayout>(R.id.notification_sound_setting)?.let { soundSetting ->
                // Finde das TextView für den Ton-Namen
                val soundNameTextView = soundSetting.findViewById<TextView>(R.id.notification_sound_value)
                if (soundNameTextView != null) {
                    // Hole den aktuellen Ton
                    val currentSound = notificationSettingsManager.notificationSound
                    
                    // Hole den Namen des Tons
                    val ringtone = RingtoneManager.getRingtone(requireContext(), currentSound)
                    val ringtoneName = ringtone?.getTitle(requireContext()) ?: "Standard"
                    
                    // Setze den Namen in die TextView
                    soundNameTextView.text = ringtoneName
                    LogUtils.debug(this, "Benachrichtigungston-Anzeige aktualisiert: $ringtoneName")
                } else {
                    LogUtils.error(this, "TextView für Ton-Namen nicht gefunden")
                }
            }
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Aktualisieren der Ton-Anzeige", e)
        }
    }
} 