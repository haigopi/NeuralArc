# NeuralArc macOS Release 2.5.3

## Artifact
- File: NeuralArc-2.5.3.dmg
- Path: artifacts/macos/NeuralArc-2.5.3.dmg

## Install
1. Open the DMG file.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  shasum -a 256 artifacts/macos/NeuralArc-2.5.3.dmg

## Changes
- Fix target sell replacement handling (0d8b22c)
- Support avg-down with working target sells (61798e4)
- Fix average-down flow and symbol ownership (eaf3984)
- Harden ticker list parsing for pasted text (d61bd7f)
- Harden stock import parsing and add import logs (09c63ce)
- Remove liquidation P&L from status counters (d8ca295)
- Fix monitor P&L basis and status line fitting (db9c74f)

