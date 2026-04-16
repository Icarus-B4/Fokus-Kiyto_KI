package com.deepcore.kiytoapp.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener
import android.speech.tts.Voice
import android.util.Log
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.delay

class SpeechManager(
    private val context: Context
) {
    private val TAG = "SpeechManager"
    
    @Volatile
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionListener: RecognitionListener? = null
    
    // Text-to-Speech
    private var textToSpeech: TextToSpeech? = null
    private var ttsInitialized = false
    private var isRecording = false
    private var isSpeaking = false
    private var currentVoice: Voice? = null
    
    private var mediaPlayer: android.media.MediaPlayer? = null
    
    // Callback für Status-Updates
    private var statusCallback: ((Boolean, Boolean) -> Unit)? = null
    
    // Callback für Lautstärke-Änderungen (für Debug/UI)
    var onVolumeChanged: ((Int) -> Unit)? = null

    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2 // Größerer Buffer für bessere Performance
        private const val SPEECH_TIMEOUT = 3000L // 3.0 Sekunden Stille für automatisches Ende
    }
    
    fun initialize() {
        initTTS()
        initSTT()
        Log.d(TAG, "SpeechManager initialisiert")
    }
    
    private fun initSTT(): Boolean {
        return try {
            if (speechRecognizer == null && SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
                Log.d(TAG, "STT initialisiert")
                true
            } else {
                Log.e(TAG, "Spracherkennung nicht verfügbar auf diesem Gerät")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei STT-Initialisierung", e)
            false
        }
    }
    
    private fun initTTS() {
        if (textToSpeech == null) {
            textToSpeech = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    try {
                        val result = textToSpeech?.setLanguage(Locale.GERMAN)
                        if (result == TextToSpeech.LANG_MISSING_DATA || 
                            result == TextToSpeech.LANG_NOT_SUPPORTED) {
                            Log.e(TAG, "Sprache nicht unterstützt")
                            return@TextToSpeech
                        }
                        
                        // Optimierte Einstellungen für bessere Stimme
                        textToSpeech?.setPitch(1.1f) // Leicht höhere Tonlage
                        textToSpeech?.setSpeechRate(0.95f) // Etwas langsamer für bessere Verständlichkeit
                        
                        // Verbesserte Stimmenauswahl
                        selectBestVoice()
                        
                        // Optimierte Audioqualität
                        textToSpeech?.setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                                .setFlags(android.media.AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                                .build()
                        )
                        
                        ttsInitialized = true
                        Log.d(TAG, "TTS erfolgreich initialisiert")
                    } catch (e: Exception) {
                        Log.e(TAG, "Fehler bei TTS-Konfiguration", e)
                        ttsInitialized = false
                    }
                } else {
                    Log.e(TAG, "TTS konnte nicht initialisiert werden: $status")
                    ttsInitialized = false
                }
            }
        }
    }
    
    private fun selectBestVoice() {
        try {
            val voices = textToSpeech?.voices?.toList() ?: return
            
            // Suche gezielt nach weiblichen deutschen Stimmen
            val germanVoices = voices.filter { voice ->
                voice.locale.language == "de" &&
                !voice.isNetworkConnectionRequired &&
                (voice.name.lowercase().contains("female") || voice.name.lowercase().contains("w-")) 
            }
            
            // Fallback auf allgemeine deutsche Stimmen, falls keine explizit weibliche gefunden wurde
            val fallbackVoices = if (germanVoices.isEmpty()) {
                voices.filter { it.locale.language == "de" }
            } else {
                germanVoices
            }

            // Wähle die Stimme mit der höchsten Qualität
            val bestVoice = fallbackVoices.maxByOrNull { it.quality }
            
            bestVoice?.let {
                textToSpeech?.voice = it
                currentVoice = it
                Log.d(TAG, "Beste weibliche/deutsche Stimme ausgewählt: ${it.name}")
            }
            
            // Pitch und Rate für weibliche Stimmen feinjustieren
            textToSpeech?.setPitch(1.05f) // Ganz leicht höher für femininere Note
            textToSpeech?.setSpeechRate(1.0f) // Standard Geschwindigkeit
            
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Stimmenauswahl", e)
        }
    }
    
    // Speech-to-Speech-Funktion
    suspend fun startSpeechToSpeech(responseHandler: (String) -> Unit): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val spokenText = startListening()
                
                if (!spokenText.isNullOrEmpty()) {
                    val cleanedText = spokenText.trim()
                    if (cleanedText.isNotEmpty()) {
                        // Sende den Text an den Handler für die UI-Aktualisierung
                        responseHandler(cleanedText)
                        
                        // Warte kurz, damit die UI aktualisiert werden kann
                        kotlinx.coroutines.delay(500)
                        
                        // Sprich die Antwort aus
                        if (ttsInitialized) {
                            speak(cleanedText)
                        }
                        
                        return@withContext true
                    }
                }
                return@withContext false
            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei Speech-to-Speech: ${e.message}", e)
                return@withContext false
            }
        }
    }
    
    suspend fun startListening(): String? {
        return withContext(Dispatchers.IO) {
            try {
                // Stelle sicher, dass keine vorherige Aufnahme läuft
                if (isRecording) {
                    Log.d(TAG, "Stoppe vorherige Aufnahme vor dem Neustart")
                    stopRecording()
                    delay(300) // Kurze Pause, um sicherzustellen, dass alles gestoppt wurde
                }
                
                // Warte bis die Sprachausgabe beendet ist
                while (isSpeaking) {
                    delay(100)
                }
                
                // Kurze Pause nach der Sprachausgabe
                delay(500)
                
                // Stelle sicher, dass der AudioRecord ordnungsgemäß initialisiert ist
                // Stelle sicher, dass der WakeWordService pausiert wird, um das Mikrofon freizugeben
                sendWakeWordAction("com.deepcore.kiytoapp.PAUSE_WAKE_WORD")
                delay(300) // Puffer für Dienst-Reaktion
                
                isRecording = true
                statusCallback?.invoke(isRecording, isSpeaking)
                
                val audioFile = recordAudio()
                audioFile?.let { file ->
                    // Nutze Gemini zur Transkription
                    val transcription = if (GeminiService.isEnabled()) {
                        Log.d(TAG, "Nutze Gemini zur Transkription")
                        GeminiService.transcribeAudio(file)
                    } else {
                        null
                    }
                    
                    file.delete() // Cleanup
                    return@withContext transcription
                }
                return@withContext null
            } catch (e: Exception) {
                Log.e(TAG, "Fehler bei der Sprachaufnahme", e)
                return@withContext null
            } finally {
                isRecording = false
                statusCallback?.invoke(isRecording, isSpeaking)
                
                // WakeWordService wieder fortsetzen
                sendWakeWordAction("com.deepcore.kiytoapp.RESUME_WAKE_WORD")
            }
        }
    }

    private fun sendWakeWordAction(action: String) {
        try {
            val intent = Intent(context, com.deepcore.kiytoapp.services.WakeWordService::class.java)
            intent.action = action
            context.startService(intent)
            Log.d(TAG, "Benachrichtige WakeWordService: $action")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Benachrichtigen des WakeWordServices", e)
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
            val audioSource = android.media.MediaRecorder.AudioSource.MIC
            val audioRecord = AudioRecord(
                audioSource,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )
            
            val outputFile = File(context.cacheDir, "audio_recording.wav")
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "Fehler: AudioRecord nicht initialisiert")
                audioRecord.release()
                return@withContext null
            }
            
            val outputStream = ByteArrayOutputStream()
            
            try {
                val audioData = ByteArray(bufferSize)
                audioRecord.startRecording()
                
                var silenceStartTime = System.currentTimeMillis()
                var lastSoundTime = silenceStartTime
                
                while (isRecording) {
                    val readBytes = audioRecord.read(audioData, 0, bufferSize)
                    
                    if (readBytes > 0) {
                        val maxAmplitude = audioData.maxOfOrNull { Math.abs(it.toInt()) } ?: 0
                        
                        // Callback für die UI/Debug
                        onVolumeChanged?.invoke(maxAmplitude)
                        
                        if (maxAmplitude > 50) { // Extrem niedriger Schwellenwert für maximale Sensitivität
                            lastSoundTime = System.currentTimeMillis()
                            if (maxAmplitude > 100) {
                                Log.d(TAG, "Deutliches Geräusch erkannt: Amplitude $maxAmplitude")
                            }
                        }
                        
                        // Immer schreiben, um kontinuierliches Audio zu gewährleisten
                        outputStream.write(audioData, 0, readBytes)
                        
                        // Prüfe auf Stille-Timeout
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSoundTime > SPEECH_TIMEOUT) {
                            Log.d(TAG, "Spracherkennung durch Stille beendet ($SPEECH_TIMEOUT ms)")
                            break
                        }
                    }
                    
                    delay(10)
                }
                
                if (outputStream.size() > 0) {
                    FileOutputStream(outputFile).use { fos ->
                        writeWavHeader(fos, outputStream.size())
                        outputStream.writeTo(fos)
                    }
                    return@withContext outputFile
                }
                
                return@withContext null
            } finally {
                outputStream.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Audioaufnahme", e)
            return@withContext null
        } finally {
            try {
                audioRecord?.apply {
                    stop()
                    release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Cleanup der Audioaufnahme", e)
            }
        }
    }
    
    private fun writeWavHeader(outputStream: FileOutputStream, audioLength: Int) {
        val headerSize = 44
        val totalSize = headerSize + audioLength
        
        // RIFF-Header
        outputStream.write("RIFF".toByteArray())
        outputStream.write(intToByteArray(totalSize - 8))
        outputStream.write("WAVE".toByteArray())
        
        // Format-Chunk
        outputStream.write("fmt ".toByteArray())
        outputStream.write(intToByteArray(16)) // Chunk-Größe
        outputStream.write(shortToByteArray(1)) // Audio-Format (1 = PCM)
        outputStream.write(shortToByteArray(1)) // Anzahl Kanäle
        outputStream.write(intToByteArray(SAMPLE_RATE)) // Sample-Rate
        outputStream.write(intToByteArray(SAMPLE_RATE * 2)) // Byte-Rate
        outputStream.write(shortToByteArray(2)) // Block-Align
        outputStream.write(shortToByteArray(16)) // Bits pro Sample
        
        // Data-Chunk
        outputStream.write("data".toByteArray())
        outputStream.write(intToByteArray(audioLength))
    }
    
    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value shr 8).toByte(),
            (value shr 16).toByte(),
            (value shr 24).toByte()
        )
    }
    
    private fun shortToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            value.toByte(),
            (value shr 8).toByte()
        )
    }
    
    fun stopRecording() {
        isRecording = false
        try {
            speechRecognizer?.apply {
                stopListening()
                cancel()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Stoppen der Aufnahme", e)
        }
    }
    
    fun isPlaying(): Boolean {
        return try {
            mediaPlayer?.isPlaying == true
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Prüfen des Wiedergabestatus", e)
            false
        }
    }
    
    suspend fun speak(text: String) {
        try {
            stopSpeaking()
            isSpeaking = true
            statusCallback?.invoke(isRecording, isSpeaking)
            
            Log.d(TAG, "Generiere Sprache für: \"${text.take(50)}...\"")
            
            // 1. Primärer Pfad: Natives Gemini Audio (die "schöne Stimme")
            val audioData = if (GeminiService.isEnabled()) {
                Log.d(TAG, "Anfrage für multimodale Gemini-Sprachausgabe gestartet")
                GeminiService.generateGeminiSpeech(text)
            } else {
                null
            }
            
            if (audioData != null && audioData.isNotEmpty()) {
                Log.d(TAG, "Gemini Audio erfolgreich empfangen (${audioData.size} Bytes)")
                
                // Audio im Cache speichern
                val tempFile = File(context.cacheDir, "gemini_voice_${System.currentTimeMillis()}.mp3")
                tempFile.writeBytes(audioData)
                
                withContext(Dispatchers.Main) {
                    playAudioFile(tempFile)
                }
            } else {
                // 2. Fallback: Lokale Android TTS (Offline oder API-Fehler)
                Log.w(TAG, "Gemini Sprache fehlgeschlagen oder deaktiviert, nutze lokales TTS-System")
                
                withContext(Dispatchers.Main) {
                    if (textToSpeech != null && ttsInitialized) {
                        // Parameter für die Sprachausgabe
                        val params = Bundle().apply {
                            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
                            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "kiyto_speech_${System.currentTimeMillis()}")
                        }
                        
                        // EventListener für den Abschluss
                        textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {
                                Log.d(TAG, "Lokales TTS gestartet")
                            }
                            override fun onDone(utteranceId: String?) {
                                isSpeaking = false
                                statusCallback?.invoke(isRecording, isSpeaking)
                            }
                            override fun onError(utteranceId: String?) {
                                Log.e(TAG, "Fehler bei lokaler TTS")
                                isSpeaking = false
                                statusCallback?.invoke(isRecording, isSpeaking)
                            }
                        })
                        
                        // Text in Chunks sprechen (Android TTS hat Limits pro Utterance)
                        val chunks = splitTextIntoChunks(text, 350) 
                        for (chunk in chunks) {
                            textToSpeech?.speak(chunk, TextToSpeech.QUEUE_ADD, params, params.getString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID))
                        }
                    } else {
                        Log.e(TAG, "Kein Sprachsystem (KI oder Lokal) verfügbar!")
                        isSpeaking = false
                        statusCallback?.invoke(isRecording, isSpeaking)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Kritischer Fehler bei Sprachausgabe", e)
            isSpeaking = false
            statusCallback?.invoke(isRecording, isSpeaking)
        }
    }
    
    private fun splitTextIntoChunks(text: String, maxChunkSize: Int): List<String> {
        val chunks = mutableListOf<String>()
        val sentences = text.split(Regex("[.!?]+\\s+"))
        
        var currentChunk = StringBuilder()
        for (sentence in sentences) {
            if (currentChunk.length + sentence.length > maxChunkSize) {
                chunks.add(currentChunk.toString())
                currentChunk = StringBuilder(sentence)
            } else {
                if (currentChunk.isNotEmpty()) {
                    currentChunk.append(". ")
                }
                currentChunk.append(sentence)
            }
        }
        
        if (currentChunk.isNotEmpty()) {
            chunks.add(currentChunk.toString())
        }
        
        return chunks
    }
    
    private fun playAudioFile(audioFile: File) {
        mediaPlayer?.release()
        mediaPlayer = null
        
        mediaPlayer = android.media.MediaPlayer().apply {
            setDataSource(audioFile.path)
            setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .build()
            )
            prepare()
            
            setOnPreparedListener {
                start()
            }
            
            setOnCompletionListener {
                release()
                mediaPlayer = null
                audioFile.delete()
                isSpeaking = false
                statusCallback?.invoke(isRecording, isSpeaking)
            }
            
            setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                release()
                mediaPlayer = null
                audioFile.delete()
                isSpeaking = false
                statusCallback?.invoke(isRecording, isSpeaking)
                true
            }
        }
    }
    
    fun stopSpeaking() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            isSpeaking = false
            statusCallback?.invoke(isRecording, isSpeaking)
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Stoppen der Sprachausgabe", e)
        }
    }
    
    fun shutdown() {
        stopSpeaking()
        stopRecording()
        
        // Bereinige alle Ressourcen
        try {
            // Bereinige SpeechRecognizer
            cleanupRecognizer()
            
            // Bereinige TextToSpeech
            textToSpeech?.let { tts ->
                try {
                    if (tts.isSpeaking) {
                        tts.stop()
                    }
                    tts.shutdown()
                    textToSpeech = null
                    ttsInitialized = false
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler beim Herunterfahren von TextToSpeech", e)
                }
            }
            
            // MediaPlayer freigeben
            mediaPlayer?.let { player ->
                try {
                    if (player.isPlaying) {
                        player.stop()
                    }
                    player.release()
                    mediaPlayer = null
                } catch (e: Exception) {
                    Log.e(TAG, "Fehler beim Freigeben des MediaPlayers", e)
                }
            }
            
            // Status zurücksetzen
            isRecording = false
            isSpeaking = false
            statusCallback?.invoke(isRecording, isSpeaking)
            statusCallback = null
            
            Log.d(TAG, "SpeechManager erfolgreich heruntergefahren")
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Herunterfahren des SpeechManagers", e)
        }
    }
    
    private fun cleanupRecognizer() {
        try {
            speechRecognizer?.let { recognizer ->
                recognizer.cancel()
                recognizer.stopListening()
                recognizer.destroy()
                speechRecognizer = null
            }
            recognitionListener = null
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Aufräumen des Recognizers", e)
        }
    }

    fun setStatusCallback(callback: (Boolean, Boolean) -> Unit) {
        statusCallback = callback
    }
} 