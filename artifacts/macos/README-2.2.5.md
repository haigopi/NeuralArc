# NeuralArc macOS Release 2.2.5

## Artifact
- File: NeuralArc-2.2.5.dmg
- Path: artifacts/macos/NeuralArc-2.2.5.dmg

## Install
1. Open the DMG file.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  shasum -a 256 artifacts/macos/NeuralArc-2.2.5.dmg

## Changes
- Batch strategy polling and add validation UI (e7076df)
- Purge hidden stale strategies on refresh (014da35)

