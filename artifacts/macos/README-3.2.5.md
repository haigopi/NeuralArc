# NeuralArc macOS Release 3.2.5

## Artifacts
- NeuralArc-3.2.5-x64.dmg

- NeuralArc-3.2.5-arm64.dmg is for Apple Silicon Macs (M1 and later).
- NeuralArc-3.2.5-x64.dmg is for Intel Macs.
- Not sure which you have? Apple menu > About This Mac: "Chip: Apple M..." is Apple Silicon,
  "Processor: Intel..." is Intel.

## Install
1. Open the DMG file that matches your Mac.
2. Drag NeuralArc.app to Applications.
3. Launch from Applications.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  shasum -a 256 artifacts/macos/NeuralArc-3.2.5-arm64.dmg artifacts/macos/NeuralArc-3.2.5-x64.dmg

## Changes
- Self Cleaning (529b591)
- Minor Reconcialation (d46cf28)

