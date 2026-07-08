# NeuralArc macOS Release 2.1.1

## Artifact
- File: NeuralArc-2.1.1.dmg
- Path: artifacts/macos/NeuralArc-2.1.1.dmg

## Install
1. Open the DMG file.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  shasum -a 256 artifacts/macos/NeuralArc-2.1.1.dmg

## Changes
- Add per-workspace strategy scan history (bf08057)
- Fix workspace capital and tab count headings (80fbd3e)
- Handle manual buy failures and highlight errors (f460377)

