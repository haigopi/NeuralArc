# AGENTS.md

## Scope and stack
- This is a Gradle Java desktop application built with Swing.
- Entrypoint: `src/main/java/com/neuralarc/NeuralArc.java`.
- Java target: Java 22 via `build.gradle`.
- The app is local-first, desktop-first, and stateful. Treat it like a long-running operator console, not a stateless web app.

## Non-negotiable code organization rules
- Keep any single Java class at or under `1000` lines.
- If a class approaches that limit, modularize before adding more logic.
- While refactoring or adding new code, remove nearby dead code, stale branches, unused helpers, and obsolete overloads when it is safe to do so.
- Do not leave known dead code behind as part of a feature change unless there is a documented reason to defer its removal.
- Prefer extracting:
  - UI subpanels, renderers, dialogs, and action handlers from large Swing classes
  - snapshot/state carriers from UI orchestration
  - persistence helpers from repositories
  - rule evaluators and side-effect helpers from engine classes
- Do not solve size pressure by hiding complexity in anonymous inner classes or giant private methods.
- Preserve behavior while modularizing. Refactors must keep existing persistence formats and strategy semantics unless the change explicitly requires a migration.

## Current architecture map
- Main window and UI orchestration:
  - `src/main/java/com/neuralarc/ui/TradingFrame.java`
  - `src/main/java/com/neuralarc/ui/TradingRuntimeSupport.java`
  - `src/main/java/com/neuralarc/ui/StrategyActionsController.java`
  - `src/main/java/com/neuralarc/ui/PortfolioActionsController.java`
  - `src/main/java/com/neuralarc/ui/PortfolioCaptureController.java`
  - `src/main/java/com/neuralarc/ui/TradeStreamLifecycleCoordinator.java`
  - `src/main/java/com/neuralarc/ui/ConnectionLifecycleCoordinator.java`
- App startup and look-and-feel bootstrap:
  - `src/main/java/com/neuralarc/NeuralArc.java`
- Settings and operator configuration:
  - `src/main/java/com/neuralarc/ui/SettingsDialog.java`
  - `src/main/java/com/neuralarc/service/AppSettingsService.java`
  - `src/main/java/com/neuralarc/db/AppDatabase.java`
- Polling and strategy execution path:
  - `src/main/java/com/neuralarc/service/StrategyPollingService.java`
  - `src/main/java/com/neuralarc/service/StrategyEngine.java`
  - `src/main/java/com/neuralarc/service/StrategyService.java`
- Strategy persistence:
  - `src/main/java/com/neuralarc/db/AppDatabase.java`
  - `src/main/java/com/neuralarc/db/SqliteStrategyRepository.java`
  - `src/main/java/com/neuralarc/db/SqliteStrategyOrderRepository.java`
  - `src/main/java/com/neuralarc/db/SqliteStrategyExecutionEventRepository.java`
  - legacy compatibility helpers still exist in:
    - `src/main/java/com/neuralarc/service/FileStrategyRepository.java`
    - `src/main/java/com/neuralarc/service/FileStrategyOrderRepository.java`
    - `src/main/java/com/neuralarc/service/FileStrategyExecutionEventRepository.java`
- Broker and market data boundary:
  - `src/main/java/com/neuralarc/api/TradingApi.java`
  - `src/main/java/com/neuralarc/api/TradingApiFactory.java`
  - `src/main/java/com/neuralarc/api/AlpacaTradingApi.java`
  - `src/main/java/com/neuralarc/api/HttpAlpacaClient.java`
  - `src/main/java/com/neuralarc/api/AlpacaMarketDataApi.java`
  - `src/main/java/com/neuralarc/api/HttpAlpacaMarketDataApi.java`
  - `src/main/java/com/neuralarc/api/AlpacaTradingWebSocketClient.java`
- Market-hours and session logic:
  - `src/main/java/com/neuralarc/service/MarketHoursService.java`
- Auto Analyze and recommendation flow:
  - `src/main/java/com/neuralarc/service/AutoAnalyzeService.java`
  - `src/main/java/com/neuralarc/service/RecommendationEngine.java`
  - `src/main/java/com/neuralarc/ui/StrategyDialog.java`

## Runtime model to preserve
- The app is snapshot-driven at the UI layer.
- Rendering should prefer cached strategy snapshots, not live broker calls from table renderers or label formatting.
- Runtime wiring for broker clients and mode-specific services should go through `TradingRuntimeSupport`.
- Strategy polling and stream handling happen off the Swing EDT.
- Swing EDT is for:
  - painting
  - user interaction
  - applying already-computed state to widgets
- Background executors are for:
  - polling
  - broker I/O
  - repository flushes
  - stream reconciliation

