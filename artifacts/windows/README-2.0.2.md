# NeuralArc Windows Release 2.0.2

## Artifact
- File: NeuralArc-2.0.2.exe
- Path: artifacts/windows/NeuralArc-2.0.2.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-2.0.2.exe" -Algorithm SHA256

## Changes
- MINOR UPDATES (e7e54c9)
- Merge pull request #58 from haigopi/claude/admiring-wu-320edf (74cf77c)
- Remove custom JLabel tab styling from dialogs to match native strategy tabs (1f16c71)
- Merge pull request #57 from haigopi/claude/auto-adjust-risk-stop-loss (1a396a8)
- Add Auto Adjust Risk & Stop Loss feature to the New Strategy dialog (5c5c61e)
- Merge pull request #56 from haigopi/claude/help-faq-tab-styling (62e2c9a)
- Style Help & FAQ tabs to match app strategy tabs; expand strategy abbreviations (ce2fb51)

