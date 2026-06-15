# Implementation Plan — ORB Engine from Smart Picks

Status: PLANNED
Branch: current branch
Last updated: 2026-06-15

This plan describes how to turn the existing Smart Picks "ORB Engine" workspace
stub into a production-ready Opening Range Breakout workflow. It intentionally
mirrors the Gap Rocket implementation style: Smart Picks creates the dedicated
workspace, a strategy-specific panel owns discovery/configuration, background
services perform all broker and market-data I/O, and the normal
`StrategyPollingService -> StrategyEngine -> StrategyService` path remains the
only execution path for orders and lifecycle transitions.

Read `AGENTS.md` before implementation. The non-negotiables for this feature
are: no blocking broker calls on the Swing EDT, no hardcoded runtime ticker or
price samples, no large additions to `TradingFrame`, append-only SQLite
migrations, and paper/live behavior routed through the existing runtime support
boundaries.

---

## 1. Goal (customer point of view)

Smart Picks already offers an "ORB Engine" template. Today that template creates
a generic workspace, but it does not yet provide ORB-specific discovery,
configuration, candidate review, or opening-range execution behavior.

The target experience is:

1. The operator clicks **Smart Picks -> ORB Engine -> Create**.
2. NeuralArc opens a dedicated ORB Engine tab with clear ORB-specific controls.
3. The operator can either:
   - type/watch selected symbols manually, or
   - auto-discover live candidates from Alpaca market data and screeners.
4. During the opening-range window, NeuralArc captures each candidate's high/low
   range from live bars.
5. After the range closes, NeuralArc arms breakout entries using the existing
   strategy engine and broker-backed paper/live mode.
6. The ORB workspace shows each row's setup state, planned entry, stop,
   target/risk, broker order state, and position-aware lifecycle status.
7. Optional scheduling lets a running desktop app prepare candidates before the
   open and arm entries immediately after the configured range closes.

### Trading-domain definition

Opening Range Breakout is a session strategy that:

- observes a configured first-session range, commonly 5, 15, or 30 minutes after
  the regular market open;
- records the range high and range low;
- enters long when price breaks above the range high, or short/avoid-short in v1
  depending on product scope;
- anchors the stop near the opposite side of the range or a volatility-adjusted
  level;
- sizes risk from the planned entry and stop rather than from broker blended
  account-level average price;
- typically stops accepting new entries after a morning cutoff.

For v1, implement long-only ORB by default. Short ORB can be a later extension
because the current strategy UI and risk defaults are already long-position
oriented.

---

## 2. What already exists (reuse, do not rebuild)

| Concern | Existing code | How ORB should use it |
|---|---|---|
| Smart Picks template | `model/StrategyWorkspaceTemplate` | `ORB Engine` already appears with code `ORB`; keep using this as the workspace entry point. |
| Workspace creation | `service/WorkspaceService`, `db/SqliteWorkspaceRepository` | Use `findOrCreate("ORB Engine", "ORB", mode)` and existing dynamic tabs. |
| Runtime wiring | `ui/TradingRuntimeSupport` | Acquire paper/live trading and market-data clients through the same mode-aware boundary as other strategies. |
| Strategy execution path | `service/StrategyPollingService`, `service/StrategyEngine`, `service/StrategyService` | Convert ORB recommendations into normal `Strategy` rows; do not create a parallel order engine. |
| Broker order identity | `util/ClientOrderId` | Workspace code `ORB` should continue appearing in order IDs for traceability. |
| Market data boundary | `api/AlpacaMarketDataApi`, `api/HttpAlpacaMarketDataApi` | Fetch live intraday bars for opening-range capture and trigger validation. |
| Market-hours logic | `service/MarketHoursService` | Gate pre-open preparation, range capture, and post-range entry windows. |
| Existing ORB data hooks | `model/StrategyWorkspace`, `model/Strategy` | Keep workspace grouping separate from per-symbol strategy rows. |
| Gap Rocket pattern | `gaprocket/*`, `ui/GapAndGoCoordinator` | Reuse the architecture pattern: config model, scanner/analyzer/factory, panel, coordinator, repository if scheduling is persisted. |
| Smart Picks review/placement | `ui/SmartPicksTrendingStocksDialog`, `ui/SmartPicksSimulationPlacementController` | Reuse review and one-click strategy creation patterns where practical; do not duplicate broker submission code. |

