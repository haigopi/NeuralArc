# NeuralArc macOS Release 1.2.3

## Artifact
- File: NeuralArc-1.2.3.dmg
- Path: artifacts/macos/NeuralArc-1.2.3.dmg

## Install
1. Open the DMG file.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  shasum -a 256 artifacts/macos/NeuralArc-1.2.3.dmg

## Changes
- Scope polling by mode and heal failed exposure (7ab8a36)
- Add live-service promotion and stream UI tweaks (065a267)
- Add uninstall service & mode-aware trading UI (fe1cdb9)

