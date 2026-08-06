# NeuralArc Windows Release 2.2.5

## Artifact
- File: NeuralArc-2.2.5.exe
- Path: artifacts/windows/NeuralArc-2.2.5.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-2.2.5.exe" -Algorithm SHA256

## Changes
- Batch strategy polling and add validation UI (e7076df)
- Purge hidden stale strategies on refresh (014da35)

