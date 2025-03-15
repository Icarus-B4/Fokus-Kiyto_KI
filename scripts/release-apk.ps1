param(
    [Parameter(Mandatory=$true)]
    [string]$version,
    
    [Parameter(Mandatory=$false)]
    [string]$token = ""
)

$gitExe = "C:\Program Files\Git\bin\git.exe"
Write-Host "Verwende Git von: $gitExe"

# Update app version
function Update-AppVersion {
    Write-Host "Updating app version to $version..."
    
    $gradleFile = "app/build.gradle.kts"
    $content = Get-Content $gradleFile -Raw
    
    # Extrahiere aktuellen versionCode und versionName
    $versionCodeMatch = [regex]::Match($content, 'versionCode\s*=\s*(\d+)')
    $currentVersionCode = [int]$versionCodeMatch.Groups[1].Value
    
    $versionNameMatch = [regex]::Match($content, 'versionName\s*=\s*"([^"]*)"')
    $currentVersionName = $versionNameMatch.Groups[1].Value
    
    Write-Host "Aktuelle Version: $currentVersionName (Code: $currentVersionCode)"
    Write-Host "Neue Version: $version"
    
    # Vergleiche Versionen
    $currentParts = $currentVersionName.Split(".")
    $newParts = $version.Split(".")
    
    $isHigherVersion = $false
    
    # Prüfe, ob die neue Version höher ist
    if ($newParts[0] -gt $currentParts[0]) {
        $isHigherVersion = $true
    } elseif ($newParts[0] -eq $currentParts[0] -and $newParts.Count -gt 1 -and $currentParts.Count -gt 1) {
        if ($newParts[1] -gt $currentParts[1]) {
            $isHigherVersion = $true
        } elseif ($newParts[1] -eq $currentParts[1] -and $newParts.Count -gt 2 -and $currentParts.Count -gt 2) {
            if ($newParts[2] -gt $currentParts[2]) {
                $isHigherVersion = $true
            }
        }
    }
    
    if (-not $isHigherVersion) {
        Write-Host "WARNUNG: Die angegebene Version $version ist nicht höher als die aktuelle Version $currentVersionName!" -ForegroundColor Red
        Write-Host "Bitte gib eine höhere Version an." -ForegroundColor Red
        exit 1
    }
    
    # Berechne neuen versionCode basierend auf Semantic Versioning Format
    # Dies stellt sicher, dass der versionCode eindeutig ist und die Versionsreihenfolge korrekt ist
    $newVersionParts = $version.Split(".")
    $majorVersion = [int]$newVersionParts[0]
    $minorVersion = if ($newVersionParts.Length -gt 1) { [int]$newVersionParts[1] } else { 0 }
    $patchVersion = if ($newVersionParts.Length -gt 2) { [int]$newVersionParts[2] } else { 0 }
    
    # Format: MMNNPP (Major, Minor, Patch)
    # Beispiel: Version 2.3.5 -> versionCode = 20305
    $newVersionCode = ($majorVersion * 10000) + ($minorVersion * 100) + $patchVersion
    
    # Stelle sicher, dass der neue versionCode immer höher ist als der aktuelle
    if ($newVersionCode -le $currentVersionCode) {
        $newVersionCode = $currentVersionCode + 1
        Write-Host "Der berechnete versionCode war nicht höher als der aktuelle. Erhöhe auf $newVersionCode."
    }
    
    Write-Host "Neuer versionCode: $newVersionCode"
    
    # Update versionCode und versionName
    $content = $content -replace 'versionCode\s*=\s*\d+', "versionCode = $newVersionCode"
    $content = $content -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$version`""
    
    $content | Set-Content $gradleFile -NoNewline
    Write-Host "Updated $gradleFile with versionCode=$newVersionCode and versionName=$version"
}

# Commit changes
function Commit-Changes {
    Write-Host "Committing changes..."
    
    # Generiere ausführliche Commit-Nachricht
    $commitMessage = "Release v$version`n`n"
    $commitMessage += "versionCode: $((Get-Content app/build.gradle.kts | Select-String 'versionCode\s*=\s*(\d+)').Matches.Groups[1].Value)`n"
    $commitMessage += "Aenderungen in dieser Version:`n"
    $commitMessage += "- Berechnung des versionCode basierend auf Semantic Versioning`n"
    $commitMessage += "- Bereinigung des Build-Prozesses vor dem Erstellen der APK`n"
    $commitMessage += "- Verbesserung des Release-Prozesses mit API-Ueberpruefung und Fehlerbehandlung`n"
    $commitMessage += "- Hinzufuegen von Upload-Logik fuer die APK als Asset in GitHub Releases"
    
    Write-Host "Commit-Nachricht:`n$commitMessage"
    
    # Schreibe die Commit-Nachricht in eine temporäre Datei
    $tempFile = [System.IO.Path]::GetTempFileName()
    $commitMessage | Out-File -FilePath $tempFile -Encoding utf8
    
    # Füge Änderungen hinzu und committe mit der generierten Nachricht aus der Datei
    & $gitExe add app/build.gradle.kts
    & $gitExe commit -F $tempFile
    
    # Lösche die temporäre Datei
    Remove-Item -Path $tempFile
}

