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
    private val context: Context,
    private val openAIService: OpenAIService
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
    
    companion object {
        private const val SAMPLE_RATE = 16000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_FACTOR = 2 // Größerer Buffer für bessere Performance
        private const val SPEECH_TIMEOUT = 1500L // 1.5 Sekunden Stille für automatisches Ende
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
            
            // Filtere nach deutschen Stimmen mit hoher Qualität
            val germanVoices = voices.filter { voice ->
                voice.locale.language == "de" &&
                voice.quality >= Voice.QUALITY_VERY_HIGH &&
                !voice.name.contains("network") // Vermeide Netzwerk-Stimmen für bessere Performance
            }
            
            // Wähle die beste verfügbare Stimme
            val bestVoice = germanVoices
                .maxByOrNull { voice -> 
                    voice.quality + (if (voice.name.contains("female")) 10 else 0) // Bevorzuge weibliche Stimmen
                }
                ?: voices.firstOrNull { it.locale.language == "de" }
            
            bestVoice?.let {
                if (currentVoice?.name != it.name) {
                    textToSpeech?.voice = it
                    currentVoice = it
                    Log.d(TAG, "Neue Stimme ausgewählt: ${it.name}")
                }
            }
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
                // Warte bis die Sprachausgabe beendet ist
                while (isSpeaking) {
                    delay(100)
                }
                
                // Kurze Pause nach der Sprachausgabe
                delay(500)
                
                isRecording = true
                statusCallback?.invoke(isRecording, isSpeaking)
                
                val audioFile = recordAudio()
                audioFile?.let { file ->
                    val transcription = openAIService.transcribeAudio(file)
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
            
            val outputFile = File(context.cacheDir, "audio_recording.wav")
            val outputStream = ByteArrayOutputStream()
            
            try {
                val audioData = ByteArray(bufferSize)
                audioRecord.startRecording()
                
                var silenceStartTime = System.currentTimeMillis()
                var lastSoundTime = silenceStartTime
                
                while (isRecording) {
                    val readBytes = audioRecord.read(audioData, 0, bufferSize)
                    
                    if (readBytes > 0) {
                        var hasSound = false
                        
                        // Prüfe auf Geräusche
                        for (i in 0 until readBytes step 2) {
                            val sample = (audioData[i + 1].toInt() shl 8) or (audioData[i].toInt() and 0xFF)
                            if (Math.abs(sample) > 1000) { // Erhöhter Schwellenwert
                                hasSound = true
                                lastSoundTime = System.currentTimeMillis()
                                break
                            }
                        }
                        
                        // Schreibe nur wenn Geräusche erkannt wurden
                        if (hasSound) {
                            outputStream.write(audioData, 0, readBytes)
                        }
                        
                        // Prüfe auf Stille
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastSoundTime > SPEECH_TIMEOUT) {
                            Log.d(TAG, "Spracherkennung durch Stille beendet")
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
            
            val audioData = openAIService.textToSpeech(text) ?: return
            
            val tempFile = File(context.cacheDir, "tts_output.mp3")
            tempFile.writeBytes(audioData)
            
            withContext(Dispatchers.Main) {
                mediaPlayer?.release()
                mediaPlayer = null
                
                mediaPlayer = android.media.MediaPlayer().apply {
                    setDataSource(tempFile.path)
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
                        tempFile.delete()
                        isSpeaking = false
                        statusCallback?.invoke(isRecording, isSpeaking)
                    }
                    
                    setOnErrorListener { mp, what, extra ->
                        Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                        release()
                        mediaPlayer = null
                        tempFile.delete()
                        isSpeaking = false
                        statusCallback?.invoke(isRecording, isSpeaking)
                        true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Sprachausgabe", e)
            mediaPlayer?.release()
            mediaPlayer = null
            isSpeaking = false
            statusCallback?.invoke(isRecording, isSpeaking)
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