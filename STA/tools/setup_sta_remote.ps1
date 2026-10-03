[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ServerRoot,
    [ValidatePattern('^[A-Za-z0-9_-]{1,32}$')][string]$AdminId = 'dispatcher1',
    [ValidateRange(1, 65535)][int]$Port = 8766,
    [string]$BindAddress = '127.0.0.1',
    [string]$ClientHost,
    [string]$JavaHome = $env:JAVA_HOME,
    [ValidatePattern('^[A-Za-z0-9_. -]+\.(bat|cmd)$')][string]$LaunchScript = 'run.bat'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ($env:OS -ne 'Windows_NT') { throw 'This setup requires Windows and DPAPI.' }
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Utility\Microsoft.PowerShell.Utility.psd1') -ErrorAction Stop
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1') -ErrorAction Stop

function New-RandomText([int]$byteCount) {
    $bytes = New-Object byte[] $byteCount
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Get-Sha256Text([string]$value) {
    $sha = [Security.Cryptography.SHA256]::Create()
    try {
        $bytes = $sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($value))
        return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
    } finally { $sha.Dispose() }
}

function Set-RemoteSection([string]$contents, [string]$section) {
    $newline = if ($contents.Contains("`r`n")) { "`r`n" } else { "`n" }
    $lines = [regex]::Split($contents, '\r?\n')
    $start = -1
    for ($index = 0; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match '^remote:\s*(?:#.*)?$') {
            if ($start -ge 0) { throw 'Duplicate remote sections in STA config.yml.' }
            $start = $index
        }
    }
    if ($start -lt 0) {
        $body = $contents.TrimEnd([char[]]@("`r", "`n"))
        if ($body.Length -gt 0) { return $body + $newline + $newline + $section + $newline }
        return $section + $newline
    }
    $end = $lines.Count
    for ($index = $start + 1; $index -lt $lines.Count; $index++) {
        if ($lines[$index] -match '^[A-Za-z_][A-Za-z0-9_-]*\s*:') { $end = $index; break }
    }
    $old = ($lines[$start..($end - 1)] -join $newline)
    if ($old -match '(?mi)^\s{2}enabled:\s*(?:true|yes|on|1)\s*(?:#.*)?$') {
        throw 'STA remote is already enabled. Existing credentials are preserved; do not run first-time setup over them.'
    }
    if ($old -match '(?m)^\s{4}[A-Za-z0-9_-]{1,32}:\s*\S') {
        throw 'STA remote already has administrators. Existing credentials are preserved.'
    }
    $prefix = if ($start -eq 0) { @() } else { @($lines[0..($start - 1)]) }
    $suffix = if ($end -ge $lines.Count) { @() } else { @($lines[$end..($lines.Count - 1)]) }
    $replacement = @($section -split '\r?\n')
    return (($prefix + $replacement + $suffix) -join $newline).TrimEnd([char[]]@("`r", "`n")) + $newline
}

$server = (Resolve-Path -LiteralPath $ServerRoot).Path
$launch = Join-Path $server $LaunchScript
if (!(Test-Path -LiteralPath $launch -PathType Leaf)) { throw "Server launch script not found: $launch" }
if ([IO.File]::ReadAllText($launch) -match '(?im)^\s*set\s+["'']?SKYRAIL_STA_KEYSTORE_PASSWORD\s*=') {
    throw 'run.bat already sets the STA keystore password. Keep the existing setup or remove that assignment first.'
}
if ($BindAddress -notmatch '^[A-Za-z0-9_.:-]{1,253}$') { throw 'Invalid bind address.' }
if ([string]::IsNullOrWhiteSpace($ClientHost)) {
    if ($BindAddress -eq '0.0.0.0' -or $BindAddress -eq '::') {
        throw 'Pass -ClientHost with the server address reachable by the desktop dispatcher.'
    }
    $ClientHost = $BindAddress
}
if ($ClientHost -notmatch '^[A-Za-z0-9_.:-]{1,253}$' -or $ClientHost -eq '0.0.0.0' -or $ClientHost -eq '::') {
    throw 'Invalid client host.'
}