---

## 3. Architecture decisions

- **Dedicated ORB package:** put ORB-specific models and services under
  `src/main/java/com/neuralarc/orb/` to avoid inflating `TradingFrame` or generic
  service classes.
- **Workspace first, rows second:** Smart Picks creates the ORB Engine workspace;
  ORB candidate analysis creates per-symbol `Strategy` rows inside that
  workspace.
- **No synthetic candidates:** auto-discovery must use live Alpaca screeners,
  market movers, most-actives, or operator-supplied symbols. Missing data means
  an empty or credential-required state.
- **Range capture is stateful:** opening-range high/low, captured bar interval,
  capture timestamp, and readiness should be explicit state, not recomputed by
  table renderers.
- **Execution remains strategy-engine based:** ORB should produce planned buy
  parameters and then rely on the existing polling/stream reconciliation path for
  broker state.
- **Long-only v1:** ship a focused long-breakout workflow before adding short
  ORB, failed-breakdown, or reversal variants.
- **Schedule requires the app to be running:** this desktop app has no cloud
  scheduler. Persisted schedules should restore only when NeuralArc is open.
- **Test with fake APIs:** all scanner/analyzer/range-capture tests should use
  fake market-data clients and deterministic bars.

---

## 4. Proposed ORB module

### 4.1 Models

Create small immutable records in `com.neuralarc.orb`:

- `OrbConfig`
  - `rangeDurationMinutes` — default 15; allowed 5, 15, 30.
  - `entryBufferPercent` — default small buffer above range high.
  - `stopMode` — `RANGE_LOW`, `MID_RANGE`, `ATR_ADJUSTED` for future extension;
    v1 can implement `RANGE_LOW` and persist the enum for compatibility.
  - `riskPercent`, `takeProfitPercent`, `maxStocksToAdd`, `minimumPrice`,
    `maximumPrice`, `minimumRelativeVolume`, `minimumRangePercent`,
    `latestEntryTimeEt`.
  - `candidateSymbols` — optional manual override.
  - `autoDiscoverEnabled` and `scheduleEnabled`.
- `OrbCandidate`
  - symbol, latest price, regular-session open reference when available,
    relative volume, average volume, spread, and discovery source.
- `OpeningRangeSnapshot`
  - symbol, range start/end, high, low, volume, bar count, complete flag,
    rejection reason if incomplete.
- `OrbRecommendation`
  - symbol, range high/low, planned entry, stop, target, score, rationale,
    risk metadata, and `OrbConfig` values used to compute it.
- `OrbRunMode`
  - `ANALYZE_NOW`, `ANALYZE_AND_ARM_NOW`, `SCHEDULE`.

Keep each record narrowly scoped so UI and engine classes do not accumulate large
mutable payloads.

### 4.2 Services

- `OrbDiscoveryService`
  - Uses live Alpaca screener data where available.
  - Merges movers and most-actives, filters by price/volume/spread, caps the
    symbol list.
  - Honors manual symbols first, exactly like Gap Rocket's manual override.
- `OpeningRangeCaptureService`
  - Uses `AlpacaMarketDataApi.getIntradayBars(...)` for the configured opening
    interval.
  - Computes high, low, total volume, range percent, and completeness.
  - Rejects candidates with missing bars, zero/invalid prices, or ranges that are
    too wide/narrow according to config.
- `OrbAnalyzer`
  - Scores complete snapshots using range quality, relative volume, spread, and
    price location versus range high.
  - Produces deterministic recommendations without broker I/O.
