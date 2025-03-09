package com.deepcore.kiytoapp.settings

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.deepcore.kiytoapp.util.LogUtils
import java.util.TimeZone

/**
 * Manager-Klasse für die Kalenderfunktionalität
 * Ermöglicht das Synchronisieren von Aufgaben mit dem Gerätekalender
 */
class CalendarManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Prüft, ob die Kalender-Berechtigung erteilt wurde
     */
    fun hasCalendarPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED &&
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_CALENDAR
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Prüft, ob die Kalendersynchronisierung aktiviert ist
     */
    var calendarSyncEnabled: Boolean
        get() = prefs.getBoolean(KEY_CALENDAR_SYNC_ENABLED, false)
        set(value) {
            LogUtils.debug(this, "Setze Kalendersynchronisierung aktiviert: $value")
            prefs.edit().putBoolean(KEY_CALENDAR_SYNC_ENABLED, value).apply()
        }
    
    /**
     * Gibt die ID des ausgewählten Kalenders zurück oder -1, wenn keiner ausgewählt ist
     */
    var selectedCalendarId: Long
        get() = prefs.getLong(KEY_SELECTED_CALENDAR_ID, -1)
        set(value) {
            LogUtils.debug(this, "Setze ausgewählten Kalender-ID: $value")
            prefs.edit().putLong(KEY_SELECTED_CALENDAR_ID, value).apply()
        }
    
    /**
     * Gibt eine Liste aller verfügbaren Kalender zurück
     * @return Liste von Kalender-Objekten mit ID und Name
     */
    fun getAvailableCalendars(): List<Calendar> {
        if (!hasCalendarPermission()) {
            LogUtils.warn(this, "Keine Kalender-Berechtigung")
            return emptyList()
        }
        
        val calendars = mutableListOf<Calendar>()
        val contentResolver: ContentResolver = context.contentResolver
        
        val uri = CalendarContract.Calendars.CONTENT_URI
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME
        )
        
        try {
            val cursor: Cursor? = contentResolver.query(uri, projection, null, null, null)
            cursor?.use {
                val idColumn = it.getColumnIndex(CalendarContract.Calendars._ID)
                val nameColumn = it.getColumnIndex(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                val accountColumn = it.getColumnIndex(CalendarContract.Calendars.ACCOUNT_NAME)
                
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val name = it.getString(nameColumn)
                    val account = it.getString(accountColumn)
                    
                    calendars.add(Calendar(id, name, account))
                }
            }
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Abrufen der Kalender", e)
        }
        
        return calendars
    }
    
    /**
     * Fügt ein Ereignis zum ausgewählten Kalender hinzu
     * @param title Titel des Ereignisses
     * @param description Beschreibung des Ereignisses
     * @param startTime Startzeit in Millisekunden
     * @param endTime Endzeit in Millisekunden
     * @return ID des erstellten Ereignisses oder -1 bei Fehler
     */
    fun addEvent(title: String, description: String, startTime: Long, endTime: Long): Long {
        if (!hasCalendarPermission() || !calendarSyncEnabled || selectedCalendarId == -1L) {
            LogUtils.warn(this, "Kalendersynchronisierung nicht möglich: Berechtigung=${hasCalendarPermission()}, aktiviert=${calendarSyncEnabled}, kalenderID=${selectedCalendarId}")
            return -1
        }
        
        val contentResolver: ContentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, selectedCalendarId)
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, endTime)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }
        
        try {
            val uri = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            return if (uri != null) {
                ContentUris.parseId(uri)
            } else {
                LogUtils.error(this, "Fehler beim Hinzufügen des Ereignisses: URI ist null")
                -1
            }
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Hinzufügen des Ereignisses", e)
            return -1
        }
    }
    
    /**
     * Aktualisiert ein bestehendes Ereignis
     * @param eventId ID des zu aktualisierenden Ereignisses
     * @param title Neuer Titel
     * @param description Neue Beschreibung
     * @param startTime Neue Startzeit
     * @param endTime Neue Endzeit
     * @return true, wenn das Update erfolgreich war
     */
    fun updateEvent(eventId: Long, title: String, description: String, startTime: Long, endTime: Long): Boolean {
        if (!hasCalendarPermission() || !calendarSyncEnabled || selectedCalendarId == -1L) {
            LogUtils.warn(this, "Kalendersynchronisierung nicht möglich: Berechtigung=${hasCalendarPermission()}, aktiviert=${calendarSyncEnabled}, kalenderID=${selectedCalendarId}")
            return false
        }
        
        val contentResolver: ContentResolver = context.contentResolver
        val values = ContentValues().apply {
            put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DESCRIPTION, description)
            put(CalendarContract.Events.DTSTART, startTime)
            put(CalendarContract.Events.DTEND, endTime)
        }
        
        val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        
        try {
            val updatedRows = contentResolver.update(eventUri, values, null, null)
            return updatedRows > 0
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Aktualisieren des Ereignisses", e)
            return false
        }
    }
    
    /**
     * Löscht ein Ereignis aus dem Kalender
     * @param eventId ID des zu löschenden Ereignisses
     * @return true, wenn das Löschen erfolgreich war
     */
    fun deleteEvent(eventId: Long): Boolean {
        if (!hasCalendarPermission() || !calendarSyncEnabled) {
            LogUtils.warn(this, "Kalendersynchronisierung nicht möglich: Berechtigung=${hasCalendarPermission()}, aktiviert=${calendarSyncEnabled}")
            return false
        }
        
        val contentResolver: ContentResolver = context.contentResolver
        val eventUri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        
        try {
            val deletedRows = contentResolver.delete(eventUri, null, null)
            return deletedRows > 0
        } catch (e: Exception) {
            LogUtils.error(this, "Fehler beim Löschen des Ereignisses", e)
            return false
        }
    }
    
    /**
     * Datenklasse für Kalender-Informationen
     */
    data class Calendar(
        val id: Long,
        val name: String,
        val account: String
    )
    
    companion object {
        private const val PREFS_NAME = "calendar_settings"
        private const val KEY_CALENDAR_SYNC_ENABLED = "calendar_sync_enabled"
        private const val KEY_SELECTED_CALENDAR_ID = "selected_calendar_id"
    }
} 