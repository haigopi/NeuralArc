# NeuralArc Windows Release 1.0.6

## Artifact
- File: NeuralArc-1.0.6.exe
- Path: artifacts/windows/NeuralArc-1.0.6.exe

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch NeuralArc from Start Menu.

## Verify checksum (optional, PowerShell)
powershell:
  Get-FileHash "C:\path\to\NeuralArc-1.0.6.exe" -Algorithm SHA256

## Changes
- Add v1.0.6 artifact READMEs; update release script (b522db1)
- Add release notes and paper-strategy handling (d481e31)
- Adding Overnight Eligible Options and showing in UI. (29d837b)
- Handle Alpaca API key changes and add UI icons (ba778ac)

