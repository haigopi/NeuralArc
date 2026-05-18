# NeuralArc Linux Release 1.1.5

## Artifact
- File: NeuralArc-1.1.5.deb
- Path: artifacts/linux/NeuralArc-1.1.5.deb

## Install
1. Install the DEB package (for Debian/Ubuntu-based distributions).
2. Launch NeuralArc from applications menu.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  sha256sum artifacts/linux/NeuralArc-1.1.5.deb

## Changes
- Mark expired orders failed; market-closed pause (3f64ab8)
- Deduplicate strategy polls and extract loader (ef1cd85)

