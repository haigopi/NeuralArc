# NeuralArc Windows Release 2.5.3

## Artifact
- File: NeuralArc-2.5.3.exe
- Path: artifacts/windows/NeuralArc-2.5.3.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-2.5.3.exe" -Algorithm SHA256

## Changes
- Fix target sell replacement handling (0d8b22c)
- Support avg-down with working target sells (61798e4)
- Fix average-down flow and symbol ownership (eaf3984)
- Harden ticker list parsing for pasted text (d61bd7f)
- Harden stock import parsing and add import logs (09c63ce)
- Remove liquidation P&L from status counters (d8ca295)
- Fix monitor P&L basis and status line fitting (db9c74f)

