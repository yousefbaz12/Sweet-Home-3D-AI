@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\windows\build.ps1"
if errorlevel 1 goto failed
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\windows\package-catalogs.ps1"
if errorlevel 1 goto failed
echo.
echo Build and catalog packaging completed successfully.
pause
exit /b 0

:failed
echo.
echo Build or packaging failed. Review the output above.
pause
exit /b 1

