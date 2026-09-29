param(
    [string]$Python = 'py',
    [string]$PythonVersion = '-3.12'
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$arguments = @()
if ($Python -eq 'py') { $arguments += $PythonVersion }
$buildDirectory = Join-Path $root 'target\pyinstaller-build'
$specDirectory = Join-Path $root 'target\pyinstaller-spec'
New-Item -ItemType Directory -Force -Path $buildDirectory, $specDirectory | Out-Null

& $Python @arguments -c 'import tkinter as tk; root = tk.Tk(); root.withdraw(); root.destroy()'
if ($LASTEXITCODE -ne 0) { throw 'This Python installation does not have a working Tcl/Tk runtime.' }

& $Python @arguments -m PyInstaller --version
if ($LASTEXITCODE -ne 0) { throw 'Install PyInstaller in this Python first: py -3.12 -m pip install pyinstaller' }

& $Python @arguments -m PyInstaller `
    --noconfirm --onefile --windowed `
    --name 'SkyRail-Dispatcher-2.1.1' `
    --distpath (Join-Path $root 'dist') `
    --workpath $buildDirectory `
    --specpath $specDirectory `
    --paths (Join-Path $root 'STA\tools') `
    --paths (Join-Path $root 'STCS\tools\testbench') `
    (Join-Path $root 'STA\tools\sta_dispatcher.py')
if ($LASTEXITCODE -ne 0) { throw 'PyInstaller packaging failed.' }

Write-Host "Built $(Join-Path $root 'dist\SkyRail-Dispatcher-2.1.1.exe')"
