# NeuralArc Linux Release 3.0.3

## Artifact
- File: NeuralArc-3.0.3.deb
- Path: artifacts/linux/NeuralArc-3.0.3.deb

## Install
1. Install the DEB package (for Debian/Ubuntu-based distributions).
2. Launch NeuralArc from applications menu.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  sha256sum artifacts/linux/NeuralArc-3.0.3.deb

## Changes
- Fix broker adoption to check both modes on refresh (585b57e)
- Fix short position handling across engine and UI (a102655)
- Harden sell accounting and broker sync adoption (1960dd1)

