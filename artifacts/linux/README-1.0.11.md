# NeuralArc Linux Release 1.0.11

## Artifact
- File: NeuralArc-1.0.11.deb
- Path: artifacts/linux/NeuralArc-1.0.11.deb

## Install
1. Install the DEB package (for Debian/Ubuntu-based distributions).
2. Launch NeuralArc from applications menu.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  sha256sum artifacts/linux/NeuralArc-1.0.11.deb

## Changes
- Filter movers by trade_count and refine UI labels (4873e95)
- Add clean trade history and stream recovery sync (32ec782)
- Use hasPosition for paused toggle text (e787c1c)
- Return strategyId for streaming order updates (fff359c)
- Add strategy defaults and Lucky review (7aa7d28)

