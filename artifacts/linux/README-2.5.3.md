# NeuralArc Linux Release 2.5.3

## Artifact
- File: NeuralArc-2.5.3.deb
- Path: artifacts/linux/NeuralArc-2.5.3.deb

## Install
1. Install the DEB package (for Debian/Ubuntu-based distributions).
2. Launch NeuralArc from applications menu.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  sha256sum artifacts/linux/NeuralArc-2.5.3.deb

## Changes
- Fix target sell replacement handling (0d8b22c)
- Support avg-down with working target sells (61798e4)
- Fix average-down flow and symbol ownership (eaf3984)
- Harden ticker list parsing for pasted text (d61bd7f)
- Harden stock import parsing and add import logs (09c63ce)
- Remove liquidation P&L from status counters (d8ca295)
- Fix monitor P&L basis and status line fitting (db9c74f)

