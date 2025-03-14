param (
    [Parameter(Mandatory=$true)]
    [string]$version,
    
    [Parameter(Mandatory=$false)]
    [string]$token = $null,
    
    [Parameter(Mandatory=$false)]
    [string]$repo = "Icarus-B4/Fokus-Kiyto_KI",

    [Parameter(Mandatory=$false)]
    [string]$commitMessage = "Release v$version"
)

# Funktion zum Finden des Git-Executable
function Find-GitPath {
    # Prüfe direkt
    $gitCommand = Get-Command git -ErrorAction SilentlyContinue
    if ($gitCommand) {
        return "git"
    }
    
    # Suche in üblichen Verzeichnissen
    $commonPaths = @(
        "C:\Program Files\Git\bin\git.exe",
        "C:\Program Files (x86)\Git\bin\git.exe",
        "$env:ProgramFiles\Git\bin\git.exe",
        "$env:LOCALAPPDATA\Programs\Git\bin\git.exe"
    )
    
    foreach ($path in $commonPaths) {
        if (Test-Path $path) {
            return $path
        }
    }
    
    Write-Host "Git wurde nicht gefunden. Bitte installiere Git und stelle sicher, dass es im PATH verfügbar ist." -ForegroundColor Red
    exit 1
}

# Setze den Git-Pfad
$gitExe = Find-GitPath
Write-Host "Verwende Git von: $gitExe" -ForegroundColor Green

# Funktion zum Aktualisieren der App-Version
function Update-AppVersion {
    param (
        [string]$version
    )
    
    Write-Host "Updating app version to $version..." -ForegroundColor Green
    
    # Extrahiere versionCode aus der Version (z.B. 2.0.5 -> 20005)
    $versionParts = $version.Split('.')
    $versionCode = [int]($versionParts[0] + $versionParts[1].PadLeft(2, '0') + $versionParts[2].PadLeft(2, '0'))
    
    # Lese aktuelle versionCode
    $gradlePath = "app/build.gradle.kts"
    $gradleContent = Get-Content $gradlePath -Raw
    if ($gradleContent -match 'versionCode\s*=\s*(\d+)') {
        $currentVersionCode = [int]$matches[1]
        # Erhöhe versionCode nur wenn die neue Version größer ist
        if ($versionCode -le $currentVersionCode) {
            $versionCode = $currentVersionCode + 1
            Write-Host "Increasing versionCode to $versionCode to ensure update detection" -ForegroundColor Yellow
        }
    }
    
    # Update build.gradle.kts
    $gradleContent = $gradleContent -replace 'versionCode\s*=\s*\d+', "versionCode = $versionCode"
    $gradleContent = $gradleContent -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$version`""
    Set-Content -Path $gradlePath -Value $gradleContent
    
    Write-Host "Updated $gradlePath with versionCode=$versionCode and versionName=$version" -ForegroundColor Green
}

# Version aktualisieren
Update-AppVersion -version $version

# Token-Handling verbessert
function Get-GitHubToken {
    # 1. Prüfe, ob Token als Parameter übergeben wurde
    if ($token) {
        return $token
    }
    
    # 2. Prüfe, ob Token in Umgebungsvariable existiert
    $envToken = $env:GITHUB_TOKEN
    if ($envToken) {
        return $envToken
    }
    
    # 3. Prüfe, ob Token in User-Umgebungsvariable existiert
    $userToken = [System.Environment]::GetEnvironmentVariable('GITHUB_TOKEN', [System.EnvironmentVariableTarget]::User)
    if ($userToken) {
        return $userToken
    }
    
    # 4. Fehler, wenn kein Token gefunden
    Write-Host "GitHub token not found. Please set the GITHUB_TOKEN environment variable or pass the token as a parameter." -ForegroundColor Red
    Write-Host "Gehe zu GitHub Settings > Developer Settings > Personal Access Tokens > Tokens (classic)" -ForegroundColor Yellow
    Write-Host "Wähle 'repo', 'write:packages', und 'delete:packages' Berechtigungen." -ForegroundColor Yellow
    exit 1
}

# Token abrufen und setzen
$token = Get-GitHubToken
$env:GITHUB_TOKEN = $token

# Funktion zum Generieren einer ausführlichen Commit-Nachricht aus der Git-History
function Get-ReleaseNotes {
    param (
        [string]$version
    )
    
    Write-Host "Generating release notes for version $version..." -ForegroundColor Green
    
    # Ermittle das letzte Tag/Release
    $lastTag = & $gitExe describe --tags --abbrev=0 2>$null
    $gitRange = if ($lastTag) { "$lastTag..HEAD" } else { "HEAD" }
    
    # Hole alle Commit-Nachrichten seit dem letzten Release
    $commits = & $gitExe log $gitRange --pretty=format:"%s" --no-merges
    
    if (-not $commits) {
        return "Release v$version`n`nKeine Änderungen dokumentiert."
    }
    
    # Kategorisiere Commits (basierend auf Präfixen oder Keywords)
    $features = @()
    $bugfixes = @()
    $improvements = @()
    $others = @()
    
    foreach ($commit in $commits) {
        if ($commit -match "^(feat|feature|add):|hinzugefügt|neue|feature") {
            $features += "- $($commit -replace '^(feat|feature|add):\s*', '')"
        }
        elseif ($commit -match "^fix:|behoben|fehler|bug") {
            $bugfixes += "- $($commit -replace '^fix:\s*', '')"
        }
        elseif ($commit -match "^(improve|perf|refactor):|verbessert|optimiert") {
            $improvements += "- $($commit -replace '^(improve|perf|refactor):\s*', '')"
        }
        else {
            $others += "- $commit"
        }
    }
    
    # Erstelle die Release-Nachricht
    $releaseNotes = "# Kiyto App Release v$version`n`n"
    
    if ($features.Count -gt 0) {
        $releaseNotes += "## Neue Funktionen`n"
        $releaseNotes += $features -join "`n"
        $releaseNotes += "`n`n"
    }
    
    if ($bugfixes.Count -gt 0) {
        $releaseNotes += "## Fehlerbehebungen`n"
        $releaseNotes += $bugfixes -join "`n"
        $releaseNotes += "`n`n"
    }
    
    if ($improvements.Count -gt 0) {
        $releaseNotes += "## Verbesserungen`n"
        $releaseNotes += $improvements -join "`n"
        $releaseNotes += "`n`n"
    }
    
    if ($others.Count -gt 0) {
        $releaseNotes += "## Sonstige Änderungen`n"
        $releaseNotes += $others -join "`n"
        $releaseNotes += "`n`n"
    }
    
    $releaseNotes += "## Installation`nLade die APK herunter und installiere sie auf deinem Android-Gerät. Bei Problemen deinstalliere bitte vorher die App und versuche es erneut."
    
    return $releaseNotes
}

