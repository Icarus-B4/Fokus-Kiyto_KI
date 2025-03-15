package com.deepcore.kiytoapp.ai

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.deepcore.kiytoapp.data.Quote
import com.deepcore.kiytoapp.data.QuoteCategory
import com.deepcore.kiytoapp.data.QuoteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

class DailyInspirationManager(private val context: Context) {
    private var textToSpeech: TextToSpeech? = null
    private var isTtsReady = false
    private val openAIService = OpenAIService
    private val TAG = "DailyInspirationManager"
    private val prefs: SharedPreferences

    init {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        prefs = EncryptedSharedPreferences.create(
            "daily_inspiration_prefs",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        initTextToSpeech()
    }

    private fun initTextToSpeech() {
        Log.d(TAG, "Initialisiere TextToSpeech")
        try {
            textToSpeech = TextToSpeech(context) { status ->
                isTtsReady = status == TextToSpeech.SUCCESS
                if (isTtsReady) {
                    textToSpeech?.let { tts ->
                        tts.language = Locale.GERMAN
                        tts.setSpeechRate(0.8f)  // Etwas langsamer für bessere Verständlichkeit
                        Log.d(TAG, "TextToSpeech erfolgreich initialisiert")
                    }
                } else {
                    Log.e(TAG, "TextToSpeech Initialisierung fehlgeschlagen mit Status: $status")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der TextToSpeech-Initialisierung", e)
            isTtsReady = false
        }
    }

    suspend fun getDailyQuote(category: QuoteCategory? = null): Quote {
        return withContext(Dispatchers.IO) {
            try {
                val savedQuote = getSavedQuote()
                if (savedQuote != null && isQuoteFromToday(savedQuote)) {
                    savedQuote
                } else {
                    val newQuote = if (category != null) {
                        QuoteDatabase.getDailyQuoteByCategory(category)
                    } else {
                        QuoteDatabase.getDailyQuote()
                    }
                    saveQuote(newQuote)
                    newQuote
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fehler beim Laden des Zitats", e)
                // Fallback-Zitat
                Quote(
                    text = "Der frühe Vogel fängt den Wurm.",
                    author = "Sprichwort",
                    category = QuoteCategory.PRODUCTIVITY
                )
            }
        }
    }

    private fun getSavedQuote(): Quote? {
        val text = prefs.getString("quote_text", null) ?: return null
        val author = prefs.getString("quote_author", null) ?: return null
        val category = try {
            QuoteCategory.fromString(prefs.getString("quote_category", null) ?: return null)
        } catch (e: Exception) {
            return null
        }
        val aiComment = prefs.getString("quote_ai_comment", null)
        
        return Quote(text, author, category, aiComment)
    }

    private fun saveQuote(quote: Quote) {
        prefs.edit()
            .putString("quote_text", quote.text)
            .putString("quote_author", quote.author)
            .putString("quote_category", quote.category.name)
            .putString("quote_ai_comment", quote.aiComment)
            .putLong("quote_timestamp", System.currentTimeMillis())
            .apply()
    }

    private fun isQuoteFromToday(quote: Quote): Boolean {
        val savedTimestamp = prefs.getLong("quote_timestamp", 0)
        if (savedTimestamp == 0L) return false
        
        val savedCalendar = Calendar.getInstance().apply { timeInMillis = savedTimestamp }
        val currentCalendar = Calendar.getInstance()
        
        return savedCalendar.get(Calendar.YEAR) == currentCalendar.get(Calendar.YEAR) &&
               savedCalendar.get(Calendar.DAY_OF_YEAR) == currentCalendar.get(Calendar.DAY_OF_YEAR)
    }

    suspend fun getAIComment(quote: Quote): String {
        // Lösche den alten Kommentar
        quote.aiComment = null
        saveQuote(quote.copy(aiComment = null))
        
        return try {
            // Generiere einen neuen Kommentar
            val aiComment = openAIService.generateQuoteComment(quote)
            quote.aiComment = aiComment
            saveQuote(quote)
            aiComment
        } catch (e: Exception) {
            Log.e(TAG, "Fehler beim Generieren des KI-Kommentars", e)
            "Ein inspirierender Gedanke für mehr Erfolg."
        }
    }

    fun speakQuote(text: String) {
        Log.d(TAG, "Versuche Text vorzulesen: $text")
        try {
            if (!isTtsReady) {
                Log.e(TAG, "TextToSpeech ist nicht bereit")
                // Versuche eine Neuinitialisierung
                initTextToSpeech()
                return
            }

            textToSpeech?.let { tts ->
                // Stoppe vorherige Ausgabe
                tts.stop()
                
                // Teile den Text in Absätze
                val paragraphs = text.split("\n\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                
                Log.d(TAG, "Spreche ${paragraphs.size} Absätze")
                
                // Sprich jeden Absatz mit einer längeren Pause dazwischen
                paragraphs.forEachIndexed { index, paragraph ->
                    // Sprich den Absatz
                    tts.speak(
                        paragraph,
                        if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                        null,
                        "QUOTE_PARAGRAPH_$index"
                    )
                    
                    // Füge eine längere Pause zwischen den Absätzen ein
                    if (index < paragraphs.size - 1) {
                        tts.playSilentUtterance(1000, TextToSpeech.QUEUE_ADD, null)
                    }
                }
            } ?: run {
                Log.e(TAG, "TextToSpeech-Instanz ist null")
                // Versuche eine Neuinitialisierung
                initTextToSpeech()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fehler bei der Sprachausgabe", e)
        }
    }

    fun shutdown() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        Log.d(TAG, "TextToSpeech heruntergefahren")
    }
} 