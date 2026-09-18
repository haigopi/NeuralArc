# NeuralArc Windows Release 3.0.3

## Artifact
- File: NeuralArc-3.0.3.exe
- Path: artifacts/windows/NeuralArc-3.0.3.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-3.0.3.exe" -Algorithm SHA256

## Changes
- Fix broker adoption to check both modes on refresh (585b57e)
- Fix short position handling across engine and UI (a102655)
- Harden sell accounting and broker sync adoption (1960dd1)

