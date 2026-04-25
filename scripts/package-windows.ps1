$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

$AppName = "NeuralArc"
$MainClass = "com.neuralarc.NeuralArc"
$VersionArg = $null
if ($args.Length -gt 0) {
    $VersionArg = $args[0]
}

if (-not $VersionArg) {
    $VersionArg = (& (Join-Path $ProjectDir "gradlew.bat") -q properties --property version | Select-Object -Last 1).Split()[-1]
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage is required and was not found in PATH."
}

Set-Location $ProjectDir

& (Join-Path $ProjectDir "gradlew.bat") clean installDist

$DistDir = Get-ChildItem (Join-Path $ProjectDir "build\install") -Directory | Select-Object -First 1
if (-not $DistDir) {
    throw "Unable to locate installDist output under build\install"
}

$InputDir = Join-Path $DistDir.FullName "lib"
$DestDir = Join-Path $ProjectDir "build\installer\windows"
New-Item -ItemType Directory -Force -Path $DestDir | Out-Null

$MainJar = Get-ChildItem $InputDir -Filter *.jar | Where-Object { $_.Name -notlike "*-plain*" } | Select-Object -First 1
if (-not $MainJar) {
    throw "Unable to locate packaged application jar in $InputDir"
}

jpackage `
  --type exe `
  --name $AppName `
  --dest $DestDir `
  --input $InputDir `
  --main-jar $MainJar.Name `
  --main-class $MainClass `
  --app-version $VersionArg `
  --vendor "NeuralArc" `
  --copyright "Copyright © 2026 NeuralArc. Patent Pending."

Write-Host "Windows installer created in $DestDir"
