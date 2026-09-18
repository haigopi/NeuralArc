# NeuralArc macOS Release 3.0.3

## Artifact
- File: NeuralArc-3.0.3.dmg
- Path: artifacts/macos/NeuralArc-3.0.3.dmg

## Install
1. Open the DMG file.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  shasum -a 256 artifacts/macos/NeuralArc-3.0.3.dmg

## Changes
- Fix broker adoption to check both modes on refresh (585b57e)
- Fix short position handling across engine and UI (a102655)
- Harden sell accounting and broker sync adoption (1960dd1)

