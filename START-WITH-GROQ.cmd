@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\windows\run-with-groq.ps1"
if errorlevel 1 pause
