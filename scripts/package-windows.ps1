$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectDir = Split-Path -Parent $ScriptDir

$AppName = "NeuralArc"
$MainClass = "com.neuralarc.NeuralArc"
$RawVersion = $null
$LogoPng = Join-Path $ProjectDir "src\main\resources\logo.png"
$IconDir = Join-Path $ProjectDir "build\jpackage\windows"
$IconPng = Join-Path $IconDir "logo-256.png"
$IconIco = Join-Path $IconDir "NeuralArc.ico"
if ($args.Length -gt 0) {
    $RawVersion = $args[0]
}

if (-not $RawVersion) {
    $RawVersion = (& (Join-Path $ProjectDir "gradlew.bat") -q properties --property version | Select-Object -Last 1).Split()[-1]
}

$VersionArg = [regex]::Match($RawVersion, '^[0-9]+(\.[0-9]+)*').Value
if (-not $VersionArg) {
    throw "Unable to derive a valid numeric app version from [$RawVersion]. Pass an explicit release version, e.g. .\package-windows.ps1 1.0.0"
}

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage is required and was not found in PATH."
}

Set-Location $ProjectDir

& (Join-Path $ProjectDir "gradlew.bat") clean installDist

if ($RawVersion -ne $VersionArg) {
    Write-Host "Using package version $VersionArg derived from project version $RawVersion"
}

New-Item -ItemType Directory -Force -Path $IconDir | Out-Null

Add-Type -AssemblyName System.Drawing
$sourceImage = [System.Drawing.Image]::FromFile($LogoPng)
try {
    $bitmap = New-Object System.Drawing.Bitmap 256, 256
    try {
        $graphics = [System.Drawing.Graphics]::FromImage($bitmap)
        try {
            $graphics.Clear([System.Drawing.Color]::Transparent)
            $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
            $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
            $graphics.DrawImage($sourceImage, 0, 0, 256, 256)
        } finally {
            $graphics.Dispose()
        }
        $bitmap.Save($IconPng, [System.Drawing.Imaging.ImageFormat]::Png)
    } finally {
        $bitmap.Dispose()
    }
} finally {
    $sourceImage.Dispose()
}

[byte[]]$pngBytes = [System.IO.File]::ReadAllBytes($IconPng)
$fileStream = [System.IO.File]::Open($IconIco, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write)
try {
    $writer = New-Object System.IO.BinaryWriter($fileStream)
    try {
        $writer.Write([UInt16]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]1)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]32)
        $writer.Write([UInt32]$pngBytes.Length)
        $writer.Write([UInt32]22)
        $writer.Write($pngBytes)
    } finally {
        $writer.Dispose()
    }
} finally {
    $fileStream.Dispose()
}

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
  --icon $IconIco `
  --vendor "NeuralArc" `
  --copyright "Copyright © 2026 NeuralArc | Patent Pending."

Write-Host "Windows installer created in $DestDir"