# Git Commit and Push
Write-Host "Committing changes..."

# Wenn keine explizite Commit-Nachricht angegeben wurde, generiere Release Notes
$releaseNotes = Get-ReleaseNotes -version $version

& $gitExe add .
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error adding files to git." -ForegroundColor Red
    exit 1
}

& $gitExe commit -m $commitMessage
if ($LASTEXITCODE -ne 0) {
    Write-Host "Warning: No changes to commit or commit failed." -ForegroundColor Yellow
}

Write-Host "Pushing changes..."
& $gitExe push
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error pushing changes." -ForegroundColor Red
    Write-Host "Tipp: Wenn der Push aufgrund eines erkannten Tokens blockiert wird, prüfe den Git-Verlauf und bereinige ihn." -ForegroundColor Yellow
    exit 1
}

# Create directories
$releasesDir = "releases"
if (-not (Test-Path $releasesDir)) {
    New-Item -ItemType Directory -Path $releasesDir | Out-Null
    Write-Host "Created directory '$releasesDir'."
}

# Build APK mit Keystore-Parameter
Write-Host "Building release APK with proper signing..."
$env:KEYSTORE_PASSWORD = "kiytoapp"  # Setze deinen Keystore-Passwort hier
$env:KEY_ALIAS = "kiyto"          # Setze deinen Key-Alias hier
$env:KEY_PASSWORD = "kiytoapp"    # Setze dein Key-Passwort hier

./gradlew assembleRelease
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error building APK." -ForegroundColor Red
    exit 1
}

# Define paths
$apkPath = "app/build/outputs/apk/release/app-release.apk"
$releasedApkPath = "$releasesDir/KiytoApp-v$version.apk"

# Copy APK
Write-Host "Copying APK..."
Copy-Item -Path $apkPath -Destination $releasedApkPath -Force
if (-not (Test-Path $releasedApkPath)) {
    Write-Host "Error copying APK." -ForegroundColor Red
    exit 1
}

# Check if GitHub CLI is installed
$ghInstalled = $null
try {
    $ghInstalled = $null  # Force API usage
} catch {
    $ghInstalled = $null
}

