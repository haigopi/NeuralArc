# NeuralArc macOS Release 2.2.1

## Artifact
- File: NeuralArc-2.2.1.dmg
- Path: artifacts/macos/NeuralArc-2.2.1.dmg

## Install
1. Open the DMG file.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  shasum -a 256 artifacts/macos/NeuralArc-2.2.1.dmg

## Changes
- Relax GapRocket defaults and fix catalyst gating (207d117)
- Loosen strategy defaults and guard refresh (437fb94)
- Release NOtes (cde432d)

