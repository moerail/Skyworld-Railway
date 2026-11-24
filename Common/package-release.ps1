[CmdletBinding()]
param([string]$ReleaseName = 'SkyRail-Suite-2026.11.24')
$ErrorActionPreference = 'Stop'
if ($ReleaseName -notmatch '^[A-Za-z0-9.-]+$') { throw 'Invalid release name' }
$root = Split-Path $PSScriptRoot -Parent
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$out = Join-Path $PSScriptRoot ('dist/release-' + $stamp)
$stage = Join-Path $out $ReleaseName
New-Item -ItemType Directory -Path $stage -Force | Out-Null
Add-Type -AssemblyName System.IO.Compression.FileSystem
$source = Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'dist') -Filter 'SkyRail-Suite-source-*.zip' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
if (!$source) { throw 'Run package-source.ps1 first' }
# Expand the source tree too: historical links to modules, tests and build scripts
# stay navigable without asking the recipient to unpack a second archive.
$sourceUnpack = Join-Path $out 'source-unpack'
[IO.Compression.ZipFile]::ExtractToDirectory($source.FullName,$sourceUnpack)
Get-ChildItem -LiteralPath (Join-Path $sourceUnpack 'SkyRail-Suite') -Force | Copy-Item -Destination $stage -Recurse -Force
foreach ($folder in @('Common/doc','STA/tools','STCS/tools')) {
    $destination = Join-Path $stage $folder
    New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
    $sourceDirectory = Join-Path $root $folder
    foreach ($file in Get-ChildItem -LiteralPath $sourceDirectory -File -Recurse) {
        $relative = $file.FullName.Substring($sourceDirectory.Length+1)
        if ($relative -match '(^|[\\/])(target|artifacts|dist|node_modules|__pycache__|\.git)([\\/]|$)') { continue }
        if ($file.Extension -notin @('.md','.py','.ps1','.cmd','.bat','.txt','.json','.yml','.yaml')) { throw "Unexpected documentation/tool file: $relative" }
        $targetFile = Join-Path $destination $relative
        New-Item -ItemType Directory -Force -Path (Split-Path $targetFile -Parent) | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $targetFile
    }
}
foreach ($name in @('README.md','VALIDATION.md','Common/LICENSE','Common/ASSET-LICENSE.md','Common/logo.png','Common/CHANGELOG.md','Common/validate-docs.cjs','SkyATO-Lite/README.md','Latest/SHA256.json')) {
    $destination = Join-Path $stage $name
    New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent) | Out-Null
    Copy-Item -LiteralPath (Join-Path $root $name) -Destination $destination
}
Get-ChildItem -LiteralPath $PSScriptRoot -Filter 'README.*.md' | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination (Join-Path $stage 'Common')
}
$plugins = Join-Path $stage 'plugins'
New-Item -ItemType Directory -Path $plugins | Out-Null
$manifest = Get-Content -LiteralPath (Join-Path $root 'Latest/SHA256.json') -Raw | ConvertFrom-Json
if (@($manifest).Count -ne 5) { throw 'Expected exactly five plugin files' }
foreach ($entry in $manifest) {
    if ($entry.file -notmatch '^(SkyworldTrainAPI|SkyTrainFolia|STCS|SkyPCC|SkyATO-Lite)-[0-9.]+\.jar$') { throw 'Unexpected manifest path' }
    $file = Join-Path (Join-Path $root 'Latest') $entry.file
    if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $entry.sha256) { throw "Build hash mismatch: $file" }
    Copy-Item -LiteralPath $file -Destination $plugins
}
Copy-Item -LiteralPath (Join-Path $root 'Latest/SHA256.json') -Destination $plugins
New-Item -ItemType Directory -Path (Join-Path $stage 'Source') | Out-Null
Copy-Item -LiteralPath $source.FullName -Destination (Join-Path $stage 'Source')
# No server data/configuration, credentials or temporary test outputs enter this allowlist.
& node (Join-Path $PSScriptRoot 'validate-docs.cjs') $stage > (Join-Path $out 'link-check-stage.json')
if ($LASTEXITCODE -ne 0) { throw 'Distribution links failed validation' }
$hashes = @(Get-ChildItem -LiteralPath $stage -Recurse -File | Sort-Object FullName | ForEach-Object {
    (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() + '  ' + $_.FullName.Substring($stage.Length+1).Replace('\','/')
})
[IO.File]::WriteAllLines((Join-Path $stage 'MANIFEST.sha256'),$hashes,[Text.UTF8Encoding]::new($false))
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = Join-Path $out ($ReleaseName + '.zip')
[IO.Compression.ZipFile]::CreateFromDirectory($stage,$zip,[IO.Compression.CompressionLevel]::Optimal,$true)
$unpack = Join-Path $out 'extracted-check'
[IO.Compression.ZipFile]::ExtractToDirectory($zip,$unpack)
$check = Join-Path $unpack $ReleaseName
& node (Join-Path $PSScriptRoot 'validate-docs.cjs') $check > (Join-Path $out 'link-check-extracted.json')
if ($LASTEXITCODE -ne 0) { throw 'Extracted links failed validation' }
foreach ($row in Get-Content -LiteralPath (Join-Path $check 'MANIFEST.sha256')) {
    $pair = $row -split '  ',2
    if ((Get-FileHash -LiteralPath (Join-Path $check $pair[1]) -Algorithm SHA256).Hash.ToLowerInvariant() -ne $pair[0]) { throw "Extracted hash mismatch: $($pair[1])" }
}
$zipHash = (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText(($zip+'.sha256'),$zipHash+'  '+[IO.Path]::GetFileName($zip)+[Environment]::NewLine,[Text.UTF8Encoding]::new($false))
@{ zip=$zip; sha256=$zipHash; stage=$stage; extracted=$check; files=$hashes.Count; source=$source.Name } |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'target/latest-distribution.json') -Encoding utf8
Write-Output "Distribution: $zip"
Write-Output "Validated extracted links and $($hashes.Count) file hashes."
