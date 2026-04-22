# AGENTS.md

## Scope and stack
- This is a Maven Java desktop app (Swing), entrypoint `src/main/java/com/neuralarc/MainApp.java`.
- Build targets are Java 22 (`pom.xml`), even though `README.md` mentions Java 17+.
- Core behavior is local-first and safe-by-default: use `BrokerType.MOCK` and paper trading unless explicitly changed.

## Architecture map (read these first)
- UI orchestration lives in `src/main/java/com/neuralarc/ui/TradingFrame.java`.
- Strategy loop is split across `PricePoller` -> `TradingStrategyService.onPriceTick()` -> `RuleEvaluationService.evaluate(...)`.
- Broker boundary is `TradingApi` (`src/main/java/com/neuralarc/api/TradingApi.java`) with implementations behind `TradingApiFactory`.
- Analytics boundary is `AnalyticsPublisher`; default impl is `HttpAnalyticsPublisher` + `AnalyticsQueue` for retries/persistence.
- Security boundary is `CredentialManager` + `EncryptionUtil` for local encrypted credential storage.

## Runtime data flow (important coupling)
- `TradingFrame.startStrategy()` builds `StrategyConfig`, `TelemetryConfig`, and `TradingStrategyService`, then starts polling.
- Every tick: broker price fetch, position refresh, rule evaluation, optional order submission, then position analytics publish.
- Rule deduplication depends on `StrategyState.markTriggered(...)`; do not bypass it when adding rules.
- Full exit resets strategy state (`TradingStrategyService.handleRule(...)` when shares reach 0).
- UI refresh reads broker position directly in `TradingFrame.refreshPanels()`; keep API and state changes thread-safe.

## Project-specific conventions
- Monetary values use `BigDecimal` everywhere (`Position`, `StrategyConfig`, `RuleEvaluationService`).
- Domain config/results favor Java records (`StrategyConfig`, `OrderResult`, `TelemetryConfig`).
- Event payloads are built with fluent `AnalyticsEvent.put(...)` and serialized by `AnalyticsEvent.toJson()`.
- `Position` and `StrategyState` methods are synchronized; preserve thread-safety when modifying these classes.
- The Alpaca path is currently a stub (`AlpacaTradingApi` returns failed/empty behavior); treat MOCK as the only working broker.

## Developer workflows
- Run tests: `mvn clean test`.
- Launch app: `mvn exec:java -Dexec.mainClass=com.neuralarc.MainApp`.
- Tests are small unit tests in `src/test/java/com/neuralarc/...` and assert deterministic rule/math/identity behavior.
- If you change strategy logic, update `RuleEvaluationServiceTest` first; if you change P&L math, update `PositionTest`.
- If you change telemetry schema/serialization, update `AnalyticsEventTest` and `examples/analytics-payloads.json`.

## Integration and privacy constraints
- Telemetry is opt-in only (`TradingFrame` checkbox + `SettingsDialog`, `PRIVACY.md`).
- Never include API keys/secrets in analytics events; current flow intentionally omits them.
- Credential file path is `~/.neuralarc/credentials.properties`; analytics dead-letter file is `~/.neuralarc/analytics-queue.log`.
- HTTP analytics uses up to 4 retries with exponential backoff before persisting failed events (`HttpAnalyticsPublisher.sendWithRetry`).
- When adding a broker: implement `TradingApi`, extend `BrokerType`, wire `TradingApiFactory`, and expose UI/settings fields if needed.