if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $javaMatch = [regex]::Match([IO.File]::ReadAllText($launch), '(?i)"([A-Za-z]:\\[^"\r\n]+\\bin\\java(?:\.exe)?)"')
    if ($javaMatch.Success -and (Test-Path -LiteralPath $javaMatch.Groups[1].Value -PathType Leaf)) {
        $JavaHome = Split-Path -Parent (Split-Path -Parent $javaMatch.Groups[1].Value)
    }
}
if ([string]::IsNullOrWhiteSpace($JavaHome)) {
    $command = Get-Command keytool.exe -ErrorAction SilentlyContinue
    if ($null -eq $command) { throw 'Pass -JavaHome pointing to JDK 25.' }
    $keytool = $command.Source
} else {
    $keytool = Join-Path (Resolve-Path -LiteralPath $JavaHome).Path 'bin\keytool.exe'
}
if (!(Test-Path -LiteralPath $keytool -PathType Leaf)) { throw "keytool not found: $keytool" }

$pluginDir = Join-Path $server 'plugins\SkyworldTrainAPI'
New-Item -ItemType Directory -Force -Path $pluginDir | Out-Null
$config = Join-Path $pluginDir 'config.yml'
$keystore = Join-Path $pluginDir 'remote-server.p12'
$secret = Join-Path $pluginDir '.sta-remote-password.dpapi'
$profile = Join-Path $pluginDir 'SkyRail-Dispatcher-profile.json'
$runner = Join-Path $server 'start-sta-remote.ps1'
$shortcut = Join-Path $server 'start-sta-remote.cmd'
foreach ($path in @($keystore, $secret, $runner, $shortcut)) {
    if (Test-Path -LiteralPath $path) { throw "Existing STA remote setup detected: $path. No files were replaced." }
}

$password = New-RandomText 48
$token = New-RandomText 48
$digest = Get-Sha256Text $token
$stamp = [Guid]::NewGuid().ToString('N')
$temporaryKey = Join-Path $pluginDir ("remote-server.$stamp.tmp.p12")
$temporaryCert = Join-Path $pluginDir ("remote-server.$stamp.tmp.der")
$temporaryConfig = Join-Path $pluginDir ("config.$stamp.tmp.yml")
$temporarySecret = Join-Path $pluginDir ("password.$stamp.tmp.dpapi")
$temporaryProfile = Join-Path $pluginDir ("profile.$stamp.tmp.json")
$temporaryRunner = Join-Path $server ("start-sta-remote.$stamp.tmp.ps1")
$temporaryShortcut = Join-Path $server ("start-sta-remote.$stamp.tmp.cmd")
$encoding = New-Object Text.UTF8Encoding($false)
$created = New-Object 'System.Collections.Generic.List[string]'

