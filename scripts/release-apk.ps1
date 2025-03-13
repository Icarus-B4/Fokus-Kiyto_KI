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

# Funktion zum Aktualisieren der App-Version
function Update-AppVersion {
    param (
        [string]$version
    )
    
    Write-Host "Updating app version to $version..." -ForegroundColor Green
    
    # Extrahiere versionCode aus der Version (z.B. 2.0.0 -> 200)
    $versionParts = $version.Split('.')
    $versionCode = [int]($versionParts[0] + $versionParts[1].PadLeft(2, '0') + $versionParts[2].PadLeft(2, '0'))
    
    # Update build.gradle.kts
    $gradlePath = "app/build.gradle.kts"
    $gradleContent = Get-Content $gradlePath -Raw
    $gradleContent = $gradleContent -replace 'versionCode\s*=\s*\d+', "versionCode = $versionCode"
    $gradleContent = $gradleContent -replace 'versionName\s*=\s*"[^"]*"', "versionName = `"$version`""
    Set-Content -Path $gradlePath -Value $gradleContent
    
    Write-Host "Updated $gradlePath" -ForegroundColor Green
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

# Git Commit and Push
Write-Host "Committing changes..."
git add .
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error adding files to git." -ForegroundColor Red
    exit 1
}

git commit -m $commitMessage
if ($LASTEXITCODE -ne 0) {
    Write-Host "Warning: No changes to commit or commit failed." -ForegroundColor Yellow
}

Write-Host "Pushing changes..."
git push
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
    $ghInstalled = Get-Command gh -ErrorAction SilentlyContinue
} catch {
    $ghInstalled = $null
}

if ($ghInstalled) {
    # Use GitHub CLI
    Write-Host "Using GitHub CLI for upload..."
    gh release create "v$version" --title "Kiyto App v$version" --notes "# Kiyto App Release v$version`n`n## New Features`n- Improved user interface`n- Bug fixes and performance improvements`n`n## Installation`nDownload and install the APK on your Android device. If you get an error, please uninstall the app and try again." $releasedApkPath
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
    
    # Check if release exists
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
            Write-Host "Release v$version already exists with ID: $releaseId"
        }
    } catch {
        $releaseExists = $false
    }
    
    if (-not $releaseExists) {
        # Create release
        Write-Host "Creating new release v$version..."
        $releaseData = @{
            tag_name = "v$version"
            name = "Kiyto App v$version"
            body = "# Kiyto App Release v$version`n`n## New Features`n- Improved user interface`n- Bug fixes and performance improvements`n`n## Installation`nDownload and install the APK on your Android device."
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
