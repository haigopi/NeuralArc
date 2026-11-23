# NeuralArc macOS Release 3.2.4

## Artifacts
- NeuralArc-3.2.4-x64.dmg

- NeuralArc-3.2.4-arm64.dmg is for Apple Silicon Macs (M1 and later).
- NeuralArc-3.2.4-x64.dmg is for Intel Macs.
- Not sure which you have? Apple menu > About This Mac: "Chip: Apple M..." is Apple Silicon,
  "Processor: Intel..." is Intel.

## Install
1. Open the DMG file that matches your Mac.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  shasum -a 256 artifacts/macos/NeuralArc-3.2.4-arm64.dmg artifacts/macos/NeuralArc-3.2.4-x64.dmg

## Changes
- Keep loss-buy levels resting between polls (fbef51d)
- Rearm edited filled loss-buy levels (20f9ea7)
- Rest sell triggers as broker limit orders (6bc9a4b)