- `OrbStrategyFactory`
  - Converts `OrbRecommendation` into existing `Strategy` rows assigned to the
    ORB workspace.
  - Sets base buy limit near planned entry, stop loss, take profit, quantity/risk
    defaults, and a last-event string that explains the ORB setup.
- `OrbCoordinator`
  - Owns the full background flow: discover -> capture range -> analyze -> apply
    recommendations -> optionally arm monitoring.
  - Exposes a small `Ui` gateway similar to `GapAndGoCoordinator.Ui` so
    `TradingFrame` remains a wiring layer.
- `OrbScheduleService` (phase 4+)
  - Persists and restores schedule settings.
  - Fires preparation and range-capture tasks when the app is open.

---

## 5. UI plan

### 5.1 Smart Picks entry

Keep the existing Smart Picks template, but update the description to make the
ORB workflow explicit:

> Capture the first 5/15/30 minute regular-session range, rank live breakout
> candidates, and arm planned entries in a dedicated ORB Engine grid.

### 5.2 ORB Engine workspace tab

Add an ORB-specific panel patterned after `GapRocketPanel`:

- Empty state:
  - title: "ORB Engine is ready"
  - explanation: "Capture the market's opening range from live Alpaca data, then
    arm breakout entries after the range closes."
  - primary action: "Run ORB Engine"
- Action bar:
  - `Run ORB Engine ▾`
    - Analyze now
    - Analyze & Arm now
    - Schedule open
  - schedule badge and cancel button when a schedule is active.
- Candidate/status grid additions:
  - Range High
  - Range Low
  - Entry
  - Stop
  - Target
  - Range %
  - ORB State: Waiting for range, Range captured, Armed, Triggered, Rejected,
    Expired.

Do not perform range capture or broker calls from renderers. Render from cached
snapshots produced by `OrbCoordinator`.

### 5.3 ORB configuration dialog

Create `OrbAnalysisDialog` rather than overloading `StrategyDialog`:

- range duration selector;
- manual candidate symbol text area with "leave blank to auto-discover" guidance;
- min/max price, min relative volume, min range percent;
- entry buffer, stop mode, take-profit percent, max rows;
- latest entry time;
- schedule controls.

Descriptions should live inside the same bordered sections as their controls and
use the muted subsection-heading style required by the repo's Swing standards.

---

## 6. Persistence plan

Phase 1 can keep ORB config in memory if the UI is purely manual. Once schedule
or reusable defaults are added, use SQLite:

1. Add an append-only migration in `AppDatabase.applyMigrations()` such as
   `010_orb_engine_schedule` or the next available migration number at the time
   of implementation.
2. Persist:
   - workspace ID;
   - strategy mode;
   - serialized `OrbConfig`;
   - enabled/disabled schedule flag;
   - pre-open preparation time and range duration;
   - created/updated timestamps.
3. Add `SqliteOrbScheduleRepository` and tests.
4. Keep reads backward compatible with defaults for missing config fields.

Do not edit existing migrations. Do not store secrets in ORB tables.

---

## 7. Phased implementation

### Phase 1 — Smart Picks ORB workspace polish

- Update the ORB template description.
- Ensure clicking Smart Picks -> ORB Engine creates or focuses the ORB workspace
  and tab in the selected paper/live mode.
- Add a minimal ORB empty-state panel to the ORB workspace tab.
- Keep generic strategy-grid behavior intact for all other workspaces.

**Tests**
- Extend `StrategyWorkspaceTemplateTest` for the updated ORB description.
- Add/extend workspace-tab tests so ORB tabs render the ORB-specific empty state.

### Phase 2 — Manual-symbol ORB analysis

- Add `OrbConfig`, `OpeningRangeSnapshot`, `OrbRecommendation`,
  `OpeningRangeCaptureService`, `OrbAnalyzer`, and `OrbStrategyFactory`.
