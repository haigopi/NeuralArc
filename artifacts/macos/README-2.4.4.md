# NeuralArc macOS Release 2.4.4

## Artifact
- File: NeuralArc-2.4.4.dmg
- Path: artifacts/macos/NeuralArc-2.4.4.dmg

## Install
1. Open the DMG file.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  shasum -a 256 artifacts/macos/NeuralArc-2.4.4.dmg

## Changes
- Fix profit-hold and limit-order status labels (c199993)
- Add bulk sell-trigger and profit-threshold actions (791dcdb)

