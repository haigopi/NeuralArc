# NeuralArc Trader (Java Swing MVP)

## Project overview
NeuralArc Trader is a Java Swing desktop trading utility designed to run paper-first with Alpaca and strict risk controls.

## Features
- Java 22 desktop app using Swing
- Broker abstraction (`TradingApi`) with Alpaca integration
- Rule-based strategy engine with 5 rules
- Strategy lifecycle services for initial order submission and staged order tracking
- Paper trading ON by default (`alpaca.mode=PAPER`)
- Optional encrypted local credential storage (AES-GCM + PBKDF2)
- Opt-in telemetry publishing with queueing, retry, and graceful degradation
- BigDecimal-based money/price/P&L computations

## How rule engine works
For default strategy (NIO):
1. Buy 10 at/under 8.00
2. Activate stop logic at/above 9.00
3. Sell all if price falls below 9.00 after stop loss activation
4. Sell all at/above 10.00
5. Buy +5 at/under 7.00 and +5 at/under 6.00 once each

Duplicate triggers are prevented through strategy state tracking and rule flags.

## How to run
```bash
./gradlew test
./gradlew run
```

## Build and release scripts (consolidated)
Use these four scripts in `scripts/`:
- `scripts/package-macos.sh` - build macOS DMG on macOS
- `scripts/package-windows.ps1` - build Windows EXE on Windows
- `scripts/build-all.sh` - run the platform build for the current host and print next cross-OS step
- `scripts/release-all.sh` - run build flow and create/upload GitHub release

### Build only
On macOS:
```zsh
cd /Users/gopimac/Documents/Workspace/NeuralArc
./scripts/package-macos.sh 1.0.0
```

On Windows (PowerShell):
```powershell
cd C:\path\to\NeuralArc
.\scripts\package-windows.ps1 1.0.0
```

Host-aware build wrapper:
```zsh
cd /Users/gopimac/Documents/Workspace/NeuralArc
./scripts/build-all.sh --version 1.0.0
```

### Build and create GitHub release
Default (draft release):
```zsh
cd /Users/gopimac/Documents/Workspace/NeuralArc
./scripts/release-all.sh --version 1.0.0
```

Publish immediately (non-draft):
```zsh
cd /Users/gopimac/Documents/Workspace/NeuralArc
./scripts/release-all.sh --version 1.0.0 --publish
```

Release existing artifacts only:
```zsh
cd /Users/gopimac/Documents/Workspace/NeuralArc
./scripts/release-all.sh --version 1.0.0 --skip-build
```

Dry run preview:
```zsh
cd /Users/gopimac/Documents/Workspace/NeuralArc
./scripts/release-all.sh --version 1.0.0 --dry-run
```

### Release prerequisites
- `gh` CLI installed and authenticated (`gh auth login`)
- Both artifacts must exist for the same version:
  - `artifacts/macos/NeuralArc-<version>.dmg`
  - `artifacts/windows/NeuralArc-<version>.exe`
- Release notes are sourced from `artifacts/macos/README-<version>.md` when present (fallback: Windows README, then generic notes)

## Safety warning
This app is for personal and paper trading use first. It is **not financial advice** and is not a managed brokerage platform.
Live trading must be manually enabled by configuration and is used at the user’s own risk.
Default behavior is Alpaca paper trading only.

## Alpaca paper mode configuration
Set API key/secret in the app **Settings** dialog.

Set these runtime defaults in `src/main/resources/app.properties` (or your packaged runtime config):

```ini
alpaca.trading.paperUrl=https://paper-api.alpaca.markets
alpaca.trading.liveUrl=https://api.alpaca.markets
alpaca.dataUrl=https://data.alpaca.markets
trading.live.enabled=false
```

`LIVE` mode is blocked unless `trading.live.enabled=true` is explicitly set.

## Market hours and polling
Settings now include:
- `Auto pause polling when market is closed` (enabled by default)
- `Enable extended-hours trading` (disabled by default)

When auto-pause is enabled, NeuralArc pauses active strategies outside the current tradable session to reduce Alpaca API usage. Strategies paused by the system are marked separately from user-paused strategies and only those auto-paused strategies are resumed when the market session reopens.

Market session detection uses:
- regular US equity hours: `9:30 AM` to `4:00 PM` Eastern
- extended hours when enabled: `4:00 AM` to `8:00 PM` Eastern
- weekend and major US market holiday detection

If extended-hours trading is disabled, only regular market hours count as tradable and new orders outside that window are blocked. If extended-hours trading is enabled, eligible Alpaca limit orders are submitted with `extended_hours=true`.

## Extended-hours trading risk
Extended-hours sessions can have lower liquidity, wider spreads, more price gaps, and higher execution risk. Paper trading remains the default mode and is the recommended way to validate strategies before enabling live trading.

## How credentials are stored
If enabled, credentials are encrypted using AES-GCM with a PBKDF2-derived key and written to:
- `~/.neuralarc/credentials.properties`

Credentials are never emitted to analytics events.

## What telemetry is collected
Selected event types: app lifecycle, strategy start/stop, rule triggered, order events, and position/P&L updates.
Potential fields include userId, broker type, symbol, side, quantity, price, timestamp, session/strategy id, shares, average cost, and realized/unrealized P&L.

## Telemetry consent and privacy note
Telemetry is disabled by default and enabled only by explicit user consent.
See `PRIVACY.md`.

## How to configure analytics endpoint
Use Settings dialog to set endpoint (example: `http://localhost:8080/events`) and enable telemetry.

## How to add a new broker
1. Implement `TradingApi`
2. Add a broker enum value in `BrokerType`
3. Register implementation in `TradingApiFactory`
4. Add any UI options/credentials you need

## Strategy runtime
Strategies are stored locally under `~/.neuralarc/` and reconciled on startup using local JSON state plus Alpaca REST polling.
The app now uses one staged-buy state machine for base buy, staged averaging, stop loss, target sell, profit hold, and restart-after-exit behavior.

## Future roadmap
- Historical charts and richer analytics dashboard
- SSE/event-stream order updates in addition to polling
- Plugin-style rule definitions
- Multi-symbol and portfolio strategies

## License suggestion
Recommended: MIT or Apache-2.0 for friendly open-source collaboration.
