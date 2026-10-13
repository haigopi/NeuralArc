# NeuralArc Windows Release 3.2.1

## Artifact
- File: NeuralArc-3.2.1.exe
- Path: artifacts/windows/NeuralArc-3.2.1.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-3.2.1.exe" -Algorithm SHA256

## Changes
- Add broker fill reconciliation (e73f694)
- Multi-arch mac builds, download UI & logging (016c46e)

