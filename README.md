# 🚀 Fokus-Kiyto KI

<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png" alt="Fokus-Kiyto Logo" width="120"/>
  <br>
  <h3>Eine KI-gestützte Produktivitäts-App für Android</h3>
</div>

## 📱 Über die App

Fokus-Kiyto KI ist eine moderne Produktivitäts-App, die fortschrittliche KI-Technologien nutzt, um deine Arbeitsweise zu optimieren. Die App kombiniert Aufgabenverwaltung, Zeitmanagement und KI-gestützte Empfehlungen, um dir zu helfen, fokussierter und produktiver zu arbeiten.

### ✨ Hauptfunktionen

- **📋 Intelligente Aufgabenverwaltung**: Organisiere deine Aufgaben mit Prioritäten, Kategorien und Deadlines
- **⏱️ Pomodoro-Timer**: Verbessere deinen Fokus mit anpassbaren Arbeits- und Pausenzyklen
- **🤖 KI-Empfehlungen**: Erhalte personalisierte Vorschläge zur Optimierung deiner Produktivität
- **📊 Produktivitätsanalyse**: Verfolge deine Fortschritte und identifiziere Verbesserungspotenziale
- **🔔 Intelligente Benachrichtigungen**: Werde zur richtigen Zeit an wichtige Aufgaben erinnert
- **🌙 Dunkelmodus**: Schone deine Augen bei der Arbeit in dunkleren Umgebungen

## 🛠️ Technologien

- **Kotlin**: Moderne, sichere und ausdrucksstarke Programmiersprache für Android
- **MVVM-Architektur**: Saubere Trennung von UI, Geschäftslogik und Daten
- **Jetpack-Komponenten**: LiveData, ViewModel, Room, Navigation
- **Coroutines**: Asynchrone Programmierung für reibungslose Benutzererfahrung
- **Material Design 3**: Moderne und ansprechende Benutzeroberfläche
- **KI-Integration**: Fortschrittliche Algorithmen für personalisierte Empfehlungen V2

## 📸 Screenshots

<div align="center">
  <table>
    <tr>
      <td><img src="docs/screenshots/screenshot_1.png" width="200"/></td>
      <td><img src="docs/screenshots/screenshot_2.png" width="200"/></td>
      <td><img src="docs/screenshots/screenshot_3.png" width="200"/></td>
    </tr>
  </table>
</div>

## 🛠️ Setup

1. Klone das Repository:
```bash
git clone https://github.com/Icarus-B4/Fokus-Kiyto_KI.git
cd Fokus-Kiyto_KI
```

2. API-Key Konfiguration:
- Kopiere `config.properties.example` zu `config.properties`
- Füge deinen OpenAI API-Key in `config.properties` ein
- Die Datei wird nicht ins Git-Repository aufgenommen

3. Build und Installation:
```bash
./gradlew assembleDebug
```

## 🔑 API-Keys

Die App benötigt einen OpenAI API-Key für die KI-Funktionen. Dieser kann auf zwei Arten konfiguriert werden:
1. In der `config.properties` Datei
2. In den App-Einstellungen

**WICHTIG**: Füge niemals API-Keys direkt in den Source-Code ein oder committe sie ins Repository!

## 🧩 Architektur

Die App folgt der MVVM-Architektur (Model-View-ViewModel) und verwendet moderne Android-Entwicklungspraktiken:

```
app/
├── data/           # Datenquellen, Repositories, Datenmodelle
├── di/             # Dependency Injection
├── domain/         # Geschäftslogik, Anwendungsfälle
├── ui/             # Activities, Fragments, ViewModels, Adapter
├── utils/          # Hilfsfunktionen und -klassen
└── KiytoApp.kt     # Anwendungsklasse
```

## 🤝 Mitwirken

Beiträge sind willkommen! Wenn du zur Weiterentwicklung von Fokus-Kiyto KI beitragen möchtest:

1. Forke das Repository
2. Erstelle einen Feature-Branch (`git checkout -b feature/amazing-feature`)
3. Committe deine Änderungen (`git commit -m 'Add some amazing feature'`)
4. Pushe den Branch (`git push origin feature/amazing-feature`)
5. Öffne einen Pull Request

## 📄 Lizenz

Dieses Projekt ist unter der MIT-Lizenz lizenziert - siehe die [LICENSE](LICENSE) Datei für Details.

## 📞 Kontakt

Edgar Cuppari - [cupparikun@gmail.com](mailto:cupparikun@gmail.com)

Projektlink: [https://github.com/DEIN_USERNAME/Fokus-Kiyto_KI](https://github.com/DEIN_USERNAME/Fokus-Kiyto_KI)

---

<div align="center">
  <sub>Mit ❤️ entwickelt in Berlin</sub>
</div> 