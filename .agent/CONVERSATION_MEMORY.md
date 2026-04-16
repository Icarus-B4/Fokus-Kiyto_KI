# CONVERSATION_MEMORY - Fokus-Kiyto_KI

## 2026-04-16: Optimierung der Spracheingabe (STT) & Fehlerbehebung
- **Empfindlichkeit (Threshold):** Schwellenwert in `SpeechManager.kt` von 500 auf **200** gesenkt, um leisere Stimmen besser zu erfassen.
- **Sprechpausen (Timeout):** `SPEECH_TIMEOUT` auf **3.0 Sekunden** erhÃ¶ht, um natÃ¼rliche Pausen beim Sprechen zu ermÃ¶glichen ohne die Aufnahme abzubrechen.
- **Kontinuierliche Aufnahme:** Die Logik wurde von fragmentierter Speicherung (nur bei LautstÃ¤rke) auf **kontinuierlichen Audiostrom** umgestellt. Dies sorgt fÃ¼r eine deutlich hÃ¶here Erkennungsrate durch die Gemini API.
- **Feedback & UI:** Die Toast-Meldung bei fehlender Erkennung wurde in `AIChatFragment.kt` hilfreicher gestaltet ("Ich habe dich leider nicht verstanden...").
- **Cleanup:** Unbenutzte Konstante `RECORDING_DURATION_MS` entfernt.

*Status: Spracheingabe ist nun deutlich sensibler und zuverlÃ¤ssiger; Gemini-Transkription durch kontinuierliche Audio-Files optimiert.*

## 2026-04-16: Umfassende Stabilisierung & Chat-Optimierung

### 1. Update-System & Build-Automation
- **GitHub Actions:** Der `GITHUB_TOKEN` wurde in den `release.yml` Workflow injiziert, um der App den Zugriff auf die GitHub API fÃ¼r Update-Checks zu ermÃ¶glichen.
- **Versionierung:** Die App-Version wurde systemweit auf `2.0.95` (und spÃ¤ter `2.0.96` fÃ¼r Tests) angehoben, um Konsistenz mit den GitHub Releases zu gewÃ¤hrleisten.
- **UpdateManager:** 
    - Token-Handling wurde optional gestaltet (Fallback auf anonyme Anfragen bei Rate-Limits).
    - Metadaten (latestVersion, description, url) werden nun in den `SharedPreferences` persistiert, damit sie nicht beim SchlieÃŸen der App verloren gehen.
    - Das Debug-Log in der App wurde um Fehlermeldungen erweitert, um GitHub-Sperren (403) sichtbar zu machen.

### 2. Login- & Daten-Robustheit (Keystore-Schutz)
- **Problem:** Nach einem JKS-Key-Wechsel konnten `EncryptedSharedPreferences` nicht mehr auf alte Daten zugreifen (Absturz).
- **LÃ¶sung:** In `AuthManager.kt` und `SessionManager.kt` wurden `try-catch`-BlÃ¶cke um die Initialisierung implementiert. Bei einem Keystore-Fehler wird der Speicher sicher zurÃ¼ckgesetzt, statt die App zum Absturz zu bringen.

### 3. KI-Chat & Sprachfunktionen (Fixes)
- **Mikrofon:** Eine doppelte Initialisierung im `AIChatFragment` wurde entfernt, die den `SpeechManager` blockierte. Die Spracherkennung ist nun aktiv.
- **Quick-Chat Buttons:** Die Suggestion-Chips im Layout (`fragment_ai_chat.xml`) wurden mit IDs versehen und `checkable` gemacht. Der Klick-Listener im Code reagiert nun korrekt und sendet die Befehle an den Assistenten.
- **Feedback:** Es wurden Toasts hinzugefÃ¼gt, die dem Nutzer RÃ¼ckmeldung geben, wenn keine Sprache erkannt wurde.

### 4. Projekt-Struktur & Wartung
- **.agent Verzeichnis:** Die verschachtelte Struktur (`.agent/.agent/`) wurde bereinigt. Alle Memory- und Skill-Dateien liegen nun korrekt im Stammverzeichnis unter `.agent/`.

---
*Status: App ist stabil (v2.0.95+), Chat & Voice funktionieren, Update-Persistenz ist aktiv.*

### [2026-04-16 20:21:12] Radikale OpenAI-Entfernung & Gemini-Konsolidierung
- OpenAI wurde vollständig aus dem Projekt entfernt (SDK, Code, UI).
- Alle KI-Dienste (Chat, Audio, Video, Files) laufen nun exklusiv über Gemini.
- GeminiService wurde um REST-Transkription und Video-Analysis erweitert.
- Voice-System nutzt jetzt nur noch lokale TTS (weiblich) und Gemini.
- Projekt-Status: Bereinigt, Schlanker und auf Gemini-First-Architektur umgestellt.


