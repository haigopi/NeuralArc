# NeuralArc Windows Release 2.2.1

## Artifact
- File: NeuralArc-2.2.1.exe
- Path: artifacts/windows/NeuralArc-2.2.1.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-2.2.1.exe" -Algorithm SHA256

## Changes
- Relax GapRocket defaults and fix catalyst gating (207d117)
- Loosen strategy defaults and guard refresh (437fb94)
- Release NOtes (cde432d)

