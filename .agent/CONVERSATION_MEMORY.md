# CONVERSATION_MEMORY - Fokus-Kiyto_KI

## 2026-04-16: Optimierung der Spracheingabe (STT) & Fehlerbehebung
- **Empfindlichkeit (Threshold):** Schwellenwert in `SpeechManager.kt` von 500 auf **200** gesenkt, um leisere Stimmen besser zu erfassen.
- **Sprechpausen (Timeout):** `SPEECH_TIMEOUT` auf **3.0 Sekunden** erhöht, um natürliche Pausen beim Sprechen zu ermöglichen ohne die Aufnahme abzubrechen.
- **Kontinuierliche Aufnahme:** Die Logik wurde von fragmentierter Speicherung (nur bei Lautstärke) auf **kontinuierlichen Audiostrom** umgestellt. Dies sorgt für eine deutlich höhere Erkennungsrate durch die Gemini API.
- **Feedback & UI:** Die Toast-Meldung bei fehlender Erkennung wurde in `AIChatFragment.kt` hilfreicher gestaltet ("Ich habe dich leider nicht verstanden...").
- **Cleanup:** Unbenutzte Konstante `RECORDING_DURATION_MS` entfernt.

*Status: Spracheingabe ist nun deutlich sensibler und zuverlässiger; Gemini-Transkription durch kontinuierliche Audio-Files optimiert.*

## 2026-04-16: Umfassende Stabilisierung & Chat-Optimierung

### 1. Update-System & Build-Automation
- **GitHub Actions:** Der `GITHUB_TOKEN` wurde in den `release.yml` Workflow injiziert, um der App den Zugriff auf die GitHub API für Update-Checks zu ermöglichen.
- **Versionierung:** Die App-Version wurde systemweit auf `2.0.95` (und später `2.0.96` für Tests) angehoben, um Konsistenz mit den GitHub Releases zu gewährleisten.
- **UpdateManager:** 
    - Token-Handling wurde optional gestaltet (Fallback auf anonyme Anfragen bei Rate-Limits).
    - Metadaten (latestVersion, description, url) werden nun in den `SharedPreferences` persistiert, damit sie nicht beim Schließen der App verloren gehen.
    - Das Debug-Log in der App wurde um Fehlermeldungen erweitert, um GitHub-Sperren (403) sichtbar zu machen.

### 2. Login- & Daten-Robustheit (Keystore-Schutz)
- **Problem:** Nach einem JKS-Key-Wechsel konnten `EncryptedSharedPreferences` nicht mehr auf alte Daten zugreifen (Absturz).
- **Lösung:** In `AuthManager.kt` und `SessionManager.kt` wurden `try-catch`-Blöcke um die Initialisierung implementiert. Bei einem Keystore-Fehler wird der Speicher sicher zurückgesetzt, statt die App zum Absturz zu bringen.

### 3. KI-Chat & Sprachfunktionen (Fixes)
- **Mikrofon:** Eine doppelte Initialisierung im `AIChatFragment` wurde entfernt, die den `SpeechManager` blockierte. Die Spracherkennung ist nun aktiv.
- **Quick-Chat Buttons:** Die Suggestion-Chips im Layout (`fragment_ai_chat.xml`) wurden mit IDs versehen und `checkable` gemacht. Der Klick-Listener im Code reagiert nun korrekt und sendet die Befehle an den Assistenten.
- **Feedback:** Es wurden Toasts hinzugefügt, die dem Nutzer Rückmeldung geben, wenn keine Sprache erkannt wurde.

### 4. Projekt-Struktur & Wartung
- **.agent Verzeichnis:** Die verschachtelte Struktur (`.agent/.agent/`) wurde bereinigt. Alle Memory- und Skill-Dateien liegen nun korrekt im Stammverzeichnis unter `.agent/`.

---
*Status: App ist stabil (v2.0.95+), Chat & Voice funktionieren, Update-Persistenz ist aktiv.*

### [2026-04-16 20:21:12] Radikale OpenAI-Entfernung & Gemini-Konsolidierung
- OpenAI wurde vollst�ndig aus dem Projekt entfernt (SDK, Code, UI).
- Alle KI-Dienste (Chat, Audio, Video, Files) laufen nun exklusiv �ber Gemini.
- GeminiService wurde um REST-Transkription und Video-Analysis erweitert.
- Voice-System nutzt jetzt nur noch lokale TTS (weiblich) und Gemini.
- Projekt-Status: Bereinigt, Schlanker und auf Gemini-First-Architektur umgestellt.


### 2026-04-16 - Gemini-Migration & v2.1.4 Build
- **Status**: Migration abgeschlossen, Build erfolgreich, GitHub-Upload fehlgeschlagen (401).
- **�nderungen**:
  - OpenAI radikal aus dem Projekt entfernt (Gradle, Services, UI).
  - Alle AI-Aufgaben auf Gemini 1.5 Flash konsolidiert.
  - Kompilierfehler (Singleton-Zugriff, Signatur-Fehler, doppelte Methoden) behoben.
  - App-Version auf v2.1.4 aktualisiert.
  - APK erfolgreich lokal unter 'releases/Fokus-Kiyto-v2.1.4-debug.apk' erstellt.
- **Blocker**: GitHub-Token 'ghp_...' ung�ltig f�r automatisiertes Release.
