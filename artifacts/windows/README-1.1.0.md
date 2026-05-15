# NeuralArc Windows Release 1.1.0

## Artifact
- File: NeuralArc-1.1.0.exe
- Path: artifacts/windows/NeuralArc-1.1.0.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-1.1.0.exe" -Algorithm SHA256

## Changes
- Add grid search/filter to strategy and history grids (358900a)
- Limit history sorting and update tab title count (1ccf656)
- Add release READMEs; update release script & UI (bd46bef)

