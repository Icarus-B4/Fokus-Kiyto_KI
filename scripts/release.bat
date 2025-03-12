@echo off
chcp 65001 > nul
setlocal

if "%1"=="" (
    echo Error: Please provide a version number.
    echo Usage: release.bat VERSION [GITHUB_TOKEN]
    exit /b 1
)

set VERSION=%1
set GITHUB_TOKEN=%2

echo Starting release process for version %VERSION%...

powershell -NoProfile -ExecutionPolicy Bypass -Command "& '%~dp0release-apk.ps1' -version '%VERSION%' -token '%GITHUB_TOKEN%'"

if %ERRORLEVEL% neq 0 (
    echo Error executing release script.
    exit /b 1
)

echo Release process completed.
exit /b 0 