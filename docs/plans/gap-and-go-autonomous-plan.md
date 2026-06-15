# Implementation Plan — Autonomous Gap-and-Go Discovery, News Analysis & Scheduling

Status: Phases 1–4 IMPLEMENTED. Project migrated to Java 21.
Branch: `claude/epic-gates-jlnwiu`
Last updated: 2026-06-15

Progress:
- ✅ Phase 1 — auto-discovery (`GapAndGoDiscoveryService` + wiring + tests)
- ✅ Phase 2 — news catalysts via AI web search (`NewsCatalystResolver`,
  enrichment fan-out, defaults reconciled, tests)
- ✅ Phase 3 — consolidated "Run Gap-and-Go ▾" control (`RunMode`)
- ✅ Phase 4 — scheduling engine + persistence (`GapAndGoSchedule`,
  `GapRocketConfigCodec`, `GapAndGoScheduleService`, migration `009`,
  `SqliteGapAndGoScheduleRepository`, schedule/cancel UI, autonomous run)
- ✅ Migrated Gradle toolchain + docs from Java 22 to Java 21

This document is self-contained so any AI coding agent (or human) can continue
the work without re-deriving context. Read `AGENTS.md` and `CLAUDE.md` first —
they are the binding engineering rules (class-size limits, Swing EDT discipline,
strategy-engine/persistence standards, per-area test expectations, and the
**market-data discipline: live integrations only, never hardcoded tickers,
canned prices, or synthetic scanner candidates**).

---

## 1. Goal (customer point of view)

Today the Gap Rocket / gap-and-go feature works but requires the operator to:
- manually type candidate ticker symbols, and
- manually click Analyze during the trading window, and
- manually decide/execute entries.

The customer wants **autonomous operation with almost no watching or
monitoring**:

1. **Auto-discover** gap-and-go candidates from live Alpaca data (no manual
   symbol entry).
2. **Auto-analyze the news** for each candidate using the existing OpenAI
   integration (web search), to confirm a catalyst and rank quality.
3. **Schedule** the whole thing to run in the **premarket window 09:00–09:25 ET**
   (no SIP feed — IEX free feed has thin premarket coverage before ~09:00, so
   09:00–09:25 is the earliest reliably-usable slot), then carry through to the
   **post-open execution window (09:45–11:00 ET)**.
4. **Consolidate** the current `Analyze` / `Analyze & Execute` actions plus the
   new scheduling into a **single control**.
5. **Autonomous execution** — optionally arm and place orders after the scan
   with no manual intervention.

### Trading-domain note (why scheduling spans two windows)
Gap-and-go is a two-phase intraday strategy:
- **Discovery/scan — premarket (~09:00–09:25 ET):** find what is gapping on real
  relative volume with a news catalyst; build the watchlist.
- **Entry/execution — post-open (09:30–~11:00 ET):** the "go" is post-open price
  action (opening-range breakout / breakout retest / VWAP hold). It cannot be
  validated or executed premarket because the trigger does not exist yet.

So a schedule that only scans premarket would build a list and never act. The
schedule must register a premarket scan **and** a post-open execution arming.

### Data caveat (no SIP)
`GapRocketLiveScanner` and the screener use Alpaca's **IEX free feed**. IEX
premarket coverage is thin and the `movers`/`most-actives` screener endpoints
are regular-session oriented. Default scan time is therefore **09:05 ET**
(editable). When data is too sparse/early or credentials are missing, show an
empty / credential-required state — **never synthetic data**.

---

## 2. What already exists (REUSE — do not rebuild)

