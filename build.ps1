[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ServerRoot,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$ServerJarRelativePath = 'versions/26.2/shiroha-26.2.jar'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Set JAVA_HOME or pass -JavaHome (JDK 25).' }
$server = (Resolve-Path -LiteralPath $ServerRoot).Path
$suffix = if ($env:OS -eq 'Windows_NT') { '.exe' } else { '' }
$javac = Join-Path $JavaHome "bin/javac$suffix"
$java = Join-Path $JavaHome "bin/java$suffix"
$jar = Join-Path $JavaHome "bin/jar$suffix"
$serverJar = Join-Path $server $ServerJarRelativePath
$libraries = Join-Path $server 'libraries'
foreach ($required in @($javac, $java, $jar, $serverJar, $libraries)) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing build dependency: $required" }
}
$separator = [IO.Path]::PathSeparator
$dependencies = @($serverJar) + @(Get-ChildItem -LiteralPath $libraries -Recurse -Filter '*.jar' | ForEach-Object FullName)
$build = Join-Path $PSScriptRoot ('target/build-' + [Guid]::NewGuid().ToString('N'))
$artifacts = Join-Path $PSScriptRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $build,$artifacts | Out-Null
$shared = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'shared/src/main/java') -Recurse -Filter '*.java')
$builtJars = @()
$testCount = 0

function Compile-Java([string[]]$Sources, [string]$Classpath, [string]$Output, [string]$ArgsFile) {
    New-Item -ItemType Directory -Force -Path $Output | Out-Null
    $arguments = @('-encoding','UTF-8','--release','25','-cp',('"' + $Classpath.Replace('\','/') + '"'),'-d',('"' + $Output.Replace('\','/') + '"'))
    $arguments += $Sources | ForEach-Object { '"' + $_.Replace('\','/') + '"' }
    [IO.File]::WriteAllLines($ArgsFile, $arguments, [Text.UTF8Encoding]::new($false))
    & $javac "@$ArgsFile"
    if ($LASTEXITCODE -ne 0) { throw "Java compilation failed: $ArgsFile" }
}

# Each build starts with STA from source; no prebuilt suite JAR is required.
foreach ($module in @('STA','SkyTrainFolia','STCS','SkyPCC')) {
    $root = Join-Path $PSScriptRoot $module
    $metadata = Get-Content -LiteralPath (Join-Path $root 'src/main/resources/plugin.yml') -Raw
    $nameMatch = [regex]::Match($metadata, '(?m)^name:\s*([A-Za-z0-9_-]+)\s*$')
    $versionMatch = [regex]::Match($metadata, '(?m)^version:\s*([A-Za-z0-9_.-]+)\s*$')
    if (!$nameMatch.Success -or !$versionMatch.Success) { throw "Unsupported plugin metadata in $module" }
    $name = $nameMatch.Groups[1].Value
    $version = $versionMatch.Groups[1].Value
    $classes = Join-Path $build "$module/classes"
    $classpath = ($dependencies + $builtJars) -join $separator
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src/main/java') -Recurse -Filter '*.java') + $shared
    Compile-Java -Sources @($sources | ForEach-Object FullName) -Classpath $classpath -Output $classes -ArgsFile (Join-Path $build "$module-main.args")
    Get-ChildItem -LiteralPath (Join-Path $root 'src/main/resources') | Copy-Item -Destination $classes -Recurse
    $legal = Join-Path $classes 'META-INF'
    New-Item -ItemType Directory -Force -Path $legal | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'LICENSE') -Destination (Join-Path $legal 'LICENSE-SkyRail-Suite.txt')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'ASSET-LICENSE.md') -Destination $legal
    $output = Join-Path $artifacts "$name-$version.jar"
    & $jar --create --file $output -C $classes .
    if ($LASTEXITCODE -ne 0) { throw "Packaging failed: $module" }
    $builtJars += $output
    $testRoot = Join-Path $root 'src/test/java'
    if (Test-Path -LiteralPath $testRoot) {
        $tests = @(Get-ChildItem -LiteralPath $testRoot -Recurse -Filter '*.java')
        if ($tests.Count -gt 0) {
            $testClasses = Join-Path $build "$module/test-classes"
            $testClasspath = (@($classes) + $dependencies + $builtJars) -join $separator
            Compile-Java -Sources @($tests | ForEach-Object FullName) -Classpath $testClasspath -Output $testClasses -ArgsFile (Join-Path $build "$module-tests.args")
            $working = Join-Path $build "$module/test-work"
            New-Item -ItemType Directory -Force -Path $working | Out-Null
            Push-Location $working
            try {
                foreach ($test in $tests) {
                    if ((Get-Content -LiteralPath $test.FullName -Raw) -notmatch 'public\s+static\s+void\s+main\s*\(') { continue }
                    $class = $test.FullName.Substring($testRoot.Length + 1).Replace('\','.').Replace('/','.').Replace('.java','')
                    & $java -ea -cp ($testClasses + $separator + $testClasspath) $class
                    if ($LASTEXITCODE -ne 0) { throw "Test failed: $class" }
                    $testCount++
                    Write-Output "PASS $class"
                }
            } finally { Pop-Location }
        }
    }
    Write-Output "Built $name $version"
}
$hashes = @($builtJars | ForEach-Object { @{ file = [IO.Path]::GetFileName($_); sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash } })
$hashes | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $artifacts 'SHA256.json') -Encoding utf8
Write-Output "Complete: four plugins, $testCount Java test entry points passed. No server files were modified."
