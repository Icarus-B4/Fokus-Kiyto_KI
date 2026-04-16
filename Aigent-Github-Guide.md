# 🔄 Entwicklungsworkflow-Guide für KiytoApp

## 📁 Repository-Struktur

- **V1 (Private)**: `https://github.com/Icarus-B4/KiytoApp`
  - Entwicklungsumgebung
  - Privates Testing
  - Feature-Entwicklung

- **V2 (Public)**: `https://github.com/Icarus-B4/Fokus-Kiyto_KI`
  - Production-Ready Code
  - Play Store Version
  - Öffentliche Dokumentation

## 🛠️ Repository Setup

```bash
# Remote-Verbindungen einrichten
git remote add v1 https://github.com/Icarus-B4/KiytoApp.git
git remote add v2 https://github.com/Icarus-B4/Fokus-Kiyto_KI.git
```

## 👨‍💻 Entwicklungsprozess

### In V1 (Private) entwickeln:
```bash
git checkout main          # Hauptbranch in V1
git pull v1 main          # Aktuellen Stand holen
# Entwickeln und Testen
git add .
git commit -m "Feature: Beschreibung"
git push v1 main          # Nach V1 pushen
```

### Nach V2 (Public) übertragen:
```bash
git checkout v2-main      # Branch für V2
git merge main            # V1 Changes übernehmen
git push v2 main         # Nach V2 (public) pushen
```

## 📋 Best Practices

### Feature-Entwicklung:
```bash
git checkout -b feature/neue-funktion
# Entwicklung durchführen
git merge feature/neue-funktion
```

### Releases:
```bash
# In V2 vor Play Store Release
git tag -a v1.0.0 -m "Version 1.0.0"
git push v2 --tags
```

## ⚠️ Wichtige Regeln

1. **Qualitätskontrolle**:
   - Code muss in V1 getestet sein
   - Keine Fehler vorhanden
   - Dokumentation aktualisiert

2. **Sicherheit**:
   - Sensitive Daten prüfen
   - Environment Variables nutzen
   - API-Keys schützen

3. **Dokumentation**:
   - Changelog pflegen
   - Features dokumentieren
   - README aktualisieren

## 🔍 Vor dem Push nach V2 prüfen:

- [ ] Alle Tests erfolgreich
- [ ] Keine sensiblen Daten im Code
- [ ] Dokumentation aktualisiert
- [ ] Version aktualisiert
- [ ] Changelog gepflegt

## 📱 Play Store Release

1. Code in V2 pushen
2. Version taggen
3. Release Notes erstellen
4. App Bundle generieren
5. In Play Console hochladen

## 🤖 Cursor AI Anweisungen

- Ordnerstrukturen nicht verändern
- PowerShell-Befehle verwenden
- build.gradle.kts nur auf explizite Anweisung ändern
- Alle Kommunikation auf Deutsch
- Best Practices für Android/Kotlin befolgen

## 📞 Support

Bei Fragen oder Problemen:
- Email: cupparikun@gmail.com
- GitHub Issues erstellen

---
Zuletzt aktualisiert: [DATUM] 