| Concern | Existing code | Notes |
|---|---|---|
| Screener (movers / most-actives) | `service/HttpAlpacaScreenerClient` (`AlpacaScreenerClient` iface) | `getMarketMovers(top)`, `getMostActives(by, top)`. Alpaca auth headers + 401/403/429/timeout handling already done. |
| Screener parsing/scoring example | `service/TrendingStocksService` | Good template for union + filter + dedupe of screener JSON. |
| Per-symbol gap/volume scan | `gaprocket/GapRocketLiveScanner.candidates(List<String>)` | Computes gap %, relative volume, spread, VWAP from live bars into `GapRocketCandidate`. Already "no hardcoded tickers". |
| Filtering + scoring (0–100) | `gaprocket/GapRocketAnalyzer` | `MINIMUM_RECOMMENDATION_SCORE = 70`. Already grants **+20 when `catalystType != null`** and rejects when `newsCatalystRequired` and catalyst missing. Sorts, caps at `maxStocksToAdd` (default 10). |
| Candidate/recommendation models | `gaprocket/GapRocketCandidate`, `gaprocket/GapRocketRecommendation` | Both already carry `catalystType` + `catalystSummary` — currently always `null`. |
| Config | `gaprocket/GapRocketConfig` | Has `newsCatalystRequired`, `catalystTypes`, `ExecutionFrequency` enum (MANUAL / EVERY_5_MINUTES / EVERY_15_MINUTES / MARKET_OPEN_ONLY), `PRIMARY_WINDOW_START_ET=09:45`, `PRIMARY_WINDOW_END_ET=11:00`, `candidateSymbols`. |
| Dialog | `gaprocket/GapRocketAnalysisDialog` | Title already says "Executes 9:45 AM ET to 11:00 AM ET". Has `candidateSymbols` text area + `Analyze` / `Analyze & Execute` buttons. Inert "News Catalyst Required" checkbox. |
| Empty-state panel + button | `gaprocket/GapRocketPanel` | "Analyze Gap Stocks" button + description. |
| Strategy creation | `gaprocket/GapRocketStrategyFactory.toStrategy(...)` | Turns a recommendation into a `Strategy`. Uses `latestOrderStatus` `GAP_ROCKET_RECOMMENDED` / `GAP_ROCKET_MONITORING`. |
| UI wiring | `ui/TradingFrame` | `openGapRocketAnalysisDialog()`, `addGapRocketRecommendations(config, executeRequested)` (runs on `uiPollingExecutor`, applies via `invokeLater`), `applyGapRocketRecommendations(...)`, `placeAllGapRocketPendingLimitBuys()`. Workspace code `GAPROCKET`. |
| Bars API | `api/AlpacaMarketDataApi` (`HttpAlpacaMarketDataApi`) | `getDailyBars`, `getIntradayBars(symbol,start,end,intervalMinutes)`. |
| Market hours | `service/MarketHoursService` | Regular/extended hours, holidays — use for scheduling gating. |
| **OpenAI integration (news analysis)** | `service/OpenAiRecommendationProvider`, `service/AiRecommendationProviderFactory`, `service/AiRecommendationService`, `model/AiRecommendationRequest`/`Response`, `service/AiRecommendationJsonMapper` | **Already enables OpenAI `web_search` tool** and requests `web_search_call.action.sources`. Default model `gpt-5`. Returns strict-JSON action + confidence + reasons + risks + sources. This IS the "analyze news/articles automatically" capability — no Google, no separate news API required. |
| AI settings persistence | `service/AppSettingsService.loadAiRecommendationSettings()` / `saveAiRecommendationSettings(...)`; key `KEY_AI_OPENAI_API_KEY` (stored encrypted) | Lets a headless scheduled run load the OpenAI key. |
| Parallel execution pattern | `ui/SmartPicksParallelExecutor` (`mapPreservingOrder`) | Capped pool, preserves order — use to fan out per-symbol news calls off the EDT. |
| Persistence pattern | `db/AppDatabase.applyMigrations()` (append-only; currently up to `008_strategy_workspaces`), `db/Sqlite*Repository` | New migration must be `009_...`; never edit existing migrations. |

**Key insight:** the two "hard" pieces the customer imagined (news scraping +
AI analysis) are already solved by `OpenAiRecommendationProvider`'s built-in web
search. The remaining work is orchestration: discovery, catalyst mapping,
scheduling, autonomous execution, and consolidating the UI control.

---

## 3. Architecture decisions (locked)

- **No Google.** News/article analysis uses the existing OpenAI web-search path.
- **No separate news API for v1.** OpenAI web search returns catalyst + sources.
  (A future Alpaca News pre-filter is optional, see §8.)
- **Auto-discovery is the default;** manual symbol entry is an optional override
  (if the operator typed symbols, use them; otherwise auto-discover).
- **Cost/latency control:** only call OpenAI on the gap/volume **survivors**
  (~10–15 names), never the full screener list. Cap concurrency via
  `SmartPicksParallelExecutor`.
