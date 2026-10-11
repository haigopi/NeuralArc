# NeuralArc Windows Release 3.1.3

## Artifact
- File: NeuralArc-3.1.3.exe
- Path: artifacts/windows/NeuralArc-3.1.3.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-3.1.3.exe" -Algorithm SHA256

## Changes
- Add pre-open reposition + bulk progress flows (94d2509)
- Add foldable trade-history groups and UX fixes (ad601bc)
- AI Analyst polish and poll-failure resilience (208d933)

