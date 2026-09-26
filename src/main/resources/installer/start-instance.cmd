@echo off
setlocal DisableDelayedExpansion
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0launch.ps1"
set "result=%errorlevel%"
if not "%result%"=="0" pause
exit /b %result%
