[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ServerRoot,
    [Parameter(Mandatory = $true)][string]$ServerRoot26_3,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$ServerJarRelativePath = 'versions/26.2/shiroha-26.2.jar',
    [string]$ServerJarRelativePath26_3 = 'versions/26.3/shiroha-26.3.jar'
)
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Set JAVA_HOME or pass -JavaHome (JDK 25).' }
$server = (Resolve-Path -LiteralPath $ServerRoot).Path
$server26_3 = (Resolve-Path -LiteralPath $ServerRoot26_3).Path
$suffix = if ($env:OS -eq 'Windows_NT') { '.exe' } else { '' }
$javac = Join-Path $JavaHome "bin/javac$suffix"
$java = Join-Path $JavaHome "bin/java$suffix"
$jar = Join-Path $JavaHome "bin/jar$suffix"
$serverJar = Join-Path $server $ServerJarRelativePath
$libraries = Join-Path $server 'libraries'
$serverJar26_3 = Join-Path $server26_3 $ServerJarRelativePath26_3
$libraries26_3 = Join-Path $server26_3 'libraries'
foreach ($required in @($javac, $java, $jar, $serverJar, $libraries, $serverJar26_3, $libraries26_3)) {
    if (!(Test-Path -LiteralPath $required)) { throw "Missing build dependency: $required" }
}
$separator = [IO.Path]::PathSeparator
$dependencies = @($serverJar) + @(Get-ChildItem -LiteralPath $libraries -Recurse -Filter '*.jar' | ForEach-Object FullName)
$dependencies26_3 = @($serverJar26_3) + @(Get-ChildItem -LiteralPath $libraries26_3 -Recurse -Filter '*.jar' | ForEach-Object FullName)
$build = Join-Path $PSScriptRoot ('target/build-' + [Guid]::NewGuid().ToString('N'))
$artifacts = Join-Path $PSScriptRoot 'artifacts'
New-Item -ItemType Directory -Force -Path $build,$artifacts | Out-Null
$shared = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'shared/src/main/java') -Recurse -Filter '*.java')
$builtJars = @()
$testCount = 0
$testCount26_3 = 0

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
    if ($module -eq 'SkyTrainFolia') {
        $adapter26_2 = Join-Path $root 'src/protocol-26.2/java/net/skyworld/skytrain/TrainDisplayPacketAdapter_26_2.java'
        $adapter26_3 = Join-Path $root 'src/protocol-26.3/java/net/skyworld/skytrain/TrainDisplayPacketAdapter_26_3.java'
        $sources += @(Get-Item -LiteralPath $adapter26_2)
    }
    Compile-Java -Sources @($sources | ForEach-Object FullName) -Classpath $classpath -Output $classes -ArgsFile (Join-Path $build "$module-main.args")
    if ($module -eq 'SkyTrainFolia') {
        $classes26_3 = Join-Path $build 'SkyTrainFolia/adapter-build-26.3'
        $sources26_3 = @($sources | Where-Object FullName -ne $adapter26_2) + @(Get-Item -LiteralPath $adapter26_3)
        Compile-Java -Sources @($sources26_3 | ForEach-Object FullName) -Classpath (($dependencies26_3 + $builtJars) -join $separator) -Output $classes26_3 -ArgsFile (Join-Path $build 'adapter-build-26.3.args')
        $adapterBytecode = Join-Path $classes26_3 'net/skyworld/skytrain/TrainDisplayPacketAdapter_26_3.class'
        Copy-Item -LiteralPath $adapterBytecode -Destination (Join-Path $classes 'net/skyworld/skytrain')
    }
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

# Recompile the same sources against 26.3 and run an independent test suite. The
# distributable JARs above already contain both protocol adapters; this pass only validates.
$validated26_3 = @()
foreach ($module in @('STA','SkyTrainFolia','STCS','SkyPCC')) {
    $root = Join-Path $PSScriptRoot $module
    $classes = Join-Path $build "$module/classes-26.3"
    $classpath = ($dependencies26_3 + $validated26_3) -join $separator
    $sources = @(Get-ChildItem -LiteralPath (Join-Path $root 'src/main/java') -Recurse -Filter '*.java') + $shared
    if ($module -eq 'SkyTrainFolia') {
        $adapter26_3 = Join-Path $root 'src/protocol-26.3/java/net/skyworld/skytrain/TrainDisplayPacketAdapter_26_3.java'
        $sources += @(Get-Item -LiteralPath $adapter26_3)
    }
    Compile-Java -Sources @($sources | ForEach-Object FullName) -Classpath $classpath -Output $classes -ArgsFile (Join-Path $build "$module-main-26.3.args")
    Get-ChildItem -LiteralPath (Join-Path $root 'src/main/resources') | Copy-Item -Destination $classes -Recurse
    $validationJar = Join-Path $build "$module-validation-26.3.jar"
    & $jar --create --file $validationJar -C $classes .
    if ($LASTEXITCODE -ne 0) { throw "26.3 validation packaging failed: $module" }
    $testRoot = Join-Path $root 'src/test/java'
    if (Test-Path -LiteralPath $testRoot) {
        $tests = @(Get-ChildItem -LiteralPath $testRoot -Recurse -Filter '*.java' |
                Where-Object { $module -ne 'SkyTrainFolia' -or $_.Name -ne 'TrainDisplayConnectionTest.java' })
        if ($module -eq 'SkyTrainFolia') {
            $tests += @(Get-ChildItem -LiteralPath (Join-Path $root 'src/test-26.3/java') -Recurse -Filter '*.java')
        }
        if ($tests.Count -gt 0) {
            $testClasses = Join-Path $build "$module/test-classes-26.3"
            $testClasspath = (@($validationJar) + $dependencies26_3 + $validated26_3) -join $separator
            Compile-Java -Sources @($tests | ForEach-Object FullName) -Classpath $testClasspath -Output $testClasses -ArgsFile (Join-Path $build "$module-tests-26.3.args")
            $working = Join-Path $build "$module/test-work-26.3"
            New-Item -ItemType Directory -Force -Path $working | Out-Null
            Push-Location $working
            try {
                foreach ($test in $tests) {
                    if ((Get-Content -LiteralPath $test.FullName -Raw) -notmatch 'public\s+static\s+void\s+main\s*\(') { continue }
                    $sourceRoot = if ($test.FullName -like '*test-26.3*') { Join-Path $root 'src/test-26.3/java' } else { $testRoot }
                    $class = $test.FullName.Substring($sourceRoot.Length + 1).Replace('\','.').Replace('/','.').Replace('.java','')
                    & $java -ea -cp ($testClasses + $separator + $testClasspath) $class
                    if ($LASTEXITCODE -ne 0) { throw "26.3 test failed: $class" }
                    $testCount26_3++
                    Write-Output "PASS 26.3 $class"
                }
            } finally { Pop-Location }
        }
    }
    $validated26_3 += $validationJar
    Write-Output "Validated $module against Shiroha 26.3"
}
$hashes = @($builtJars | ForEach-Object { @{ file = [IO.Path]::GetFileName($_); sha256 = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash } })
$hashes | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $artifacts 'SHA256.json') -Encoding utf8
Write-Output "Complete: four plugins, $testCount tests on 26.2 and $testCount26_3 tests on 26.3. No server files were modified."
