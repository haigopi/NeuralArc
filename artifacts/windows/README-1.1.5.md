# NeuralArc Windows Release 1.1.5

## Artifact
- File: NeuralArc-1.1.5.exe
- Path: artifacts/windows/NeuralArc-1.1.5.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-1.1.5.exe" -Algorithm SHA256

## Changes
- Mark expired orders failed; market-closed pause (3f64ab8)
- Deduplicate strategy polls and extract loader (ef1cd85)