- **Graceful degradation:** if no OpenAI key is configured, skip news scoring and
  rank on Alpaca gap/volume metrics only. If credentials/data are missing, show
  empty/credential state — never synthetic data.
- **Threading:** all screener/bars/news/broker I/O on background executors; only
  grid/state updates on the EDT (repo invariant).
- **Scheduling reality:** NeuralArc is a local desktop console with no cloud
  cron. The app must be running at the scheduled time; surface this in the UI.
- **Keep `TradingFrame` thin — extract into separate classes.** `TradingFrame`
  is already **5000+ lines**. Do NOT pour new gap-and-go logic into it. Put the
  orchestration (discovery → scan → news enrichment → analyze → schedule →
  execute) into dedicated, independently testable classes (e.g. a
  `GapAndGoCoordinator` / controller, plus the services below), mirroring the
  existing split (`StrategyActionsController`, `PortfolioActionsController`,
  `TradeStreamLifecycleCoordinator`, `ConnectionLifecycleCoordinator`,
  `TradingRuntimeSupport`). `TradingFrame` should only **wire** these in and
  forward UI events — ideally a net-neutral or shrinking line count, not growth.
  Respect the class-size limits in `AGENTS.md`.

---

## 4. Phased implementation

### Phase 1 — Auto-discovery of candidates
**New:** `service/GapAndGoDiscoveryService`
- Constructor: `GapAndGoDiscoveryService(AlpacaScreenerClient screener)`
  (bars not needed here; the precise gap/relvol recompute happens later in
  `GapRocketLiveScanner`).
- Method: `List<String> discoverCandidates(GapRocketConfig config, int maxSymbols)`.
- Logic:
  1. `screener.getMarketMovers(...)` (gainers) + `screener.getMostActives("volume", ...)`;
     union symbols (mirror parsing style in `TrendingStocksService`).
  2. Cheap pre-filter on screener fields: price within
     `[minimumStockPrice, maximumStockPrice]`, change% ≥
     `minimumPremarketGapPercent`.
  3. Return top ~`maxSymbols` (use ~30; analyzer will reject some after the
     bar-based recompute, so over-fetch to still land 10).
- Pure orchestration over live endpoints; respects market-data discipline.

**Wire into `TradingFrame.addGapRocketRecommendations(...)`:**
- If `config.candidateSymbols()` is **empty** (now the expected default), call
  `discoverCandidates(...)` before `scanner.candidates(...)` instead of warning.
- If symbols were typed, keep using them (manual override wins).

**`GapRocketAnalysisDialog` changes:**
- Relabel `candidateSymbols` → "Candidate Symbols (leave blank to auto-discover
  via Alpaca screener)" and make it optional. Default flow = auto-discover 10.

**Tests:** `GapAndGoDiscoveryServiceTest` — fake `AlpacaScreenerClient` returning
canned JSON; assert union/filter/cap and price/change filtering.

---

### Phase 2 — News catalyst via existing OpenAI integration
**New:** `gaprocket/NewsCatalystResolver` (or `service/`)
- Dependency: `AiRecommendationService` / `AiRecommendationProvider` (from
  `AiRecommendationProviderFactory.create(settings)` using
  `AppSettingsService.loadAiRecommendationSettings()`).
- Method: `GapRocketCandidate enrich(GapRocketCandidate candidate)` — builds an
  `AiRecommendationRequest` for the symbol, calls `analyzeStock(...)`, maps the
  AI response to:
  - `GapRocketConfig.CatalystType` (EARNINGS / FDA_BIOTECH / ANALYST_UPGRADE /
    CONTRACT_PARTNERSHIP / GENERAL_BREAKING_NEWS), inferred from the AI
    reasons/summary keywords;
  - `catalystSummary` string (AI summary + key sources);
  - returns a copy of the candidate with those fields populated.
- Recency: prefer catalysts the AI ties to recent (last ~24–48h) news; if the AI
  finds nothing material, leave `catalystType = null` (analyzer then withholds
  the +20 and, if required, rejects).
- **Fallback:** if OpenAI key missing or call fails → return candidate unchanged
  (no catalyst), log, continue. Never fabricate a catalyst.