if ($ghInstalled) {
    # Use GitHub CLI
    Write-Host "Using GitHub CLI for upload..."
    gh release create "v$version" --title "Kiyto App v$version" --notes "$releaseNotes" $releasedApkPath
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error creating release with GitHub CLI." -ForegroundColor Red
    } else {
        Write-Host "Release created and APK uploaded successfully!" -ForegroundColor Green
    }
} else {
    # Use curl if GitHub CLI is not installed
    if (-not $token) {
        Write-Host "GitHub token not found. Please set the GITHUB_TOKEN environment variable or pass the token as a parameter." -ForegroundColor Red
        exit 1
    }
    
    Write-Host "Using API for upload..."
    Write-Host "Using token: $($token.Substring(0, 4))..." -ForegroundColor Yellow
    
    # Check if release exists
    $releaseUrl = "https://api.github.com/repos/$repo/releases/tags/v$version"
    Write-Host "Checking release URL: $releaseUrl" -ForegroundColor Yellow
    
    $releaseExists = $false
    $releaseId = $null
    
    try {
        Write-Host "Sending API request..." -ForegroundColor Yellow
        $headers = @{
            "Authorization" = "token $token"
            "Accept" = "application/vnd.github.v3+json"
        }
        Write-Host "Headers: $($headers | ConvertTo-Json)" -ForegroundColor Yellow
        
        $releaseInfo = Invoke-RestMethod -Uri $releaseUrl -Headers $headers -Method Get -ErrorAction SilentlyContinue
        
        if ($releaseInfo -and $releaseInfo.id) {
            $releaseExists = $true
            $releaseId = $releaseInfo.id
            Write-Host "Release v$version already exists with ID: $releaseId"
        }
    } catch {
        Write-Host "Error checking release: $_" -ForegroundColor Red
        $releaseExists = $false
    }
    
    if (-not $releaseExists) {
        # Create release
        Write-Host "Creating new release v$version..."
        $releaseData = @{
            tag_name = "v$version"
            name = "Kiyto App v$version"
            body = $releaseNotes
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
            Write-Host "Release created with ID: $releaseId"
        } catch {
            Write-Host "Error creating release: $_" -ForegroundColor Red
            exit 1
        }
    }
    
    # Upload asset
    if ($releaseId) {
        Write-Host "Uploading APK as asset..."
        $assetName = [System.IO.Path]::GetFileName($releasedApkPath)
        $uploadUrl = "https://uploads.github.com/repos/$repo/releases/$releaseId/assets?name=$assetName"
        
        try {
            $fileBytes = [System.IO.File]::ReadAllBytes($releasedApkPath)
            $response = Invoke-RestMethod -Uri $uploadUrl -Headers @{
                "Authorization" = "token $token"
                "Accept" = "application/vnd.github.v3+json"
                "Content-Type" = "application/vnd.android.package-archive"
            } -Method Post -Body $fileBytes
            
            Write-Host "APK uploaded successfully: $($response.browser_download_url)" -ForegroundColor Green
        } catch {
            Write-Host "Error uploading APK: $_" -ForegroundColor Red
            exit 1
        }
    }
}

Write-Host "Process completed. The APK is located at: $releasedApkPath" -ForegroundColor Green

# Install via ADB
Write-Host "Installing APK via ADB..."

# Prüfe, ob ADB verfügbar ist oder finde den Pfad
function Find-AdbPath {
    # Prüfe direkt
    $adbCommand = Get-Command adb -ErrorAction SilentlyContinue
    if ($adbCommand) {
        return "adb"
    }
    
    # Suche in üblichen Verzeichnissen
    $commonPaths = @(
        "$env:LOCALAPPDATA\Android\sdk\platform-tools\adb.exe",
        "C:\Program Files\Android\android-sdk\platform-tools\adb.exe",
        "C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe",
        "$env:USERPROFILE\AppData\Local\Android\Sdk\platform-tools\adb.exe"
    )
    
    foreach ($path in $commonPaths) {
        if (Test-Path $path) {
            return $path
        }
    }
    
    # Suche in Android Studio-Verzeichnissen
    $androidStudioPath = "$env:LOCALAPPDATA\Android\Sdk"
    if (Test-Path $androidStudioPath) {
        $platformToolsPath = Join-Path $androidStudioPath "platform-tools\adb.exe"
        if (Test-Path $platformToolsPath) {
            return $platformToolsPath
        }
    }
    
    return $null
}

$adbPath = Find-AdbPath
if ($adbPath) {
    # Verwende -r Option für Updates ohne Datenverlust
    Write-Host "Installing APK with update mode..." -ForegroundColor Green
    & $adbPath install -r $releasedApkPath
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Error installing APK via ADB. This could be due to signature mismatch." -ForegroundColor Red
        Write-Host "If the installation fails, please uninstall the app manually first." -ForegroundColor Yellow
    } else {
        Write-Host "APK installed successfully!" -ForegroundColor Green
    }
} else {
    Write-Host "ADB not found. Cannot install APK automatically." -ForegroundColor Yellow
    Write-Host "To install manually:" -ForegroundColor Yellow
    Write-Host "1. Make sure Android SDK is installed" -ForegroundColor Yellow
    Write-Host "2. Add platform-tools to your PATH environment variable" -ForegroundColor Yellow
    Write-Host "3. Use: adb install -r $releasedApkPath" -ForegroundColor Yellow
    Write-Host "Or install the APK directly on your device: $releasedApkPath" -ForegroundColor Yellow
} 
