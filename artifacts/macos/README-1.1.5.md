# NeuralArc macOS Release 1.1.5

## Artifact
- File: NeuralArc-1.1.5.dmg
- Path: artifacts/macos/NeuralArc-1.1.5.dmg

## Install
1. Open the DMG file.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  shasum -a 256 artifacts/macos/NeuralArc-1.1.5.dmg

## Changes
- Mark expired orders failed; market-closed pause (3f64ab8)
- Deduplicate strategy polls and extract loader (ef1cd85)