# Push changes
function Push-Changes {
    Write-Host "Pushing changes..."
    & $gitExe push
}

# Build APK
function Build-ReleaseApk {
    Write-Host "Building release APK with proper signing..."
    
    # Zuerst bereinigen wir das Projekt, um sicherzustellen, dass keine alten Artefakte Probleme verursachen
    Write-Host "Clean project..."
    ./gradlew clean
    
    # Dann bauen wir die Release-APK
    Write-Host "Build release APK..."
    ./gradlew assembleRelease
    
    # Überprüfe, ob die APK erstellt wurde
    if (Test-Path "app/build/outputs/apk/release/app-release.apk") {
        Write-Host "APK erfolgreich erstellt: app/build/outputs/apk/release/app-release.apk"
    } else {
        Write-Host "Fehler: APK wurde nicht erstellt!" -ForegroundColor Red
        exit 1
    }
}

# Copy APK
function Copy-Apk {
    Write-Host "Copying APK..."
    
    # Ensure releases directory exists
    if (-not (Test-Path "releases")) {
        New-Item -ItemType Directory -Path "releases"
    }
    
    $timestamp = Get-Date -Format 'yyyyMMdd'
    $apkFileName = "KiytoApp-v$version-$timestamp.apk"
    Copy-Item "app/build/outputs/apk/release/app-release.apk" "releases/$apkFileName" -Force
    Write-Host "APK kopiert nach: releases/$apkFileName"
    
    # Rückgabe des Dateinamens für die weitere Verwendung
    return $apkFileName
}

