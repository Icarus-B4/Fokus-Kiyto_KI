param (
    [Parameter(Mandatory=$true)]
    [string]$version,
    
    [Parameter(Mandatory=$false)]
    [string]$token = $env:GITHUB_TOKEN,
    
    [Parameter(Mandatory=$false)]
    [string]$repo = "Icarus-B4/Fokus-Kiyto_KI",
    
    [Parameter(Mandatory=$false)]
    [string]$keystorePath = "app/keystore/kiyto_release.keystore",
    
    [Parameter(Mandatory=$false)]
    [string]$keystorePassword = "kiytoapp",
    
    [Parameter(Mandatory=$false)]
    [string]$keyAlias = "kiyto",
    
    [Parameter(Mandatory=$false)]
    [string]$keyPassword = "kiytoapp"
)

# Verzeichnisse erstellen
$releasesDir = "releases"
if (-not (Test-Path $releasesDir)) {
    New-Item -ItemType Directory -Path $releasesDir | Out-Null
    Write-Host "Verzeichnis '$releasesDir' erstellt."
}

# APK erstellen
Write-Host "Erstelle Release-APK..."
./gradlew assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host "Fehler beim Erstellen der APK." -ForegroundColor Red
    exit 1
}

# Pfade definieren
$unsignedApkPath = "app/build/outputs/apk/release/app-release-unsigned.apk"
$signedApkPath = "$releasesDir/KiytoApp-v$version-signed.apk"

# APK signieren
Write-Host "Signiere APK..."
jarsigner -verbose -sigalg SHA256withRSA -digestalg SHA-256 -keystore $keystorePath -storepass $keystorePassword -keypass $keyPassword $unsignedApkPath $keyAlias
if ($LASTEXITCODE -ne 0) {
    Write-Host "Fehler beim Signieren der APK." -ForegroundColor Red
    exit 1
}

# Signierte APK kopieren
Write-Host "Kopiere signierte APK..."
Copy-Item -Path $unsignedApkPath -Destination $signedApkPath -Force
if (-not (Test-Path $signedApkPath)) {
    Write-Host "Fehler beim Kopieren der signierten APK." -ForegroundColor Red
    exit 1
}

# Prüfen, ob GitHub CLI installiert ist
$ghInstalled = $null
try {
    $ghInstalled = Get-Command gh -ErrorAction SilentlyContinue
} catch {
    $ghInstalled = $null
}

if ($ghInstalled) {
    # GitHub CLI verwenden
    Write-Host "Verwende GitHub CLI zum Hochladen..."
    gh release create "v$version" --title "Kiyto App v$version" --notes "# Kiyto App Release v$version`n`n## Neue Funktionen`n- Verbesserte Benutzeroberfläche`n- Fehlerbehebungen und Leistungsverbesserungen`n`n## Installation`nLaden Sie die APK herunter und installieren Sie sie auf Ihrem Android-Gerät." $signedApkPath
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Fehler beim Erstellen des Releases mit GitHub CLI." -ForegroundColor Red
    } else {
        Write-Host "Release erfolgreich erstellt und APK hochgeladen!" -ForegroundColor Green
    }
} else {
    # Curl verwenden, wenn GitHub CLI nicht installiert ist
    if (-not $token) {
        Write-Host "GitHub Token nicht gefunden. Bitte setzen Sie die Umgebungsvariable GITHUB_TOKEN oder übergeben Sie das Token als Parameter." -ForegroundColor Red
        exit 1
    }
    
    Write-Host "Verwende Curl zum Hochladen..."
    
    # Prüfen, ob das Release bereits existiert
    $releaseUrl = "https://api.github.com/repos/$repo/releases/tags/v$version"
    $releaseExists = $false
    $releaseId = $null
    
    try {
        $releaseInfo = Invoke-RestMethod -Uri $releaseUrl -Headers @{
            "Authorization" = "token $token"
            "Accept" = "application/vnd.github.v3+json"
        } -Method Get -ErrorAction SilentlyContinue
        
        if ($releaseInfo -and $releaseInfo.id) {
            $releaseExists = $true
            $releaseId = $releaseInfo.id
            Write-Host "Release v$version existiert bereits mit ID: $releaseId"
        }
    } catch {
        $releaseExists = $false
    }
    
    if (-not $releaseExists) {
        # Release erstellen
        Write-Host "Erstelle neues Release v$version..."
        $releaseData = @{
            tag_name = "v$version"
            name = "Kiyto App v$version"
            body = "# Kiyto App Release v$version`n`n## Neue Funktionen`n- Verbesserte Benutzeroberfläche`n- Fehlerbehebungen und Leistungsverbesserungen`n`n## Installation`nLaden Sie die APK herunter und installieren Sie sie auf Ihrem Android-Gerät."
            draft = $false
            prerelease = $false
        } | ConvertTo-Json
        
        try {
            $releaseResponse = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Headers @{
                "Authorization" = "token $token"
                "Accept" = "application/vnd.github.v3+json"
                "Content-Type" = "application/json"
            } -Method Post -Body $releaseData
            
            $releaseId = $releaseResponse.id
            Write-Host "Release erstellt mit ID: $releaseId"
        } catch {
            Write-Host "Fehler beim Erstellen des Releases: $_" -ForegroundColor Red
            exit 1
        }
    }
    
    # Asset hochladen
    if ($releaseId) {
        Write-Host "Lade APK als Asset hoch..."
        $assetName = [System.IO.Path]::GetFileName($signedApkPath)
        $uploadUrl = "https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=$assetName"
        
        try {
            $fileBytes = [System.IO.File]::ReadAllBytes($signedApkPath)
            $response = Invoke-RestMethod -Uri $uploadUrl -Headers @{
                "Authorization" = "token $token"
                "Accept" = "application/vnd.github.v3+json"
                "Content-Type" = "application/vnd.android.package-archive"
            } -Method Post -Body $fileBytes
            
            Write-Host "APK erfolgreich hochgeladen: $($response.browser_download_url)" -ForegroundColor Green
        } catch {
            Write-Host "Fehler beim Hochladen der APK: $_" -ForegroundColor Red
            exit 1
        }
    }
}

Write-Host "Prozess abgeschlossen. Die signierte APK befindet sich unter: $signedApkPath" -ForegroundColor Green

# Alternative Installation über ADB
Write-Host "Installiere APK über ADB..."
adb install app/build/outputs/apk/debug/app-debug.apk
if ($LASTEXITCODE -ne 0) {
    Write-Host "Fehler beim Installieren der APK über ADB." -ForegroundColor Red
} else {
    Write-Host "APK erfolgreich installiert!" -ForegroundColor Green
}

$env:KEYSTORE_PASSWORD="kiytoapp"
$env:KEY_PASSWORD="kiytoapp"
$env:KEY_ALIAS="kiyto" 