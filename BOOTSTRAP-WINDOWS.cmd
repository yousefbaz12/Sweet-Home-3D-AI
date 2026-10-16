@echo off
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\windows\setup-tools.ps1"
if errorlevel 1 goto failed
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\windows\setup-source.ps1"
if errorlevel 1 goto failed
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\windows\build.ps1"
if errorlevel 1 goto failed
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\windows\package-catalogs.ps1"
if errorlevel 1 goto failed
echo.
echo The local Sweet Home 3D lab is ready.
pause
exit /b 0

:failed
echo.
echo Setup failed. Review the output above.
pause
exit /b 1
