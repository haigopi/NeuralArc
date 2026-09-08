# NeuralArc Windows Release 2.5.0

## Artifact
- File: NeuralArc-2.5.0.exe
- Path: artifacts/windows/NeuralArc-2.5.0.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-2.5.0.exe" -Algorithm SHA256

## Changes
- Reconcile shared broker positions (3bf4eb6)
- Refine connection checks and portfolio actions (11eed64)
- Import Manula Buys (f4ae1e3)
- Entry Soucres (553cd42)
- Gap Roket Updates (c66247b)
- minor (6e497f2)
- Fix strategy fill sync and trade history UX (922b125)
- Suppress dev build updates and test CPU text (d1c38ad)
- Add Profit Shield strategy workflow (15eafa5)