# Create GitHub release
function Create-GithubRelease {
    param(
        [Parameter(Mandatory=$true)]
        [string]$apkFileName
    )
    
    Write-Host "Using API for upload..."
    
    # Verwende das Token aus dem Parameter, falls vorhanden, sonst aus der Umgebungsvariable
    $githubToken = if ($token -and $token -ne "") { $token } else { $env:GITHUB_TOKEN }
    
    if (-not $githubToken -or $githubToken -eq "") {
        Write-Host "WARNUNG: Kein GitHub-Token gefunden. Bitte gib ein Token als Parameter an oder setze die Umgebungsvariable GITHUB_TOKEN." -ForegroundColor Yellow
        Write-Host "Der Upload auf GitHub wird übersprungen."
        return
    }
    
    Write-Host "Using token: $($githubToken.Substring(0, 4))..."
    
    $owner = "Icarus-B4"
    $repo = "Fokus-Kiyto_KI"
    $tag = "v$version"
    $apiUrl = "https://api.github.com/repos/$owner/$repo"
    $releaseUrl = "$apiUrl/releases/tags/$tag"
    
    # Extrahiere versionCode aus dem Gradle-File
    $versionCode = (Get-Content app/build.gradle.kts | Select-String 'versionCode\s*=\s*(\d+)').Matches.Groups[1].Value
    
    # Generiere Release-Beschreibung
    $releaseDescription = "Release v$version`n`n"
    $releaseDescription += "versionCode: $versionCode`n`n"
    $releaseDescription += "Aenderungen in dieser Version:`n"
    $releaseDescription += "- Verbessertes Update-System`n"
    $releaseDescription += "- Fehlerbehebungen"
    
    Write-Host "API URL: $apiUrl"
    Write-Host "Überprüfe, ob Release bereits existiert: $releaseUrl"
    
    $headers = @{
        Authorization = "token $githubToken"
        Accept = "application/vnd.github.v3+json"
    }
    
    try {
        Write-Host "Teste API-Verbindung..."
        try {
            $userInfo = Invoke-RestMethod -Uri "https://api.github.com/user" -Headers $headers
            Write-Host "API-Verbindung erfolgreich. Angemeldet als: $($userInfo.login)"
        }
        catch {
            Write-Host "Fehler bei der API-Verbindung: $_" -ForegroundColor Red
            throw "GitHub API-Verbindung fehlgeschlagen. Bitte überprüfe dein Token und deine Internetverbindung."
        }
        
        # Prüfe, ob der Release bereits existiert
        try {
            $release = Invoke-RestMethod -Uri $releaseUrl -Headers $headers -ErrorAction Stop
            Write-Host "Release v$version bereits vorhanden (ID: $($release.id)). Lösche den alten Release..."
            
            # Lösche den bestehenden Release
            Invoke-RestMethod -Method Delete -Uri "$apiUrl/releases/$($release.id)" -Headers $headers
            Write-Host "Alter Release gelöscht."
            
            # Lösche auch den Tag, wenn vorhanden
            try {
                Invoke-RestMethod -Method Delete -Uri "$apiUrl/git/refs/tags/$tag" -Headers $headers
                Write-Host "Tag konnte nicht gelöscht werden oder existiert nicht."
            } catch {
                Write-Host "Tag konnte nicht gelöscht werden oder existiert nicht."
            }
        } catch {
            if ($_.Exception.Response.StatusCode -eq 404) {
                Write-Host "Release existiert noch nicht, erstelle einen neuen..."
            } else {
                Write-Host "Fehler beim Prüfen des Releases: $_" -ForegroundColor Red
            }
        }
        
        # Erstelle einen neuen Release
        Write-Host "Erstelle neuen Release v$version..."
        $createReleaseUrl = "$apiUrl/releases"
        $body = @{
            tag_name = $tag
            name = "Release v$version ($(Get-Date -Format 'yyyy-MM-dd'))"
            body = $releaseDescription
            draft = $false
            prerelease = $false
        } | ConvertTo-Json
        
        try {
            $release = Invoke-RestMethod -Method Post -Uri $createReleaseUrl -Headers $headers -Body $body
            Write-Host "Release erstellt mit ID: $($release.id)"
        } catch {
            Write-Host "Fehler beim Erstellen des Releases: $_" -ForegroundColor Red
            throw "Konnte keinen Release erstellen. Bitte überprüfe dein Token und Repository-Zugriff."
        }
        
        # Lade APK hoch
        Write-Host "Lade APK als Asset hoch..."
        $uploadUrl = $release.upload_url -replace '\{\?name,label\}', "?name=$(Split-Path $apkFileName -Leaf)"
        
        try {
            $response = Invoke-RestMethod -Method Post -Uri $uploadUrl -Headers @{
                Authorization = "token $githubToken"
                "Content-Type" = "application/vnd.android.package-archive"
            } -InFile "releases/$apkFileName"
            
            Write-Host "APK erfolgreich hochgeladen: $($response.browser_download_url)"
            return $response.browser_download_url
        } catch {
            Write-Host "Fehler beim Hochladen der APK: $_" -ForegroundColor Red
            Write-Host $_.Exception.Response
            if ($_.Exception.Response) {
                $responseStream = $_.Exception.Response.GetResponseStream()
                $reader = New-Object System.IO.StreamReader($responseStream)
                $responseBody = $reader.ReadToEnd()
                Write-Host "Antwort: $responseBody"
            }
            throw "Konnte die APK nicht hochladen."
        }
    } catch {
        Write-Host "Fehler während des Release-Prozesses: $_" -ForegroundColor Red
        throw "Release-Prozess fehlgeschlagen."
    }
}

try {
    Write-Host "=== Starte Release-Prozess für Version $version ===" -ForegroundColor Cyan
    
    Update-AppVersion
    Commit-Changes
    Push-Changes
    Build-ReleaseApk
    $apkFileName = Copy-Apk
    $downloadUrl = Create-GithubRelease -apkFileName $apkFileName
    
    Write-Host "=== Release-Prozess abgeschlossen ===" -ForegroundColor Green
    Write-Host "Die APK wurde erstellt und auf GitHub hochgeladen."
    Write-Host "Die APK ist lokal verfügbar unter: releases/$apkFileName"
    Write-Host "Benutzer werden beim nächsten App-Start über das Update informiert."
    Write-Host "Release abgeschlossen. Download-URL: $downloadUrl"
    exit 0
}
catch {
    Write-Host "Error: $_" -ForegroundColor Red
    if ($_.Exception.Message -match "rejected.*token") {
        Write-Host "Tipp: Wenn der Push aufgrund eines erkannten Tokens blockiert wird, prüfe den Git-Verlauf und bereinige ihn."
    }
    exit 1
} 