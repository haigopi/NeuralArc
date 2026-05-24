# NeuralArc Windows Release 1.2.3

## Artifact
- File: NeuralArc-1.2.3.exe
- Path: artifacts/windows/NeuralArc-1.2.3.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-1.2.3.exe" -Algorithm SHA256

## Changes
- Scope polling by mode and heal failed exposure (7ab8a36)
- Add live-service promotion and stream UI tweaks (065a267)
- Add uninstall service & mode-aware trading UI (fe1cdb9)

