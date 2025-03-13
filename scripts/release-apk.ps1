param (
    [Parameter(Mandatory=$true)]
    [string]$version,
    
    [Parameter(Mandatory=$false)]
    [string]$token = $env:GITHUB_TOKEN,
    
    [Parameter(Mandatory=$false)]
    [string]$repo = "Icarus-B4/Fokus-Kiyto_KI",

    [Parameter(Mandatory=$false)]
    [string]$commitMessage = "Release v$version"
)

# Git Commit and Push
Write-Host "Committing changes..."
git add .
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error adding files to git." -ForegroundColor Red
    exit 1
}

git commit -m $commitMessage
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error committing changes." -ForegroundColor Red
    exit 1
}

Write-Host "Pushing changes..."
git push
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error pushing changes." -ForegroundColor Red
    exit 1
}

# Create directories
$releasesDir = "releases"
if (-not (Test-Path $releasesDir)) {
    New-Item -ItemType Directory -Path $releasesDir | Out-Null
    Write-Host "Created directory '$releasesDir'."
}

# Build APK
Write-Host "Building release APK..."
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
    gh release create "v$version" --title "Kiyto App v$version" --notes "# Kiyto App Release v$version`n`n## New Features`n- Improved user interface`n- Bug fixes and performance improvements`n`n## Installation`nDownload and install the APK on your Android device." $releasedApkPath
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
adb install $releasedApkPath
if ($LASTEXITCODE -ne 0) {
    Write-Host "Error installing APK via ADB." -ForegroundColor Red
} else {
    Write-Host "APK installed successfully!" -ForegroundColor Green
} 