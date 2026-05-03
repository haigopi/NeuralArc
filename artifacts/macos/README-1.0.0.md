# NeuralArc macOS Release 1.0.0

## Artifact
- File: `NeuralArc-1.0.0.dmg`
- Path: `artifacts/macos/NeuralArc-1.0.0.dmg`

## Install
1. Open the DMG file.
2. Drag `NeuralArc.app` to `Applications`.
3. Launch from Applications.

## Verify checksum (optional)
```zsh
cd /Users/gopimac/Documents/Workspace/NeuralArc
shasum -a 256 artifacts/macos/NeuralArc-1.0.0.dmg
```

## Notes
- Built with `jpackage` from this repository.
- If macOS blocks first launch, use: System Settings -> Privacy & Security -> Open Anyway.

