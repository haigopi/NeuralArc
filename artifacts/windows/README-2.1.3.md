# NeuralArc Windows Release 2.1.3

## Artifact
- File: NeuralArc-2.1.3.exe
- Path: artifacts/windows/NeuralArc-2.1.3.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-2.1.3.exe" -Algorithm SHA256

## Changes
- Add pending base-buy cleanup and failure detection (64d362f)
- Add pending base buy placement actions (a944b23)
- Improve expiry resubmits and scanner UX (f065d3c)

