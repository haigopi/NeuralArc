# NeuralArc Windows Release 2.1.0

## Artifact
- File: NeuralArc-2.1.0.exe
- Path: artifacts/windows/NeuralArc-2.1.0.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-2.1.0.exe" -Algorithm SHA256

## Changes
- Scope duplicate-symbol policy by workspace (ad98660)
- Update TradingFrame.java (afa04d2)
- Deafultign Stratigies (0db161c)

