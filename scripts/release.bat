@echo off
setlocal

if "%1"=="" (
    echo Fehler: Bitte geben Sie eine Versionsnummer an.
    echo Verwendung: release.bat VERSION [GITHUB_TOKEN]
    exit /b 1
)

set VERSION=%1
set GITHUB_TOKEN=%2

echo Starte Release-Prozess für Version %VERSION%...

powershell -ExecutionPolicy Bypass -File "%~dp0release-apk.ps1" -version "%VERSION%" -token "%GITHUB_TOKEN%"

if %ERRORLEVEL% neq 0 (
    echo Fehler beim Ausführen des Release-Skripts.
    exit /b 1
)

echo Release-Prozess abgeschlossen.
exit /b 0 