## Swing engineering standards for this repo
- Do not block the EDT with:
  - HTTP calls
  - file I/O
  - strategy reconciliation
  - expensive JSON formatting/parsing
- UI components must render from state already available in memory.
- If UI needs new remote data, schedule it in a background task and apply the result later on the EDT.
- Prefer small renderer classes and helper panels over large inline UI blocks.
- Keep table renderers pure. They must not perform broker calls, persistence calls, or expensive recomputation.
- Dialogs should own layout and validation only. Service calls should be delegated to service classes or background workers.
- Put explanatory descriptions inside the same bordered section or subsection as the controls they describe, below the relevant controls. Use the muted subsection-heading style for descriptions so they read as secondary guidance.

## Strategy engine standards
- The active engine path is `StrategyPollingService -> StrategyEngine -> StrategyService`.
- Do not reintroduce legacy poller/evaluator paths.
- Lifecycle state, broker order status, and actual broker position are related but not identical.
- When changing strategy status behavior:
  - verify lifecycle transitions
  - verify broker order status mapping
  - verify position-aware UI status labels
- Avoid duplicating broker calls inside one poll cycle. Prefer batched or reused snapshots where possible.
- Streaming order updates must stay idempotent and must continue to reconcile safely with polling.

## Persistence standards
- Primary persistence is SQLite (`~/.neuralarc/neuralarc.db`) via `AppDatabase` and `Sqlite*Repository` classes.
- `AppSettingsService` migrates legacy `~/.neuralarc/*.properties` files into SQLite; keep migration behavior backward compatible.
- Schema evolution is append-only through `AppDatabase.applyMigrations()`; add a new migration entry instead of editing existing ones.
- Repository hot paths should keep using in-memory caches/indexes (`Sqlite*Repository` cache + invalidation model).
- If adding new persisted fields:
  - make reads backward compatible
  - provide sensible defaults for missing fields
- Avoid reintroducing full-file parse/rewrite persistence patterns in new runtime code.

## Project-specific conventions
- Monetary values use `BigDecimal`.
- Use `Monetary` helpers for rounding/normalization where the codebase already does so.
- `Position` is synchronized and copy-based for UI safety. Preserve that model.
- Favor immutable values or records for analysis/recommendation results where practical.
- Keep broker-facing symbols normalized to uppercase.
- Preserve safe defaults:
  - paper mode by default
  - live mode only when explicitly enabled and configured

## Performance guidance
- Prefer fewer broker calls over more threads.
- Before adding concurrency, check whether the bottleneck is:
  - broker I/O
  - persistence churn
  - unnecessary UI refresh
  - log volume
- Prefer:
  - batched market data fetches
  - reused clients
  - cached snapshots
  - targeted row updates
- Avoid:
  - per-render remote calls
  - full table refreshes when one row changed
  - unbounded in-memory UI logs

## Testing workflow
- Run tests with:
  - `./gradlew test`
- Launch app with:
  - `./gradlew run`
- Build and release automation scripts live under `scripts/` (`build-all.sh`, `release-all.sh`, `package-macos.sh`, `package-windows.ps1`).
- When changing:
  - strategy execution logic: update the relevant strategy/service tests
  - market-hours behavior: update `MarketHoursService` tests
  - recommendation logic: update recommendation/apply tests
  - persistence behavior: update repository/settings tests
  - UI state mapping: add targeted tests where practical, and verify manually if Swing behavior is involved
  - connection or stream lifecycle behavior: update `ConnectionLifecycleCoordinatorTest` and/or `TradeStreamLifecycleCoordinatorTest`

## Privacy and integration constraints
- Market-data and stock discovery features must use live broker/market-data integrations only. Do not ship hardcoded stock tickers, canned stock prices, or synthetic scanner candidates in runtime paths; if live data is unavailable, show an empty/credential-required state instead of demo rows.
- Paper mode is still broker-backed simulation mode and must use the same live market data boundaries as live mode. Do not treat paper mode as permission to use stale sample stock data.
- Never log or publish API keys or secrets.
- Telemetry remains opt-in.
- Local credentials and app state remain local unless a feature explicitly states otherwise.
- Support and diagnostics features may include request IDs and logs, but must not leak secrets.

## When updating this codebase
- Prefer improving separation of concerns over adding more branches to `TradingFrame`.
- If adding new UI behavior to a large class:
  - first check whether it belongs in a dedicated panel, dialog, renderer, or controller helper
- If touching strategy state:
  - check status labels
  - check history display
  - check polling behavior
  - check market-close behavior
- If touching broker integration:
  - check both paper and live mode behavior
  - check WebSocket/stream side effects
  - check closed-market and extended-hours behavior
