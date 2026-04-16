package com.deepcore.kiytoapp.data

enum class QuoteCategory(val displayName: String) {
    PRODUCTIVITY("Produktivität"),
    MOTIVATION("Motivation"),
    TIME_MANAGEMENT("Zeitmanagement"),
    FOCUS("Fokus"),
    SUCCESS("Erfolg"),
    MINDFULNESS("Achtsamkeit"),
    GROWTH("Persönliche Entwicklung"),
    LEADERSHIP("Führung"),
    CREATIVITY("Kreativität"),
    RESILIENCE("Resilienz");

    companion object {
        fun fromString(value: String): QuoteCategory {
            return values().find { it.name == value.uppercase() }
                ?: throw IllegalArgumentException("Ungültige Kategorie: $value")
        }
    }
} 