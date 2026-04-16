# KiytoApp - Versionsübersicht

## Aktuelle Version
- **Version**: 1.0
- **Versionscode**: 1

## Überblick
Die KiytoApp ist eine Produktivitäts-App, die Benutzern hilft, sich auf eine Aufgabe gleichzeitig zu konzentrieren. Sie bietet eine benutzerfreundliche Oberfläche zur Aufgabenverwaltung, Fortschrittsverfolgung und Minimierung von Ablenkungen.

## Sprachbefehle

### Unterstützte Befehle
- **Timer-Befehle**: 
  - "Timer für X Minuten"
  - "Starte einen Timer für X Minuten"
  - "Stelle einen Timer auf X Minuten"
  - "Fokus für X Minuten"
  - "Pomodoro starten"

- **Aufgaben-Befehle**:
  - "Erstelle eine Aufgabe: [Titel]"
  - "Neue Aufgabe: [Titel]"
  - "Task hinzufügen: [Titel]"
  - "Notiere: [Titel]"

- **Kalender-Befehle**:
  - "Öffne Kalender"
  - "Öffne Kalender mit Termin [Titel]"

- **Musik-Befehle**:
  - "Öffne Spotify"
  - "Spiele Musik"
  - "Spiele Playlist [Name]"

- **Chat-Befehle**:
  - "Lösche Chat"
  - "Chat löschen"
  - "Clear Chat"

- **Spracheingabe-Befehle**:
  - "Sprich mit mir"
  - "Stimme aktivieren"

### KI-Funktionalität
- Integrierter KI-Assistent mit Spracherkennung und -ausgabe
- Unterstützung für deutsche Sprachbefehle und Antworten
- Kurze, prägnante Antworten (max. 2-3 Sätze pro Antwort)
- Tägliche Inspirationszitate per Sprachausgabe
- Aufgabenanalyse und KI-gestützte Empfehlungen
- Bildgenerierung für visuelle Darstellungen

## Einstellungsmöglichkeiten

### Benachrichtigungen
- Anpassbare Benachrichtigungstöne
- Zeitplaneinstellungen für Erinnerungen (Start- und Endzeit)
- Hintergrundbildanpassung für Benachrichtigungen

### API-Einstellungen
- Konfiguration des OpenAI API-Keys
- Option zum Zurücksetzen auf den Standard-API-Key
- Validierung des API-Key-Formats

### Themeneinstellungen
- Anpassung des App-Erscheinungsbilds (in Entwicklung)

### Debug-Einstellungen
- Debug-Logs anzeigen (nur im Debug-Modus)
- Testbenachrichtigungen senden
- Einstellungen zurücksetzen

## OpenAI API-Key

### Integration
- Sichere Speicherung des API-Keys mit EncryptedSharedPreferences
- Unterstützung für benutzerdefinierte und Standard-API-Keys
- Automatische Validierung des API-Key-Formats (Mindestlänge 32 Zeichen)
- Prüfung auf gültige Präfixe (sk-, sk-proj-, sk_test_)

### Verwendete Modelle
- GPT-4o-mini für Konversationen und Aufgabenanalyse
- Whisper-1 für Spracherkennung (16kHz WAV-Format)
- TTS-1-HD mit Nova-Stimme für Sprachausgabe
- DALL-E für Bildgenerierung (1024x1024 Auflösung)

### Funktionen
- Spracherkennung und -transkription
- Text-zu-Sprache-Konvertierung
- Bildgenerierung basierend auf Textbeschreibungen
- Aufgabenanalyse und Empfehlungen
- Tägliche Inspirationszitate mit KI-Kommentaren

## Zukünftige Erweiterungen
- Integration mit Kalender-Apps
- Team-Funktionen für gemeinsame Aufgabenbearbeitung
- Erweiterte KI-gestützte Empfehlungen
- Verbesserte Spracherkennung und -ausgabe 