package com.deepcore.kiytoapp.data

import java.util.Calendar
import java.util.Random

data class Quote(
    val text: String,
    val author: String,
    val category: QuoteCategory,
    var aiComment: String? = null
)

object QuoteDatabase {
    private val quotes = listOf(
        // Produktivität
        Quote(
            "Der beste Weg, die Zukunft vorherzusagen, ist sie zu gestalten.",
            "Peter Drucker",
            QuoteCategory.PRODUCTIVITY
        ),
        Quote(
            "Erfolg ist die Summe kleiner Anstrengungen, die Tag für Tag wiederholt werden.",
            "Robert Collier",
            QuoteCategory.PRODUCTIVITY
        ),
        Quote(
            "Die Qualität deines Lebens wird von der Qualität deiner Gewohnheiten bestimmt.",
            "James Clear",
            QuoteCategory.PRODUCTIVITY
        ),
        Quote(
            "Das Geheimnis des Erfolgs ist, den Punkt zu finden, wo man Spaß an dem hat, was man tut.",
            "Albert Einstein",
            QuoteCategory.PRODUCTIVITY
        ),

        // Motivation
        Quote(
            "Es ist nicht wichtig, besser zu sein als andere. Es ist wichtig, besser zu sein als gestern.",
            "Jigoro Kano",
            QuoteCategory.MOTIVATION
        ),
        Quote(
            "Der beste Zeitpunkt, einen Baum zu pflanzen, war vor zwanzig Jahren. Der zweitbeste Zeitpunkt ist jetzt.",
            "Chinesisches Sprichwort",
            QuoteCategory.MOTIVATION
        ),
        Quote(
            "Große Leistungen entstehen nicht durch Kraft, sondern durch Beharrlichkeit.",
            "Samuel Johnson",
            QuoteCategory.MOTIVATION
        ),
        Quote(
            "Wer aufhört, besser werden zu wollen, hört auf, gut zu sein.",
            "Marie von Ebner-Eschenbach",
            QuoteCategory.MOTIVATION
        ),

        // Zeitmanagement
        Quote(
            "Der Schlüssel ist nicht die Priorisierung dessen, was auf deiner Zeitplanung steht, sondern die Planung deiner Prioritäten.",
            "Stephen Covey",
            QuoteCategory.TIME_MANAGEMENT
        ),
        Quote(
            "Die Zeit, die wir uns für etwas nehmen, zeigt, wie wichtig es uns ist.",
            "Johann Wolfgang von Goethe",
            QuoteCategory.TIME_MANAGEMENT
        ),
        Quote(
            "Jede Minute, die du in die Planung investierst, spart dir zehn Minuten bei der Ausführung.",
            "Brian Tracy",
            QuoteCategory.TIME_MANAGEMENT
        ),
        Quote(
            "Zeit ist keine Ressource, die man sparen kann, sondern eine, die man klug investieren muss.",
            "Peter F. Drucker",
            QuoteCategory.TIME_MANAGEMENT
        ),

        // Fokus
        Quote(
            "Konzentriere dich auf die kleinen Verbesserungen und die großen Erfolge werden von selbst kommen.",
            "James Clear",
            QuoteCategory.FOCUS
        ),
        Quote(
            "Fokussiere dich auf den Prozess, nicht auf das Ergebnis.",
            "John Wooden",
            QuoteCategory.FOCUS
        ),
        Quote(
            "Zeit ist keine Geschwindigkeit, sondern eine Tiefe.",
            "Rainer Maria Rilke",
            QuoteCategory.FOCUS
        ),
        Quote(
            "Die Kunst der Weisheit liegt darin zu wissen, was man übersehen muss.",
            "William James",
            QuoteCategory.FOCUS
        ),

        // Erfolg
        Quote(
            "Der Weg zum Erfolg ist die Summe kleiner, wiederholter Handlungen.",
            "Robert Collier",
            QuoteCategory.SUCCESS
        ),
        Quote(
            "Erfolg hat nur, wer etwas tut, während er auf den Erfolg wartet.",
            "Thomas Alva Edison",
            QuoteCategory.SUCCESS
        ),
        Quote(
            "Der einzige Ort, an dem Erfolg vor Arbeit kommt, ist im Wörterbuch.",
            "Vidal Sassoon",
            QuoteCategory.SUCCESS
        ),

        // Achtsamkeit
        Quote(
            "Das Leben ist ein Echo. Was du aussendest, kommt zu dir zurück.",
            "Zig Ziglar",
            QuoteCategory.MINDFULNESS
        ),
        Quote(
            "Der gegenwärtige Moment ist der einzige Moment, in dem das Leben verfügbar ist.",
            "Thich Nhat Hanh",
            QuoteCategory.MINDFULNESS
        ),
        Quote(
            "Achtsamkeit bedeutet, jeden Augenblick als neu und einzigartig zu betrachten.",
            "Jon Kabat-Zinn",
            QuoteCategory.MINDFULNESS
        )
    )

    fun getRandomQuote(): Quote {
        return quotes.random()
    }

    fun getQuoteByCategory(category: QuoteCategory): Quote {
        val categoryQuotes = quotes.filter { it.category == category }
        return if (categoryQuotes.isNotEmpty()) {
            categoryQuotes.random()
        } else {
            getRandomQuote()
        }
    }

    fun getDailyQuote(): Quote {
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val seed = dayOfYear.toLong()
        
        val random = Random(seed)
        return quotes[random.nextInt(quotes.size)]
    }

    fun getDailyQuoteByCategory(category: QuoteCategory): Quote {
        val categoryQuotes = quotes.filter { it.category == category }
        if (categoryQuotes.isEmpty()) return getDailyQuote()
        
        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        val seed = dayOfYear.toLong() + category.name.hashCode()
        
        val random = Random(seed)
        return categoryQuotes[random.nextInt(categoryQuotes.size)]
    }
}