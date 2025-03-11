package com.deepcore.kiytoapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.deepcore.kiytoapp.ai.DailyInspirationManager
import com.deepcore.kiytoapp.data.QuoteCategory
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class DailyQuoteFragment : Fragment() {
    private lateinit var quoteText: TextView
    private lateinit var quoteAuthor: TextView
    private lateinit var aiCommentText: TextView
    private lateinit var speakButton: MaterialButton
    private lateinit var shareButton: MaterialButton
    private lateinit var categoryButton: MaterialButton
    private lateinit var inspirationManager: DailyInspirationManager
    private var currentQuoteText: String = ""
    private var selectedCategory: QuoteCategory? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.daily_quote_card, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        inspirationManager = DailyInspirationManager(requireContext())
        
        initializeViews(view)
        setupButtons()
        loadDailyQuote()
    }

    private fun initializeViews(view: View) {
        quoteText = view.findViewById(R.id.quoteText)
        quoteAuthor = view.findViewById(R.id.quoteAuthor)
        aiCommentText = view.findViewById(R.id.aiCommentText)
        shareButton = view.findViewById(R.id.shareButton)
        categoryButton = view.findViewById(R.id.categoryButton)
    }

    private fun setupButtons() {
        speakButton.setOnClickListener {
            val textToSpeak = buildString {
                append(quoteText.text)
                append(". Von ")
                append(quoteAuthor.text.toString().removePrefix("- "))
                append(". ")
                append(aiCommentText.text)
            }
            inspirationManager.speakQuote(textToSpeak)
        }

        shareButton.setOnClickListener {
            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, currentQuoteText)
            }
            startActivity(Intent.createChooser(shareIntent, "Zitat teilen"))
        }

        categoryButton.setOnClickListener {
            showCategoryDialog()
        }
    }

    private fun showCategoryDialog() {
        val categories = QuoteCategory.values()
        val categoryNames = categories.map { it.displayName }.toTypedArray()
        
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Kategorie wählen")
            .setSingleChoiceItems(
                categoryNames,
                categories.indexOf(selectedCategory)
            ) { dialog, which ->
                selectedCategory = categories[which]
                loadDailyQuote()
                dialog.dismiss()
            }
            .setNegativeButton("Alle Kategorien") { dialog, _ ->
                selectedCategory = null
                loadDailyQuote()
                dialog.dismiss()
            }
            .show()
    }

    private fun loadDailyQuote() {
        lifecycleScope.launch {
            try {
                // Lade initial ein Fallback-Zitat
                quoteText.text = "Lade tägliche Inspiration..."
                quoteAuthor.text = ""
                aiCommentText.text = "Einen Moment bitte..."

                // Aktualisiere den Kategorie-Button
                categoryButton.text = selectedCategory?.displayName ?: "Kategorie"

                // Lade das Zitat und den KI-Kommentar
                val quote = inspirationManager.getDailyQuote(selectedCategory)
                val aiComment = inspirationManager.getAIComment(quote)
                
                // UI aktualisieren
                quoteText.text = quote.text
                quoteAuthor.text = "- ${quote.author}"
                aiCommentText.text = aiComment
                
                // Für Share-Funktion speichern
                currentQuoteText = buildString {
                    append(quote.text)
                    append("\n- ")
                    append(quote.author)
                    append("\n\nKategorie: ")
                    append(quote.category.displayName)
                    append("\n\nKI-Kommentar:\n")
                    append(aiComment)
                    append("\n\nGeteilt von KiytoApp")
                }
                
            } catch (e: Exception) {
                Log.e("DailyQuoteFragment", "Fehler beim Laden des Zitats", e)
                quoteText.text = "Der frühe Vogel fängt den Wurm."
                quoteAuthor.text = "- Sprichwort"
                aiCommentText.text = "Ein zeitloser Rat für mehr Produktivität."
                
                // Für Share-Funktion speichern
                currentQuoteText = buildString {
                    append(quoteText.text)
                    append("\n")
                    append(quoteAuthor.text)
                    append("\n\nKI-Kommentar:\n")
                    append(aiCommentText.text)
                    append("\n\nGeteilt von KiytoApp")
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        inspirationManager.shutdown()
    }
} 