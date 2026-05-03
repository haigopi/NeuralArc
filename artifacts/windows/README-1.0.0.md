# NeuralArc Windows Release 1.0.0

## Artifact
- Expected file: `NeuralArc-1.0.0.exe`
- Expected path: `artifacts/windows/NeuralArc-1.0.0.exe`

## Install
1. Run the EXE installer.
2. Follow installer prompts.
3. Launch `NeuralArc` from Start Menu.

## Verify checksum (optional, PowerShell)
```powershell
Get-FileHash "C:\path\to\NeuralArc-1.0.0.exe" -Algorithm SHA256
```

## Notes
- Windows installer is produced by `scripts/package-windows.ps1` on Windows.
- If SmartScreen warns, select "More info" -> "Run anyway" only if checksum/source is trusted.

