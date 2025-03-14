package com.deepcore.kiytoapp.services

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.deepcore.kiytoapp.MainActivity
import com.deepcore.kiytoapp.R
import com.deepcore.kiytoapp.ai.OpenAIService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Ein Service zur Erkennung von Wake Words.
 */
class WakeWordService : Service() {
    private val TAG = "WakeWordService"
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "wake_word_channel"
    
    private var serviceJob: Job? = null
    private var isRunning = false
    private var wakeLock: PowerManager.WakeLock? = null
    
    // Audio-Aufnahme-Konfiguration
    private val SAMPLE_RATE = 16000
    private val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    private val BUFFER_SIZE_FACTOR = 2
    private val RECORDING_DURATION_MS = 3000 // 3 Sekunden für Wake-Word-Erkennung
    private val DETECTION_INTERVAL_MS = 1000 // 1 Sekunde Pause zwischen Erkennungen
    
    // Wake-Word-Konfiguration
    private val WAKE_WORD = "hei kiyto"
    private val WAKE_WORD_ALTERNATIVES = listOf(
        "hey kiyto", 
        "hei kyto",
        "hey kyto",
        "hai kiyto",
        "hey kito",
        "hey kito"
    )
    
    companion object {
        const val ACTION_START_SERVICE = "com.deepcore.kiytoapp.START_WAKE_WORD_SERVICE"
        const val ACTION_STOP_SERVICE = "com.deepcore.kiytoapp.STOP_WAKE_WORD_SERVICE"
        const val ACTION_WAKE_WORD_DETECTED = "com.deepcore.kiytoapp.WAKE_WORD_DETECTED"
        const val EXTRA_ACTIVATE_VOICE = "com.deepcore.kiytoapp.ACTIVATE_VOICE"
        
        fun startService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            intent.action = ACTION_START_SERVICE
            context.startService(intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, WakeWordService::class.java)
            intent.action = ACTION_STOP_SERVICE
            context.startService(intent)
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "WakeWordService erstellt")
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "WakeWordService gestartet mit Action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startWakeWordDetection()
                Log.d(TAG, "WakeWordService im Vordergrund gestartet")
            }
            ACTION_STOP_SERVICE -> {
                stopWakeWordDetection()
                Log.d(TAG, "WakeWordService gestoppt")
            }
        }
        
        return START_STICKY
    }
    
    private fun startWakeWordDetection() {
        if (isRunning) {
            Log.d(TAG, "WakeWordService läuft bereits")
            return
        }
        
        Log.d(TAG, "Starte Wake-Word-Erkennung")
        isRunning = true
        
        // WakeLock aktivieren, um CPU aktiv zu halten
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "WakeWordService::WakeLockTag"
        )
        wakeLock?.acquire(10 * 60 * 1000L) // 10 Minuten Timeout
        
        // Service als Vordergrund-Dienst starten
        startForeground(NOTIFICATION_ID, createNotification())
        
        // Erkennungsprozess starten
        serviceJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive && isRunning) {
                try {
                    Log.d(TAG, "Starte neue Aufnahme für Wake-Word-Erkennung")
                    val wakeWordDetected = detectWakeWord()
                    
                    if (wakeWordDetected) {
                        Log.d(TAG, "Wake Word erkannt: $WAKE_WORD")
                        
                        // Broadcast senden, dass das Wake Word erkannt wurde
                        val broadcastIntent = Intent(ACTION_WAKE_WORD_DETECTED)
                        
                        // Sprache aktivieren
                        broadcastIntent.putExtra(EXTRA_ACTIVATE_VOICE, true)
                        
                        // Expliziten Intent verwenden, der auf die MainActivity zielt
                        broadcastIntent.setPackage(packageName)
                        
                        // Einfach den Standard-Broadcast verwenden, da der Receiver in derselben App registriert ist
                        // und somit kein Sicherheitsrisiko darstellt
                        sendBroadcast(broadcastIntent)
                        
                        // Zusätzlich einen direkten Intent an die MainActivity senden
                        try {
                            val directIntent = Intent(this@WakeWordService, MainActivity::class.java)
                            directIntent.action = ACTION_WAKE_WORD_DETECTED
                            directIntent.putExtra(EXTRA_ACTIVATE_VOICE, true)
                            directIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            directIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            
                            // PendingIntent verwenden, um die App auch aus dem Hintergrund zu öffnen
                            val pendingIntent = PendingIntent.getActivity(
                                this@WakeWordService,
                                0,
                                directIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            pendingIntent.send()
                            
                            // Auch direkt starten als Fallback
                            startActivity(directIntent)
                            
                            Log.d(TAG, "Wake Word-Intent an MainActivity gesendet")
                        } catch (e: Exception) {
                            Log.e(TAG, "Fehler beim Senden des Intents an MainActivity", e)
                        }
                        
                        // Zusätzlich einen Log eintragen, um die Erkennung zu bestätigen
                        Log.i(TAG, "Wake Word-Broadcast gesendet!")
                        
                        // Kurze Pause nach Erkennung
                        delay(DETECTION_INTERVAL_MS.toLong())
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler bei der Wake-Word-Erkennung", e)
                }
                
                // Kurze Pause zwischen Erkennungen
                delay(100)
            }
        }
    }
    
    private fun matchesWakeWord(text: String): Boolean {
        // Exakte Übereinstimmung
        if (text.contains(WAKE_WORD)) {
            return true
        }
        
        // Prüfen der Alternativen
        for (alternative in WAKE_WORD_ALTERNATIVES) {
            if (text.contains(alternative)) {
                return true
            }
        }
        
        // Prüfen einer ungefähren Übereinstimmung
        val words = text.split(" ")
        for (i in 0 until words.size - 1) {
            val current = words[i]
            val next = words[i + 1]
            
            // Erster Teil des Wake Words ist "hei" oder ähnliche Variante
            if (current == "hei" || current == "hey" || current == "hai" || current == "hallo") {
                // Zweiter Teil ist ähnlich zu "kiyto"
                if (next.startsWith("k") && (
                    next.contains("yt") || 
                    next.contains("it") || 
                    next.contains("eit") ||
                    next.contains("ito") ||
                    next.contains("yto") ||
                    next.contains("ito")
                )) {
                    return true
                }
            }
        }
        
        return false
    }
    
    private suspend fun detectWakeWord(): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Starte Audio-Aufnahme für Wake-Word-Erkennung...")
                val audioFile = recordAudio()
                
                if (audioFile != null) {
                    Log.d(TAG, "Audio aufgenommen, starte Transkription...")
                    try {
                        // Audio-Datei transkribieren
                        val transcription = OpenAIService.transcribeAudio(audioFile)
                        audioFile.delete() // Aufräumen
                        
                        // Wake-Word überprüfen
                        if (!transcription.isNullOrEmpty()) {
                            val normalizedText = transcription.lowercase().trim()
                            Log.d(TAG, "Erkannter Text: \"$normalizedText\"")
                            
                            // Verbesserte Überprüfung auf das Wake-Word
                            val containsWakeWord = matchesWakeWord(normalizedText)
                            if (containsWakeWord) {
                                Log.d(TAG, "Wake Word \"$WAKE_WORD\" oder ähnlich erkannt!")
                            } else {
                                Log.d(TAG, "Kein Wake Word in der Aufnahme gefunden.")
                            }
                            return@withContext containsWakeWord
                        } else {
                            Log.d(TAG, "Keine Transkription erhalten.")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler bei der Transkription: ${e.message}", e)
                    }
                } else {
                    Log.e(TAG, "Konnte keine Audio-Datei aufnehmen.")
                }
                return@withContext false
            } catch (e: Exception) {
                Log.e(TAG, "Unerwarteter Fehler bei Wake-Word-Erkennung: ${e.message}", e)
                return@withContext false
            }
        }
    }
    
    private suspend fun recordAudio(): File? = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ) * BUFFER_SIZE_FACTOR
        
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Fehler: Ungültige Aufnahmeparameter")
            return@withContext null
        }
        
        var audioRecord: AudioRecord? = null
        try {
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.e(TAG, "Keine Berechtigung für Audioaufnahme")
                return@withContext null
            }
            
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Fehler: AudioRecord nicht initialisiert")
                audioRecord.release()
                return@withContext null
            }
            
            val outputFile = File(applicationContext.cacheDir, "wake_word_audio.wav")
            val outputStream = ByteArrayOutputStream()
            
            try {
                val audioData = ByteArray(bufferSize)
                audioRecord.startRecording()
                
                // Erfasse Daten für die angegebene Zeit
                val startTime = System.currentTimeMillis()
                val endTime = startTime + RECORDING_DURATION_MS
                
                while (System.currentTimeMillis() < endTime && isRunning) {
                    val readBytes = audioRecord.read(audioData, 0, bufferSize)
                    if (readBytes > 0) {
                        outputStream.write(audioData, 0, readBytes)
                    }
                    delay(10)
                }
                
                // Aufnahme beenden
                audioRecord.stop()
                
                // WAV-Header erstellen und Datei speichern
                val wavData = createWavFileFromRawAudio(outputStream.toByteArray())
                outputFile.writeBytes(wavData)
                
                return@withContext outputFile
            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei der Audioaufnahme", e)
                return@withContext null
            } finally {
                audioRecord.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Erstellen des AudioRecord", e)
            audioRecord?.release()
            return@withContext null
        }
    }
    
    private fun createWavFileFromRawAudio(rawData: ByteArray): ByteArray {
        val channelCount = 1
        val byteRate = SAMPLE_RATE * 2 * channelCount
        
        val output = ByteArrayOutputStream()
        
        // RIFF Header
        output.write("RIFF".toByteArray())
        output.write(intToByteArray(36 + rawData.size))
        output.write("WAVE".toByteArray())
        
        // Format Chunk
        output.write("fmt ".toByteArray())
        output.write(intToByteArray(16))
        output.write(shortToByteArray(1.toShort())) // Format: PCM
        output.write(shortToByteArray(channelCount.toShort()))
        output.write(intToByteArray(SAMPLE_RATE))
        output.write(intToByteArray(byteRate))
        output.write(shortToByteArray((2 * channelCount).toShort())) // Block Align
        output.write(shortToByteArray(16.toShort())) // Bits pro Sample
        
        // Data Chunk
        output.write("data".toByteArray())
        output.write(intToByteArray(rawData.size))
        output.write(rawData)
        
        return output.toByteArray()
    }
    
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte()
        )
    }
    
    private fun shortToByteArray(value: Short): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value.toInt() shr 8).toByte()
        )
    }
    
    private fun stopWakeWordDetection() {
        isRunning = false
        serviceJob?.cancel()
        serviceJob = null
        
        // WakeLock freigeben
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        
        // Service beenden
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Wake Word Erkennung",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Hintergrundprozess zur Erkennung des Wake Words"
                setShowBadge(false)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Kiyto hört")
            .setContentText("Sage 'Hei Kiyto' um zu sprechen")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }
    
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
    
    override fun onDestroy() {
        stopWakeWordDetection()
        super.onDestroy()
        Log.d(TAG, "WakeWordService beendet")
    }
} 