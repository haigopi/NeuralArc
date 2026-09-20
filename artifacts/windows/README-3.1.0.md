# NeuralArc Windows Release 3.1.0

## Artifact
- File: NeuralArc-3.1.0.exe
- Path: artifacts/windows/NeuralArc-3.1.0.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-3.1.0.exe" -Algorithm SHA256

## Changes
- Fix short P&L metrics and summary wrapping (6fb64d5)
- Track account equity and workspace value intraday (46fff57)

