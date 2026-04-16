# Behebung wiederkehrender Berechtigungsanfragen

## Problem

In der App erschien die Berechtigungsanfrage für `android.permission.WRITE_EXTERNAL_STORAGE` wiederholt, obwohl die Berechtigung bereits erteilt wurde. Insbesondere trat die Anfrage jedes Mal auf, wenn der Benutzer zum Chat-Fragment zurückkehrte.

## Ursache

Die Ursache des Problems lag in der `checkRequiredPermissions()`-Methode des `AIChatFragment`, die bei jedem Aufruf des Fragments prüfte, ob die Berechtigung vorhanden ist, ohne den Status zu speichern. Dadurch wurde die Berechtigungsanfrage immer wieder angezeigt, selbst wenn der Benutzer sie bereits erteilt oder abgelehnt hatte.

## Lösung

Die Lösung besteht aus zwei Teilen:

### 1. Verbesserung der `checkRequiredPermissions()` Methode im AIChatFragment

Die Methode wurde verbessert, um zu prüfen, ob die Berechtigungsanfrage bereits durchgeführt wurde:

```kotlin
private fun checkRequiredPermissions() {
    // Speicherberechtigungen prüfen
    val storagePermission = Manifest.permission.WRITE_EXTERNAL_STORAGE
    
    // Wenn die Berechtigung bereits geprüft wurde, nicht erneut prüfen
    if (com.deepcore.kiytoapp.utils.PermissionManager.isPermissionChecked(requireContext(), storagePermission)) {
        Log.d("AIChatFragment", "Speicherberechtigung wurde bereits geprüft")
        return
    }
    
    val requiredPermissions = mutableListOf<String>()
    
    // Prüfe Kamera-Berechtigung
    if (requireContext().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
        requiredPermissions.add(Manifest.permission.CAMERA)
    }
    
    // Prüfe Speicher-Berechtigung
    if (requireContext().checkSelfPermission(storagePermission) != PackageManager.PERMISSION_GRANTED) {
        requiredPermissions.add(storagePermission)
    }
    
    if (requiredPermissions.isNotEmpty()) {
        requestMultiplePermissionsLauncher.launch(requiredPermissions.toTypedArray())
    }
    
    // Markiere die Speicherberechtigung als geprüft, unabhängig vom Ergebnis
    com.deepcore.kiytoapp.utils.PermissionManager.markPermissionChecked(requireContext(), storagePermission)
    Log.d("AIChatFragment", "Speicherberechtigung als geprüft markiert")
}
```

### 2. Erweiterung des PermissionManager

Der `PermissionManager` wurde um neue Methoden erweitert, die den Status einzelner Berechtigungen speichern und abfragen können:

```kotlin
/**
 * Prüft, ob eine spezifische Berechtigung als überprüft markiert wurde
 */
fun isPermissionChecked(context: Context, permission: String): Boolean {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    return prefs.getBoolean("${permission}_checked", false)
}

/**
 * Markiert eine spezifische Berechtigung als überprüft
 */
fun markPermissionChecked(context: Context, permission: String) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit().putBoolean("${permission}_checked", true).apply()
}
```

## Verhalten nach der Änderung

Nach der Implementierung dieser Änderungen wird die Berechtigungsanfrage für den externen Speicher nur beim ersten Zugriff auf das Chat-Fragment angezeigt. Der Status der Anfrage (ob sie erteilt oder abgelehnt wurde) wird in den SharedPreferences gespeichert, sodass die Anfrage nicht erneut angezeigt wird, wenn der Benutzer zum Fragment zurückkehrt.

## Vorteile

1. **Verbesserte Benutzererfahrung**: Der Benutzer wird nicht mehr mit wiederholten Berechtigungsanfragen belästigt.

2. **Konsistentes Verhalten**: Die App respektiert die Entscheidung des Benutzers bezüglich der Berechtigungen.

3. **Bessere Code-Organisation**: Die Berechtigungsverwaltung wurde in den `PermissionManager` ausgelagert, was den Code modularer und wartbarer macht.

## Zusätzliche Empfehlungen

1. Dieser Ansatz sollte auf weitere Berechtigungen ausgeweitet werden, die möglicherweise ähnliche Probleme verursachen.

2. Eine verbesserte Behandlung von Berechtigungsverweigerungen könnte implementiert werden, um dem Benutzer eine Erklärung zu bieten, warum die Berechtigung benötigt wird, und wie er die App-Einstellungen öffnen kann, um die Berechtigung später zu erteilen. 