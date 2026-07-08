# NeuralArc Windows Release 2.1.1

## Artifact
- File: NeuralArc-2.1.1.exe
- Path: artifacts/windows/NeuralArc-2.1.1.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-2.1.1.exe" -Algorithm SHA256

## Changes
- Add per-workspace strategy scan history (bf08057)
- Fix workspace capital and tab count headings (80fbd3e)
- Handle manual buy failures and highlight errors (f460377)

