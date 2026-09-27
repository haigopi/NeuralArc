# NeuralArc Windows Release 3.1.1

## Artifact
- File: NeuralArc-3.1.1.exe
- Path: artifacts/windows/NeuralArc-3.1.1.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-3.1.1.exe" -Algorithm SHA256

## Changes
- Introducing the AI Agent (69dbf0e)
- Prune duplicated order events; add re-entry UI (f620df8)
- Prune routine events and clean archived entries (cb04c93)
- Staged buy cancels and Smart Picks fixes (55b78d5)
- Add Smart Picks workspace schedules and automation (eb030a4)
- Quantity Relavant Bug Fixes (fe6a0cf)