**Enrichment step (in `TradingFrame` background flow or a small orchestrator):**
- After `scanner.candidates(...)`, fan out `NewsCatalystResolver.enrich(...)`
  over survivors via `SmartPicksParallelExecutor.mapPreservingOrder(...)` (capped
  pool). Off the EDT. Then pass enriched candidates to `GapRocketAnalyzer`.
- The "News Catalyst Required" checkbox becomes functional with **no analyzer
  change** (analyzer already filters/scores on catalyst presence).

**Tests:**
- `NewsCatalystResolverTest` — mock `AiRecommendationProvider`; assert response →
  `CatalystType` mapping, summary/sources passthrough, missing-key fallback.
- Extend `GapRocketAnalyzerTest` — verify catalyst-required rejection and +20
  scoring driven by resolved catalysts.

---

### Phase 3 — Consolidated single action control
Replace the dialog's two buttons (`Analyze`, `Analyze & Execute`) and the new
scheduling action with **one split/primary button: "Run Gap-and-Go ▾"** offering:
- **Analyze now** — scan + recommend into the grid (current `accepted` + not
  `executeRequested`).
- **Analyze & Execute now** — scan + arm post-open monitoring/entry
  (`executeRequested = true`).
- **Schedule (premarket)** — register a recurring schedule (Phase 4) instead of
  running immediately.

Implementation:
- `GapRocketAnalysisDialog` exposes an enum result, e.g.
  `enum RunMode { ANALYZE, ANALYZE_AND_EXECUTE, SCHEDULE }` plus existing
  `config()`. Replace `accepted()/executeRequested()` usages in `TradingFrame`
  with a single `runMode()` switch.
- Use a Swing split-button (or a `JButton` + popup `JPopupMenu`) consistent with
  existing FlatLaf styling. Footer collapses from two buttons to one chooser
  (+ Cancel).
- Keep the panel/grid look identical (consistency requirement).

**Tests:** keep dialog logic testable — unit-test the `RunMode` resolution and
that `config()` still round-trips fields (headless Swing tests may be limited;
favor extracting non-UI logic).

---

### Phase 4 — Scheduling engine (autonomous)
**New:** `service/GapAndGoScheduleService`
- Backed by a `ScheduledExecutorService`; gated by `MarketHoursService` so it
  never fires on weekends/holidays/closed days.
- Schedule entry fields:
  `{ id, enabled, scanTimeET (default 09:05), rescanFrequency (reuse
  GapRocketConfig.ExecutionFrequency), executionWindowStart=09:45,
  executionWindowEnd=11:00, mode (PAPER/LIVE), executeAfterScan (bool),
  configSnapshot (GapRocketConfig), workspaceId }`.
- Behavior on scan trigger:
  1. Run discovery → `GapRocketLiveScanner` → `NewsCatalystResolver` enrichment
     → `GapRocketAnalyzer` → push recommendations to grid via `invokeLater`.
  2. Optional re-scan within the window per `rescanFrequency`.
  3. If `executeAfterScan` → arm/place post-open entries (Phase 5).
- Load OpenAI + Alpaca credentials headlessly via `AppSettingsService` /
  `CredentialManager` so no dialog interaction is needed.

**New persistence:** `db` migration `009_gap_and_go_schedules` (append-only — add
to `applyMigrations()`, never edit `001`–`008`) + `SqliteGapAndGoScheduleRepository`
following existing `Sqlite*Repository` cache-with-invalidation pattern. Survives
restart; document that the **app must be running** at the scheduled time.

**UI:**
- Schedule section in the dialog (scan time, frequency, execute-after-scan
  toggle, mode), shown when "Schedule" chosen.
- Gap Rocket panel status badge: "Next scan: 09:05 ET" / "Scheduled", plus a
  cancel control.
- Surface the "NeuralArc must be open at the scheduled time" expectation.

**Tests:**
- `GapAndGoScheduleServiceTest` — fake `Clock` + `MarketHoursService`: fires at
  scan time, skips weekends/holidays, respects re-scan cadence, no fire when
  disabled, honors execution window.
- `SqliteGapAndGoScheduleRepositoryTest` — persistence round-trip + migration
  applied.

---

### Phase 5 — Autonomous execution (post-open)
Goal: "almost no watching or monitoring."
- When `executeAfterScan` is on, after the scan the recommendations are not just
  listed but armed: reuse `GapRocketStrategyFactory.toStrategy(..., executeRequested=true, ...)`
  and the existing `placeAllGapRocketPendingLimitBuys()` / strategy activation
  path so entries fire during the post-open window per the configured entry style
  (opening-range breakout / retest / VWAP) already modeled in `GapRocketConfig`.