- Build `OrbAnalysisDialog` with manual symbols required for this phase.
- Implement `OrbCoordinator.run(config, ANALYZE_NOW)` on a background executor.
- Apply recommendations to the ORB workspace as normal `Strategy` rows.

**Tests**
- Capture service computes high/low/volume from deterministic fake bars.
- Analyzer rejects incomplete ranges and scores valid ranges.
- Strategy factory maps entry/stop/target into `Strategy` fields without touching
  broker APIs.

### Phase 3 — Auto-discovery

- Add `OrbDiscoveryService` using live Alpaca screener boundaries.
- Make manual symbols optional; blank symbols trigger live auto-discovery.
- Log clear empty-state reasons for missing credentials, sparse early data, or no
  qualifying candidates.

**Tests**
- Fake screener data verifies union, filtering, sorting, duplicate removal, and
  cap behavior.
- Blank manual symbols invoke discovery; non-blank manual symbols bypass it.

### Phase 4 — Arm and execute through the existing engine

- Add `ANALYZE_AND_ARM_NOW` mode.
- For each recommendation, create/update the strategy row with ORB metadata in
  `lastEvent` and planned prices.
- Start monitoring through existing strategy actions rather than placing broker
  orders directly from the ORB coordinator.
- Ensure streaming order updates remain idempotent and polling reconciliation is
  unchanged.

**Tests**
- Strategy lifecycle test verifies ORB-created rows use existing order placement
  semantics.
- `StrategyServiceTest` continues to show client order IDs include workspace code
  `ORB`.
- UI presenter/status mapping test covers ORB pending, armed, filled, expired,
  and rejected labels where practical.

### Phase 5 — Scheduling

- Add `OrbScheduleService` and SQLite repository/migration.
- Restore active ORB schedule on app startup when credentials and workspace are
  available.
- Provide schedule badge and cancel action on the ORB panel.
- Fire range capture after the selected opening range completes, not before.
- Surface that the desktop app must be running for schedules to execute.

**Tests**
- Repository save/load/cancel tests.
- Schedule service computes the next valid market day and skips holidays/closed
  market days using `MarketHoursService`.
- Coordinator test verifies scheduled run flows through discover/capture/analyze
  without EDT work.

### Phase 6 — Hardening and observability

- Add structured ORB logs with no secrets.
- Add concise rejection reasons in the panel so operators understand why rows
  were not added.
- Cap candidate counts and Open/close-window retries to avoid log floods.
- Add manual verification notes for paper mode during market hours.

---

## 8. Acceptance criteria

- Smart Picks ORB Engine creates/focuses a dedicated ORB workspace.
- ORB analysis can run from operator-supplied symbols without blocking the EDT.
- ORB auto-discovery uses only live Alpaca integrations and shows empty states
  when unavailable.
- Opening-range high/low are computed from live intraday bars and cached for UI
  rendering.
- Recommendations become normal `Strategy` rows in the ORB workspace.
- Order placement and reconciliation continue through the existing strategy
  engine path.
- Scheduled ORB runs persist, restore, can be canceled, and clearly state that
  NeuralArc must be open.
- All new Java classes stay under 1000 lines.
- Relevant unit tests pass with `./gradlew test`.

---

## 9. Suggested PR sequence

1. `docs: add ORB Engine implementation plan` — this document.
2. `feat: polish Smart Picks ORB workspace entry` — template copy + empty panel.
3. `feat: add ORB range capture and analyzer` — pure services + tests.
4. `feat: create ORB recommendations as strategies` — coordinator + factory +
   manual-symbol dialog.
5. `feat: add ORB live candidate discovery` — screener integration + tests.
6. `feat: arm ORB recommendations through strategy engine` — monitoring/action
   wiring + lifecycle tests.
7. `feat: persist and restore ORB schedules` — migration, repository, schedule
   service, UI badge/cancel.
8. `test: harden ORB status and reconciliation coverage` — focused regression
   tests and manual verification notes.
