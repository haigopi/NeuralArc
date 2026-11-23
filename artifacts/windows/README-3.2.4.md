# NeuralArc Windows Release 3.2.4

## Artifact
- File: NeuralArc-3.2.4.exe
- Path: artifacts/windows/NeuralArc-3.2.4.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-3.2.4.exe" -Algorithm SHA256

## Changes
- Keep loss-buy levels resting between polls (fbef51d)
- Rearm edited filled loss-buy levels (20f9ea7)
- Rest sell triggers as broker limit orders (6bc9a4b)

