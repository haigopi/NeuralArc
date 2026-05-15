# NeuralArc Windows Release 1.0.11

## Artifact
- File: NeuralArc-1.0.11.exe
- Path: artifacts/windows/NeuralArc-1.0.11.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-1.0.11.exe" -Algorithm SHA256

## Changes
- Filter movers by trade_count and refine UI labels (4873e95)
- Add clean trade history and stream recovery sync (32ec782)
- Use hasPosition for paused toggle text (e787c1c)
- Return strategyId for streaming order updates (fff359c)
- Add strategy defaults and Lucky review (7aa7d28)

