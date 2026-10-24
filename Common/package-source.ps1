[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression
$root = Split-Path $PSScriptRoot -Parent
$files = @()
foreach ($directory in @('SkyTrainFolia','STCS','STA','SkyPCC','Common/shared','Common/doc')) {
    $files += Get-ChildItem -LiteralPath (Join-Path $root $directory) -Recurse -File |
        Where-Object { $_.FullName.Substring($root.Length + 1) -notmatch '(^|[\\/])(target|artifacts|dist|node_modules|__pycache__|\.git)([\\/]|$)' }
}
foreach ($name in @('.gitignore','.gitattributes','README.md','Common/LICENSE','Common/ASSET-LICENSE.md','Common/logo.png','Common/CHANGELOG.md','Common/README.zh.md','Common/README.en.md','Common/README.fr.md','Common/README.nl.md','Common/README.ja.md','Common/README.ja.classical.md','Common/build.ps1','Common/package-source.ps1')) {
    $files += Get-Item -LiteralPath (Join-Path $root $name)
}
$allowed = @('.java','.yml','.yaml','.js','.cjs','.css','.html','.png','.py','.json','.md','.ps1','.txt','.cmd','.bat')
foreach ($file in $files) {
    if ($file.Name -in @('LICENSE','.gitignore','.gitattributes')) { continue }
    if ($file.Extension -notin $allowed -or $file.Name -match '^(railgraph.*|occupancy-ledger.*)\.json$') {
        throw "Unexpected source-package file; review before packaging: $($file.FullName)"
    }
}
$out = Join-Path $PSScriptRoot 'dist'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$zipPath = Join-Path $out ('SkyRail-Suite-source-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '.zip')
$stream = [IO.File]::Open($zipPath, [IO.FileMode]::CreateNew)
try {
    $zip = [IO.Compression.ZipArchive]::new($stream, [IO.Compression.ZipArchiveMode]::Create, $true)
    try {
        foreach ($file in $files | Sort-Object FullName) {
            $relative = $file.FullName.Substring($root.Length + 1).Replace('\','/')
            $entry = $zip.CreateEntry('SkyRail-Suite/' + $relative)
            $inputStream = [IO.File]::OpenRead($file.FullName)
            $entryStream = $entry.Open()
            try { $inputStream.CopyTo($entryStream) } finally { $entryStream.Dispose(); $inputStream.Dispose() }
        }
    } finally { $zip.Dispose() }
} finally { $stream.Dispose() }
Write-Output "Source package: $zipPath ($($files.Count) files)"
