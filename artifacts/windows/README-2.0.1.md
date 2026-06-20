# NeuralArc Windows Release 2.0.1

## Artifact
- File: NeuralArc-2.0.1.exe
- Path: artifacts/windows/NeuralArc-2.0.1.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-2.0.1.exe" -Algorithm SHA256

## Changes
- Merge pull request #55 from haigopi/claude/zen-ptolemy-9e2n8c (664c016)
- Add Swing Vault strategy end-to-end; document strategy playbook in FAQs (d70dfa3)
- Merge pull request #54 from haigopi/claude/vwap-desk-strategy (ea14582)
- Add VWAP Desk strategy end-to-end (mean-reversion around intraday VWAP) (b2cafda)

