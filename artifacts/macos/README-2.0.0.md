# NeuralArc macOS Release 2.0.0

## Artifact
- File: NeuralArc-2.0.0.dmg
- Path: artifacts/macos/NeuralArc-2.0.0.dmg

## Install
1. Open the DMG file.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  shasum -a 256 artifacts/macos/NeuralArc-2.0.0.dmg

## Changes
- Merge pull request #53 from haigopi/codex/persist-diphunter-and-orb-engine-strategies (f3ac100)
- Restore ORB and Dip Hunter schedules from persistence (ab00ba9)
- Merge pull request #52 from haigopi/claude/risk-dashboard-redesign (6a00bf0)
- Redesign Risk Dashboard as a chart-based dashboard; move launcher to header (506d0dc)
- Strategy Updates (69952bf)
- Merge pull request #51 from haigopi/claude/dip-hunter-strategy (04abdcf)
- Add Dip Hunter strategy end-to-end; disable unimplemented strategies (fffe448)