- **Safety:** default `executeAfterScan = false`, especially in LIVE mode
  (paper-by-default invariant). Require explicit opt-in. Respect existing
  live-trading guards.
- Ensure idempotency with polling/streaming (repo invariant): do not double-place
  if a strategy for the symbol already exists (see existing
  `findGapRocketTrackedStrategy` / `isGapRocketPendingOrderPlacement`).

**Tests:** verify execute-after-scan arms strategies via the factory and that an
existing tracked symbol is updated, not duplicated.

---

## 5. New / changed files summary

New:
- `src/main/java/com/neuralarc/service/GapAndGoDiscoveryService.java`
- `src/main/java/com/neuralarc/gaprocket/NewsCatalystResolver.java`
- `src/main/java/com/neuralarc/service/GapAndGoScheduleService.java`
- `src/main/java/com/neuralarc/db/SqliteGapAndGoScheduleRepository.java`
- `src/main/java/com/neuralarc/model/GapAndGoSchedule.java` (schedule record)
- `src/main/java/com/neuralarc/ui/GapAndGoCoordinator.java` — owns the
  discovery → scan → enrich → analyze → schedule → execute orchestration and the
  background/EDT hand-off, so `TradingFrame` only wires it in.
- Tests mirroring each under `src/test/java/com/neuralarc/...`

Changed:
- `gaprocket/GapRocketAnalysisDialog.java` — optional symbols, consolidated
  `RunMode` control, schedule section.
- `gaprocket/GapRocketPanel.java` — schedule status badge / cancel.
- `ui/TradingFrame.java` — auto-discovery wiring, news enrichment step,
  `RunMode` handling, schedule registration, headless credential load.
- `db/AppDatabase.java` — add `applyMigration("009_gap_and_go_schedules", ...)`
  + `migration009()`.

(Watch class-size limits in `AGENTS.md`; `TradingFrame` is already 5000+ lines —
all new behavior goes into `GapAndGoCoordinator` and the services above.
`TradingFrame` changes should be limited to constructing and wiring these and
forwarding UI events, keeping its line count flat or shrinking, never growing.)

---

## 6. Commit sequence (branch `claude/epic-gates-jlnwiu`)
1. `docs: gap-and-go autonomous plan` (this file). ← current step
2. Phase 1: discovery service + tests + auto-discovery wiring + dialog relabel.
3. Phase 2: `NewsCatalystResolver` + enrichment fan-out + tests.
4. Phase 3: consolidated `RunMode` control in dialog + `TradingFrame` switch.
5. Phase 4: schedule service + model + migration `009` + repository + UI + tests.
6. Phase 5: autonomous post-open execution + safety guards + tests.

Run `./gradlew test` before each commit. Keep commits scoped per phase.

---

## 7. Acceptance criteria
- Operator can open Gap Rocket, leave symbols blank, click "Run Gap-and-Go" →
  top ~10 live gappers appear with catalyst summaries, no manual symbols.
- "News Catalyst Required" actually filters using OpenAI-derived catalysts.
- A schedule set for 09:05 ET fires premarket (when app is open), scans, and —
  if execute-after-scan — arms post-open entries autonomously.
- No hardcoded tickers / canned prices / synthetic candidates anywhere.
- Missing credentials/data → empty/credential state, not fabricated data.
- All new code covered by tests; `./gradlew test` green.

---

## 8. Deferred / optional (not in v1)
- Alpaca News API (`/v1beta1/news`) as a cheap pre-filter before OpenAI to cut
  cost/latency. Would add `AlpacaNewsClient`/`HttpAlpacaNewsClient` + `NewsArticle`
  using the same auth pattern as `HttpAlpacaScreenerClient`.
- SIP feed support to move the scan earlier into premarket.
- Multiple schedules / multiple workspaces.

---

## 9. Open questions resolved
- Scan time: **09:00–09:25 ET (default 09:05)**, no SIP. ✅
- Consolidate the three actions into one control. ✅
- Autonomous execution important (opt-in, paper-safe default). ✅
- OpenAI integration already present (with web search) — reuse it. ✅
