# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

NeuralArc is a local-first Java 21 Swing desktop trading application (Alpaca broker integration, paper/live modes). Treat it as a long-running stateful operator console, not a stateless web app.

**Read `AGENTS.md` before making changes.** It contains the binding engineering rules for this repo (class-size limits, Swing EDT discipline, strategy-engine and persistence standards, per-area test expectations). The notes below summarize commands and the big picture; `AGENTS.md` is the source of truth for conventions.

## Commands

```bash
./gradlew test                                   # run all tests (JUnit 5)
./gradlew test --tests MarketHoursServiceTest    # run a single test class
./gradlew test --tests "com.neuralarc.service.*" # run tests by package pattern
./gradlew run                                    # launch the app
./gradlew build                                  # compile + test + jar
```

Packaging/release scripts live in `scripts/` (`build-all.sh`, `package-macos.sh`, `package-windows.ps1`, `package-linux.sh`, `release-all.sh --version X.Y.Z`). Version is injected via `-PreleaseVersion`.

## Architecture

All code is under `src/main/java/com/neuralarc/`, tests mirror it under `src/test/java/com/neuralarc/`.
Ensure all the tests were written. 
Be consistant in the UI look and feel with the rest of the application
**Layers and data flow:**
- `NeuralArc.java` — entrypoint; bootstraps FlatLaf look-and-feel and the main window.
- `ui/` — Swing UI. `TradingFrame` is the main window; logic is deliberately split out into controllers/coordinators (`StrategyActionsController`, `PortfolioActionsController`, `TradeStreamLifecycleCoordinator`, `ConnectionLifecycleCoordinator`) and `TradingRuntimeSupport`, which owns runtime wiring of broker clients and mode-specific services. UI renders from cached snapshots only — never broker calls from renderers.
- `service/` — the active strategy execution path is `StrategyPollingService -> StrategyEngine -> StrategyService` (do not reintroduce legacy poller/evaluator paths). Also `AppSettingsService` (settings + credential storage and legacy-properties migration), `MarketHoursService` (regular/extended hours, holidays), `AutoAnalyzeService`/`RecommendationEngine`.
- `api/` — broker boundary. `TradingApi` is the interface; `TradingApiFactory` selects paper vs live; `AlpacaTradingApi`/`HttpAlpacaClient` for REST, `AlpacaTradingWebSocketClient` for the order stream, `AlpacaMarketDataApi` for quotes.
- `db/` — SQLite persistence at `~/.neuralarc/neuralarc.db`. `AppDatabase` owns the connection and append-only migrations (`applyMigrations()` — add new entries, never edit existing ones). `Sqlite*Repository` classes cache in memory with invalidation. Legacy `File*Repository` classes in `service/` exist only for backward compatibility.
- `model/`, `util/`, `security/`, `analytics/` — domain types (monetary values are `BigDecimal`; `Position` is synchronized/copy-based), helpers, credential encryption, opt-in telemetry.

**Threading model (the most important invariant):** the Swing EDT is for painting, user interaction, and applying already-computed state. Broker I/O, polling, persistence flushes, and stream reconciliation run on background executors. Streaming order updates must stay idempotent and reconcile safely with polling.

**State model:** strategy lifecycle state, broker order status, and reconciled broker position are related but distinct — when changing status behavior, verify lifecycle transitions, order-status mapping, and position-aware UI labels together.

**Safety defaults:** paper mode by default; live trading only via explicit configuration. Never log or persist API keys/secrets in plaintext; telemetry is opt-in.

**Market data discipline:** Stock discovery and scanner features must use live broker/market-data integrations only. Paper mode is broker-backed simulation, not a demo-data mode; never ship hardcoded stock tickers, canned prices, or synthetic scanner candidates in runtime paths. If live data or credentials are unavailable, show an empty/credential-required state.
