@echo off
cd /d "%~dp0"
echo Updating the small Ahmed El Sallab product sample...
node ".\scraping\scrape-elsallab.mjs"
if errorlevel 1 goto failed
powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\windows\package-catalogs.ps1"
if errorlevel 1 goto failed
echo.
echo Sample data and Sweet Home 3D libraries were updated successfully.
pause
exit /b 0

:failed
echo.
echo Update failed. Review the output above. Existing source code was not changed.
pause
exit /b 1
