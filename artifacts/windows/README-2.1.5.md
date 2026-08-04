# NeuralArc Windows Release 2.1.5

## Artifact
- File: NeuralArc-2.1.5.exe
- Path: artifacts/windows/NeuralArc-2.1.5.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-2.1.5.exe" -Algorithm SHA256

## Changes
- Throttle Gap Rocket market-data requests (ae5070d)
- Add color-based pending buy cancel actions (3d0beba)

