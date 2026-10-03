@echo off
setlocal EnableExtensions DisableDelayedExpansion
echo SkyRail STA Remote first-time server setup
echo Stop the Minecraft server before continuing.
echo.
set "STA_SETUP_ROOT="
set /p "STA_SETUP_ROOT=Server folder containing run.bat: "
if not defined STA_SETUP_ROOT goto :missing
set "STA_SETUP_HOST="
set /p "STA_SETUP_HOST=Desktop connection address [Enter for same PC only]: "
echo.
if defined STA_SETUP_HOST (
    powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup_sta_remote.ps1" -ServerRoot "%STA_SETUP_ROOT%" -BindAddress "0.0.0.0" -ClientHost "%STA_SETUP_HOST%"
) else (
    powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0setup_sta_remote.ps1" -ServerRoot "%STA_SETUP_ROOT%"
)
set "STA_SETUP_EXIT=%errorlevel%"
echo.
if "%STA_SETUP_EXIT%"=="0" (
    echo Setup complete. Keep the personal token shown above in a private place.
) else (
    echo Setup failed. Check the message above; existing credentials were not replaced.
)
pause
exit /b %STA_SETUP_EXIT%

:missing
echo No server folder entered. No files were changed.
pause
exit /b 2
