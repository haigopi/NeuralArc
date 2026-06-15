# NeuralArc

NeuralArc is a local-first Java desktop trading application built with Swing. It is designed as a long-running operator console for personal trading workflows: configure strategies, monitor market/session state, manage broker connectivity, and keep sensitive workflow state on your own machine.

> Not financial advice. NeuralArc is decision-support and execution software for personal use. It is not a managed brokerage service and does not guarantee profit or loss prevention.

## Current positioning
- Desktop-first Java 21 application using Swing and FlatLaf.
- Alpaca broker integration for paper and live trading workflows.
- Paper/simulation-first operating model; live trading requires explicit user configuration and is used at the user's own risk.
- Local-first persistence using SQLite at `~/.neuralarc/neuralarc.db`.
- Strategy polling and broker I/O run off the Swing EDT so the UI can remain responsive.
- Strategy status is driven by local lifecycle state, broker order status, and reconciled broker position snapshots.

## Key capabilities
- Strategy setup with base buy, staged averaging, stop-loss, target-sell, profit-hold, trailing controls, and repeat-after-exit behavior.
- Portfolio and strategy monitoring from a desktop console.
- Paper and live Alpaca modes with mode-specific credentials.
- Market-hours handling with regular-hours, extended-hours, and closed-market safeguards.
- Trade stream reconciliation plus polling fallback for broker order state.
- Local encrypted storage for broker credentials and app settings.
- Optional telemetry and diagnostics only when explicitly enabled.
- Build scripts for macOS, Windows, and Linux packaging.

## AI integration disclaimer
AI-assisted recommendation and local AI integration features are being prepared for upcoming releases. Any AI output should be treated as decision-support only, not financial advice, not a guarantee of accuracy, and not an instruction to trade. Users remain responsible for reviewing strategy settings, risk controls, market conditions, and broker behavior before enabling or continuing any automated workflow.

## Safety model
NeuralArc is built around guardrails, but those guardrails do not remove trading risk.

Important defaults and expectations:
- Start in paper/simulation mode and validate strategies before live use.
- Review every strategy's quantities, limits, stop controls, target exits, and max-capital settings.
- Live mode should only be enabled by users who understand the broker account, order behavior, and risk.
- Extended-hours trading can involve lower liquidity, wider spreads, price gaps, and higher execution risk.
- Automation can fail because of market conditions, broker API behavior, local machine state, network outages, or configuration mistakes.

## Requirements
- Java 21 toolchain.
- Gradle wrapper from this repository.
- Alpaca account and API credentials for trading workflows.
- `gh` CLI only if creating GitHub releases through the release script.

## Run locally
```bash
./gradlew test
./gradlew run
```

Entrypoint:
```text
src/main/java/com/neuralarc/NeuralArc.java
```

## Configuration
Most operator configuration is managed from the app Settings dialog and persisted locally in SQLite.

Credentials and settings are stored through `AppSettingsService` in SQLite. Legacy `~/.neuralarc/*.properties` settings and credential files are migrated for backward compatibility when present.

## Persistence
Primary persistence is SQLite:
```text
~/.neuralarc/neuralarc.db
```

SQLite-backed repositories store:
- strategies
- strategy orders
- strategy execution events
- aggregate P&L
- app settings and encrypted credentials

Legacy file repositories still exist for compatibility paths, but new runtime persistence should use the SQLite repositories.

## Market hours and polling
Settings include:
- Auto pause polling when market is closed.
- Enable extended-hours trading.
- Strategy default polling interval.
- Repeat cycle after profit exit.
- Resubmit on expiry.

When auto-pause is enabled, NeuralArc can pause active strategies outside the tradable session to reduce broker calls. System-paused strategies are tracked separately from user-paused strategies so only eligible system-paused strategies are resumed when the session reopens.

Market session handling covers:
- regular US equity hours: `9:30 AM` to `4:00 PM` Eastern
- extended hours when enabled: `4:00 AM` to `8:00 PM` Eastern
- weekend and major US market holiday detection
- eligible extended-hours Alpaca limit orders using `extended_hours=true`

## Privacy and telemetry
- Broker credentials are encrypted locally before storage.
- Telemetry is disabled by default and requires explicit consent.
- Credentials are not emitted to telemetry events.
- Diagnostics may include request IDs, status data, and logs, but should not include secrets.

See `PRIVACY.md` for the privacy policy.

## Build and package
Build scripts live under `scripts/`.

macOS:
```zsh
./scripts/package-macos.sh 1.3.3
```

Windows PowerShell:
```powershell
.\scripts\package-windows.ps1 1.3.3
```

Linux:
```bash
./scripts/package-linux.sh 1.3.3
```

Host-aware wrapper:
```bash
./scripts/build-all.sh --version 1.3.3
```

## Create a GitHub release
Draft release:
```bash
./scripts/release-all.sh --version 1.3.3
```

Publish immediately:
```bash
./scripts/release-all.sh --version 1.3.3 --publish
```

Release existing artifacts only:
```bash
./scripts/release-all.sh --version 1.3.3 --skip-build
```

Dry run:
```bash
./scripts/release-all.sh --version 1.3.3 --dry-run
```

Release prerequisites:
- `gh` CLI installed and authenticated with `gh auth login`.
- Platform artifacts generated under `artifacts/` for the target version.
- Release notes README present under one of the platform artifact directories, or the release script will fall back to generic notes.

## Repository map
- `src/main/java/com/neuralarc/NeuralArc.java` - application entrypoint.
- `src/main/java/com/neuralarc/ui/` - Swing UI, controllers, runtime lifecycle coordination.
- `src/main/java/com/neuralarc/service/` - strategy engine, polling, settings, market-hours, recommendation services.
- `src/main/java/com/neuralarc/db/` - SQLite database and repositories.
- `src/main/java/com/neuralarc/api/` - Alpaca and broker API boundary.
- `docs/` - landing page and public web assets.
- `scripts/` - build, package, release, and asset-generation scripts.

## Development notes
- Keep UI rendering state-driven; avoid broker calls from renderers.
- Keep broker I/O, polling, persistence flushes, and stream reconciliation off the Swing EDT.
- Preserve paper-first defaults and require explicit live-mode configuration.
- Do not log API keys or secrets.
- Add SQLite migrations through `AppDatabase.applyMigrations()` instead of editing existing migrations.

## License
See `LICENSE.md`. Enhanced distribution and/or commercial selling is not permitted without prior written permission.
