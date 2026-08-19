# NeuralArc Windows Release 2.4.4

## Artifact
- File: NeuralArc-2.4.4.exe
- Path: artifacts/windows/NeuralArc-2.4.4.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-2.4.4.exe" -Algorithm SHA256

## Changes
- Fix profit-hold and limit-order status labels (c199993)
- Add bulk sell-trigger and profit-threshold actions (791dcdb)

