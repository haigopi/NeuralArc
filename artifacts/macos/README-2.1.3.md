# NeuralArc macOS Release 2.1.3

## Artifact
- File: NeuralArc-2.1.3.dmg
- Path: artifacts/macos/NeuralArc-2.1.3.dmg

## Install
1. Open the DMG file.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  shasum -a 256 artifacts/macos/NeuralArc-2.1.3.dmg

## Changes
- Add pending base-buy cleanup and failure detection (64d362f)
- Add pending base buy placement actions (a944b23)
- Improve expiry resubmits and scanner UX (f065d3c)

