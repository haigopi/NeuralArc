# NeuralArc Windows Release 1.2.0

## Artifact
- File: NeuralArc-1.2.0.exe
- Path: artifacts/windows/NeuralArc-1.2.0.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-1.2.0.exe" -Algorithm SHA256

## Changes
- Add trade history grouping, filters & buy price (e4cc41f)
- Add rule-trigger history, grid copy, and status handling (78fe829)
- Add 1.1.5 artifacts; refresh watchdog & loader (bb60067)

