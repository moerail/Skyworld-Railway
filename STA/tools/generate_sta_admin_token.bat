@echo off
setlocal EnableExtensions DisableDelayedExpansion

echo SkyRail STA Remote administrator token generator
echo Run once for each administrator. The personal token is shown only in this window.
echo.
set "STA_REMOTE_ADMIN="
set /p "STA_REMOTE_ADMIN=Administrator ID [dispatcher1]: "
if not defined STA_REMOTE_ADMIN set "STA_REMOTE_ADMIN=dispatcher1"

powershell.exe -NoLogo -NoProfile -NonInteractive -Command "$admin=$env:STA_REMOTE_ADMIN; if($admin -cnotmatch '^[A-Za-z0-9_-]{1,32}$'){ [Console]::Error.WriteLine('Invalid administrator ID: use 1-32 letters, digits, _ or -.'); exit 2 }; $bytes=New-Object byte[] 48; $rng=[Security.Cryptography.RandomNumberGenerator]::Create(); try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }; $token=[Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+','-').Replace('/','_'); $sha=[Security.Cryptography.SHA256]::Create(); try { $digest=[BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($token))).Replace('-','').ToLowerInvariant() } finally { $sha.Dispose() }; [Console]::WriteLine(''); [Console]::WriteLine('Client administrator ID: '+$admin); [Console]::WriteLine('Client personal token:   '+$token); [Console]::WriteLine(''); [Console]::WriteLine('Add this line under remote.admins in plugins/SkyworldTrainAPI/config.yml:'); [Console]::WriteLine('    '+$admin+': '+[char]34+$digest+[char]34); [Console]::WriteLine(''); [Console]::WriteLine('Give the personal token to this administrator privately. It is not saved by this tool.')"
if errorlevel 1 (
    echo.
    echo Token generation failed. No file was changed.
    if /i not "%~1"=="--no-pause" pause
    exit /b 1
)

echo.
if /i not "%~1"=="--no-pause" pause
exit /b 0
