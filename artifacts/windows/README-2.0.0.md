# NeuralArc Windows Release 2.0.0

## Artifact
- File: NeuralArc-2.0.0.exe
- Path: artifacts/windows/NeuralArc-2.0.0.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-2.0.0.exe" -Algorithm SHA256

## Changes
- Merge pull request #53 from haigopi/codex/persist-diphunter-and-orb-engine-strategies (f3ac100)
- Restore ORB and Dip Hunter schedules from persistence (ab00ba9)
- Merge pull request #52 from haigopi/claude/risk-dashboard-redesign (6a00bf0)
- Redesign Risk Dashboard as a chart-based dashboard; move launcher to header (506d0dc)
- Strategy Updates (69952bf)
- Merge pull request #51 from haigopi/claude/dip-hunter-strategy (04abdcf)
- Add Dip Hunter strategy end-to-end; disable unimplemented strategies (fffe448)