try {
    $env:SKYRAIL_STA_SETUP_PASSWORD = $password
    try {
        & $keytool -genkeypair -alias sta-remote -keyalg RSA -keysize 3072 -validity 3650 `
            -storetype PKCS12 -keystore $temporaryKey -dname 'CN=SkyRail-STA' `
            -storepass:env SKYRAIL_STA_SETUP_PASSWORD -keypass:env SKYRAIL_STA_SETUP_PASSWORD -noprompt
        if ($LASTEXITCODE -ne 0) { throw 'keytool could not create the PKCS#12 keystore.' }
        & $keytool -exportcert -alias sta-remote -storetype PKCS12 -keystore $temporaryKey `
            -file $temporaryCert -storepass:env SKYRAIL_STA_SETUP_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'keytool could not export the server certificate.' }
    } finally { Remove-Item Env:SKYRAIL_STA_SETUP_PASSWORD -ErrorAction SilentlyContinue }
    $fingerprint = (Get-FileHash -LiteralPath $temporaryCert -Algorithm SHA256).Hash.ToLowerInvariant()
    $section = @(
        'remote:',
        '  enabled: true',
        "  bind-address: $BindAddress",
        "  port: $Port",
        '  keystore: remote-server.p12',
        '  keystore-password-env: SKYRAIL_STA_KEYSTORE_PASSWORD',
        '  admins:',
        "    ${AdminId}: `"$digest`""
    ) -join "`n"
    $oldConfig = if (Test-Path -LiteralPath $config -PathType Leaf) { [IO.File]::ReadAllText($config) } else { '' }
    $newConfig = Set-RemoteSection $oldConfig $section
    [IO.File]::WriteAllText($temporaryConfig, $newConfig, $encoding)

    $securePassword = ConvertTo-SecureString $password -AsPlainText -Force
    [IO.File]::WriteAllText($temporarySecret, (ConvertFrom-SecureString $securePassword) + "`n", $encoding)
    $publicProfile = [ordered]@{
        schemaVersion = 1
        host = $ClientHost
        port = $Port
        fingerprint = $fingerprint
        admin = $AdminId
    }
    [IO.File]::WriteAllText($temporaryProfile, ($publicProfile | ConvertTo-Json -Depth 2) + "`n", $encoding)

$launcherText = @'
$ErrorActionPreference = 'Stop'
Import-Module (Join-Path $PSHOME 'Modules\Microsoft.PowerShell.Security\Microsoft.PowerShell.Security.psd1') -ErrorAction Stop
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$secret = Join-Path $root 'plugins\SkyworldTrainAPI\.sta-remote-password.dpapi'
try {
    $secure = (Get-Content -LiteralPath $secret -Raw).Trim() | ConvertTo-SecureString
} catch {
    throw 'Cannot decrypt the STA Remote keystore password. Start under the Windows account that ran setup.'
}
$handle = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try {
    $env:SKYRAIL_STA_KEYSTORE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($handle)
    Push-Location $root
    try {
        & (Join-Path $root '__LAUNCH__')
        $result = $LASTEXITCODE
    } finally { Pop-Location }
    exit $result
} finally {
    Remove-Item Env:SKYRAIL_STA_KEYSTORE_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($handle)
}
'@.Replace('__LAUNCH__', $LaunchScript)
    [IO.File]::WriteAllText($temporaryRunner, $launcherText, $encoding)
    $shortcutText = '@echo off' + "`r`n" + 'cd /d "%~dp0"' + "`r`n" +
        'powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-sta-remote.ps1"' + "`r`n" +
        'set "STA_REMOTE_EXIT=%errorlevel%"' + "`r`n" +
        'if not "%STA_REMOTE_EXIT%"=="0" pause' + "`r`n" +
        'exit /b %STA_REMOTE_EXIT%' + "`r`n"
    [IO.File]::WriteAllText($temporaryShortcut, $shortcutText, $encoding)

    [IO.File]::Move($temporaryKey, $keystore); $created.Add($keystore)
    [IO.File]::Move($temporarySecret, $secret); $created.Add($secret)
    [IO.File]::Move($temporaryProfile, $profile); $created.Add($profile)
    [IO.File]::Move($temporaryRunner, $runner); $created.Add($runner)
    [IO.File]::Move($temporaryShortcut, $shortcut); $created.Add($shortcut)
    if (Test-Path -LiteralPath $config -PathType Leaf) {
        $backup = Join-Path $pluginDir ("config.yml.sta-setup-$stamp.bak")
        [IO.File]::Replace($temporaryConfig, $config, $backup)
    } else { [IO.File]::Move($temporaryConfig, $config) }
    Write-Host ''
    Write-Host 'STA Remote configured. Start the server with start-sta-remote.cmd.'
    Write-Host "Client profile (contains no token): $profile"
    Write-Host "Certificate SHA-256: $fingerprint"
    Write-Host "Administrator ID: $AdminId"
    Write-Host "Personal token (shown once; copy it privately): $token"
    Write-Host 'Copy the client profile next to sta_dispatcher.py, the .pyz or the .exe; enter the token when connecting.'
    Write-Host 'The launcher must run under the same Windows account that ran this setup.'
} catch {
    foreach ($path in $created) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
    }
    throw
} finally {
    $password = $null
    $token = $null
    foreach ($path in @($temporaryKey, $temporaryCert, $temporaryConfig, $temporarySecret,
            $temporaryProfile, $temporaryRunner, $temporaryShortcut)) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
    }
}