### 2026-04-16 - Gemini-Migration & v2.1.4 Build
- **Status**: Migration abgeschlossen, Build erfolgreich, GitHub-Upload fehlgeschlagen (401).
- **Änderungen**:
  - OpenAI radikal aus dem Projekt entfernt (Gradle, Services, UI).
  - Alle AI-Aufgaben auf Gemini 1.5 Flash konsolidiert.
  - Kompilierfehler (Singleton-Zugriff, Signatur-Fehler, doppelte Methoden) behoben.
  - App-Version auf v2.1.4 aktualisiert.
  - APK erfolgreich lokal unter 'releases/Fokus-Kiyto-v2.1.4-debug.apk' erstellt.
- **Blocker**: GitHub-Token 'ghp_...' ungültig für automatisiertes Release.

### 2026-04-16 - Download-Fix & v2.1.5 Release
- **Status**: Update-System repariert, v2.1.5 erfolgreich veröffentlicht.
- **Änderungen**:
  - UpdateManager.kt: fetchLatestDownloadUrl speichert nun Daten in SharedPreferences.
  - UpdateSystemFragment.kt: Übergibt direkte Download-URL an den Dialog.
  - UpdateDownloadDialogFragment.kt: Priorisiert APK-Links vor HTML-Seiten; Variablen-Scope korrigiert.
  - GitHub-Upload: v2.1.5 mit neuem Token erfolgreich erstellt (ID: 310022167).
- **Ergebnis**: Der Download-Button "Herunterladen und Installieren" funktioniert nun wie erwartet und stösst den automatischen APK-Download an.

### 2026-04-16 - Pipeline-Fix & v2.1.8 Release
- **Status**: Versions-Inkonsistenz und Pipeline-Fehler endgültig behoben. v2.1.8 verffentlicht.
- **nderungen**:
  - build.gradle.kts: Version hartcodiert (versionName = "2.1.8"), um Parsing-Fehler zu vermeiden.
  - release-apk.ps1: Regex-Hrtung fr Versions-Ersetzung.
  - .github/workflows/release.yml: Korrektur der Extraktions-Logik (grep/sed) fr den GitHub-Build.
  - PackageInstallerHelper.kt: Optimierung fr Android 11+ Installationen & Berechtigungs-Checks.
- **Ergebnis**: v2.1.8 ist live. Die korrekte Versionsnummer wird nun sowohl lokal als auch im GitHub-Release korrekt angezeigt.

### 2026-04-16 - Hero-Update v2.1.9: Sprach- & Wake-Word Revolution
- **Status**: Erledigt. Fokus-Kiyto nutzt nun die moderne Gemini-Inference fr Sprache.
- **Wichtigste Features**:
  - **Wake-Word**: Umstellung auf robustes Regex-Matching fr "Hei Kiyto". Toleranter gegenüber verschiedenen Aussprachen.
  - **Moderne Stimme**: Integration der nativen multimodalen Gemini-Stimme (Aoede) via v1beta API.
  - **Transkription**: Przisierung der STT-Logik durch System-Prompts ("NUR Text ausgeben").
  - **Hybrid-Audio**: Automatisches Fallback auf lokale Android-TTS bei Offline-Status oder API-Fehlern.
- **Release**: v2.1.9 erfolgreich auf GitHub verffentlicht.

### 2026-04-16 - Diagnose-Update v2.1.10 (Fix 404 Probleme)
- **Status**: Erledigt. Diagnose-Tools fr API-Probleme implementiert.
- **Wichtigste Features**:
  - **API-Test-Tool**: Button "Verbindung testen" in den KI-Einstellungen hinzugefügt. Prft den Key via Ping an die v1beta API.
  - **Verbessertes Logging**: GeminiService loggt nun den vollstndigen Response-Body bei Fehlern (Code 404/401/429 etc.).
  - **Error Feedback**: Mikrofon-Toast zeigt nun przise Fehlercodes (z.B. "FEHLER: API-Fehler 404").
  - **Build**: v2.1.10 erfolgreich auf GitHub hochgeladen.
- **Ziel**: Identifikation der Ursache fr die massiven 404-Fehler in der Google Console.

### 2026-04-16 - Next-Gen Update v2.1.11: Gemini 3.1 Flash TTS
- **Status**: Erledigt. Modernstes TTS-Modell integriert.
- **Wichtigste Features**:
  - **Modell-Switch**: Sprachausgabe nutzt nun das spezialisierte 'gemini-3.1-flash-tts-preview' Modell.
  - **404 Fix**: Das spezialisierte Routing behebt die Inkompatibilitten des Standard-1.5 Modells bei Audio-Output.
  - **Dual-Model-System**: 1.5 Flash fr Text/STT + 3.1 Flash fr TTS.
  - **Build**: v2.1.11 erfolgreich verffentlicht.
