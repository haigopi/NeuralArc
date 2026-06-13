# Design: Smart Picks Driven Strategy Workspaces

Status: **Proposal / for review** — no production code is changed by this document.
Owner: TBD · Target: phased delivery across multiple PRs.

This document specifies how to make NeuralArc *strategy-centric* (users think in
strategy workspaces such as "ORB Engine" / "VWAP Desk") while keeping the existing
stock-centric experience working untouched for current installations. It is grounded
in the current codebase and follows the rules in `AGENTS.md` (class-size limits, Swing
EDT discipline, append-only SQLite migrations, snapshot-driven UI, Paper/Live safety).

---

## 1. Terminology (important — naming collision)

NeuralArc **already** has a type called `Strategy`
(`model/Strategy.java`): it is a *per-symbol automated trade plan* (one grid row, one
symbol, its own orders and `Position`). The product spec uses the word "strategy" to
mean a higher-level **book/grouping** that can own many symbols (e.g. ORB Engine).

To avoid confusion we introduce a distinct name for the new concept:

| Concept | Spec word | This codebase |
| --- | --- | --- |
| Per-symbol automated trade plan (existing) | "stock" / position | `Strategy` (unchanged) |
| Higher-level grouping / book (new) | "strategy" | **`StrategyWorkspace`** |

Throughout this doc, **Workspace** = the new grouping; **Strategy** = the existing
per-symbol plan. UI labels will say "strategy" to match the product language, but the
code uses `StrategyWorkspace` to keep the two unambiguous.

---

## 2. Core modeling decision

**A `StrategyWorkspace` groups existing `Strategy` rows; it does not replace them.**

```
StrategyWorkspace "ORB Engine" (code ORB, mode PAPER)
  ├── Strategy(symbol=NVDA)   → Position 10 @ 120   (existing per-symbol accounting)
  └── Strategy(symbol=TSLA)   → Position  4 @ 250

StrategyWorkspace "VWAP Desk" (code VWAP, mode PAPER)
  └── Strategy(symbol=NVDA)   → Position  5 @ 124

Alpaca (broker truth):  NVDA 15 @ blended avg
```

Why grouping (vs. a brand-new position ledger)?

- **Reuses existing accounting.** Each `Strategy` already maintains its own `Position`
  from its own `StrategyOrder` fills. Strategy-level books (ORB's NVDA vs VWAP's NVDA)
  already work because they are *separate `Strategy` rows*. We do not need a new
  position table or a new fill engine — we aggregate what exists.
- **Minimal disruption.** No change to the order/fill/poll path. We add a nullable
  `workspaceId` link and aggregate upward.
- **Backward compatible.** Strategies with `workspaceId = NULL` are simply "unassigned"
  and shown under **All Stocks**.

Workspace-level position for a symbol = sum of member strategies' positions for that
symbol. Workspace P&L = sum of member strategies' realized/unrealized P&L.

> Multiple strategies on the same symbol: confirm `DuplicateSymbolPolicy`
> (`ui/DuplicateSymbolPolicy.java`) allows two active `Strategy` rows for the same
> symbol in the same mode. If it currently blocks duplicates, Phase 3 must relax it to
> "allowed when the strategies belong to different workspaces." (Open question Q1.)

---

## 3. Backward compatibility & migration

Hard rules (from the spec) and how we honor them:

1. Existing strategies/positions/orders/history are **never** modified or reassigned.
2. New SQLite objects are **additive** (new table + one nullable column), applied via a
   new append-only migration `008_strategy_workspaces` in `AppDatabase.applyMigrations()`
   (current latest is `007_base_buy_repost_reduction_percent`). Existing migrations are
   not edited.
3. On first launch after upgrade, **no** workspace exists and **no** strategy has a
   `workspaceId`. Everything appears under the **All Stocks** tab exactly as today.
4. The tab currently titled **"Current Strategies"** is renamed to **"All Stocks"**
   (`TradingFrame.currentStrategiesHeadingText()`, ~line 5542). "All Stocks" always
   shows *every* strategy in the active mode regardless of workspace assignment — it is
   the unchanged, stock-centric view.
5. Strategy-aware grouping is **opt-in**: it only starts once a user creates a workspace
   from Smart Picks and assigns/creates strategies into it.
6. Reads are written to tolerate `workspaceId = NULL` and a missing workspace table on
   downgrade is impossible (append-only), but repositories default safely if the table
   is empty.

A dedicated backward-compat test (Phase 1) opens a DB seeded with the *old* schema
fixture, runs migration 008, and asserts existing strategies load unchanged with
`workspaceId == null` and surface under All Stocks.

---

## 4. Data model changes (minimal)

### 4.1 New: `model/StrategyWorkspace.java`
Immutable value type (record-style, consistent with repo conventions):

| Field | Type | Notes |
| --- | --- | --- |
| `id` | `String` | UUID primary key |
| `name` | `String` | Display name, e.g. "ORB Engine" |
| `code` | `String` | Short uppercase code, e.g. `ORB` (used in `client_order_id`) |
| `mode` | `StrategyMode` | PAPER / LIVE — **isolation key** |
| `archived` | `boolean` | Hidden by default; not deleted |
| `createdAt` | `Instant` | |
| `updatedAt` | `Instant` | |

`code` rules: derived from name (uppercase, alphanumerics, ≤ 8 chars), unique per mode,
collision-suffixed (`ORB`, `ORB2`). Reserved code `ALL` maps to All Stocks / unassigned.

### 4.2 Changed: `model/Strategy.java`
Add **one** nullable field `workspaceId` (`String`, default `null`). Reads provide a
sensible default for legacy rows (null → unassigned). All existing constructors keep
working via an overload that defaults `workspaceId = null` (no churn at call sites).

### 4.3 Unchanged / already present
- `StrategyOrder` already has `strategyId` and `clientOrderId` — **no change needed**.
- `Position` stays per-symbol and in-memory (derived from fills). Workspace positions are
  computed by aggregation, not stored. No new column.

---

## 5. Persistence

### 5.1 Migration `008_strategy_workspaces` (append-only)
```sql
CREATE TABLE IF NOT EXISTS strategy_workspaces (
    id          TEXT PRIMARY KEY,
    name        TEXT NOT NULL,
    code        TEXT NOT NULL,
    mode        TEXT NOT NULL,          -- 'PAPER' | 'LIVE'
    archived    INTEGER NOT NULL DEFAULT 0,
    created_at  TEXT NOT NULL,
    updated_at  TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_workspaces_mode ON strategy_workspaces(mode);
CREATE UNIQUE INDEX IF NOT EXISTS idx_workspaces_code_mode ON strategy_workspaces(code, mode);

-- link existing strategies to a workspace (nullable; legacy rows stay NULL)
ALTER TABLE strategies ADD COLUMN workspace_id TEXT;   -- via addColumnIfMissing(...)
```
Use the existing `addColumnIfMissing(...)` helper for the `ALTER TABLE` so re-runs are
safe, mirroring migrations 003–007.

### 5.2 New: `db/SqliteWorkspaceRepository.java`
Mirrors `SqliteStrategyRepository`: a `WorkspaceRepository` interface + SQLite
implementation with an in-memory `LinkedHashMap<String, StrategyWorkspace>` cache and
invalidate-on-write. Methods: `save`, `findById`, `findAll(mode)`, `findActive(mode)`
(non-archived), `archive(id)`, `deleteById(id)` (hard delete reserved for empty
workspaces; normal "delete" = archive — see §8).

`SqliteStrategyRepository` gains read/write of the new `workspace_id` column (backward
compatible: missing/NULL → `workspaceId = null`).

### 5.3 Wiring
Construct `SqliteWorkspaceRepository` next to the other Sqlite repos and inject it where
needed (a new `WorkspaceService` in `service/`, see §9). Follow existing DI in
`TradingRuntimeSupport` / `TradingFrame`.

---

## 6. `client_order_id` scheme

### 6.1 Format
```
NA_<MODE>_<STRATEGYCODE>_<SYMBOL>_<TIMESTAMP>_<SHORTUUID>
e.g.  NA_PAPER_ORB_NVDA_20260613103015_A1B2
      NA_LIVE_VWAP_TSLA_20260613110522_B8C4
```
- `MODE` = `PAPER` | `LIVE`
- `STRATEGYCODE` = workspace code, or `ALL` when the strategy is unassigned
- `TIMESTAMP` = `yyyyMMddHHmmss` (UTC)
- `SHORTUUID` = first 4 hex chars of a UUID (collision-tolerant; full uniqueness comes
  from timestamp+strategy)

### 6.2 New util `util/ClientOrderId.java`
Pure, fully unit-tested: `build(mode, code, symbol, instant)` and a `parse(...)`
returning an `Optional<ClientOrderId>` record (mode, code, symbol, timestamp, uuid).
Alpaca limits `client_order_id` to 128 chars and to a safe charset — `build` sanitizes
symbol/code and truncates defensively.

### 6.3 Adoption (non-breaking)
`StrategyService.buildClientOrderId(strategyId, stage)` currently returns
`neuralarc-<id>-<STAGE>-<ts>`. We **add** the new format behind the order path while
keeping the old one parseable for in-flight/historical orders:
- New orders use `ClientOrderId.build(...)`; the existing `StrategyOrder.clientOrderId`
  column stores it (no schema change).
- Reconciliation (§10) parses both the new format and the legacy `neuralarc-...` form.
- Stage is still needed for engine logic; we keep it by also persisting `stage` on
  `StrategyOrder` (already a column) rather than encoding it in the id. (Open question
  Q2: do we want stage embedded in the id too? Default: no, to keep the spec's format.)

---

## 7. Strategy-level position accounting

No new ledger. For a workspace `W` in mode `M`:
- members = `strategyRepository.findAll()` filtered by `mode == M && workspaceId == W.id`.
- `W.position(symbol)` = aggregate of each member's `Position` for that symbol
  (shares, weighted-average cost, realized + unrealized).
- `W.pnl` = Σ member realized + Σ member unrealized; daily P&L from the existing
  day-scoped P&L source used by the status bar.

This is a read-time aggregation built from cached snapshots (keeps renderers broker-call
free, per `AGENTS.md`). A small `WorkspaceAccounting` helper (pure, unit-tested) does the
math so it is reusable by the tab P&L panel, the top status bar, and the risk dashboard.

---

## 8. Sell rules / over-sell prevention

Per-strategy selling is **already** bounded by the owning `Strategy`'s `Position`
(a strategy sells from its own book). The workspace layer adds an explicit guard and a
clear error, satisfying the spec:

- A sell request for `Strategy s` may not exceed `s.position.totalShares`.
- A workspace-level "sell all" iterates members and sells each within its own holdings;
  it can never reach into another workspace's shares (they are different `Strategy` rows).
- Reject with a typed result (e.g. `SellRejected(reason="Strategy owns only N")`) — no
  silent clamping. Surface as the existing warning dialog style.

This prevents accidental over-sell and "strategy contamination". Tests cover ORB selling
> its own NVDA, and selling NVDA owned by VWAP via ORB (rejected).

---

## 9. Services

- New `service/WorkspaceService.java`: create / rename / archive / delete workspaces,
  assign a `Strategy` to a workspace, list workspaces by mode, generate unique `code`.
  Enforces Paper/Live isolation (a workspace's mode must match its members' mode).
- New `service/WorkspaceAccounting.java` (pure): aggregation math (§7) + a
  `Snapshot` record for the UI.
- `StrategyService` gains: set/clear `workspaceId` on a strategy; use
  `ClientOrderId.build` when submitting; expose member queries.
- New `service/ReconciliationService.java` (§10).

All service mutations emit an audit event (§12).

---

## 10. Reconciliation engine

`service/ReconciliationService.java`:
1. Pull Alpaca positions (per symbol, per mode) via the existing `AlpacaClient`.
2. Aggregate NeuralArc positions per symbol = Σ over all strategies in that mode.
3. Compare quantity and cost basis per symbol; classify `MATCH | QTY_MISMATCH |
   COST_MISMATCH | MISSING_LOCAL | MISSING_BROKER`.
4. Produce a `ReconciliationReport` (immutable). **Never auto-correct** — surface
   warnings in the risk dashboard and as a status indicator.

Runs off the EDT on the existing background executor; UI renders the cached report.

---

## 11. UI structure

### 11.1 Phase-1 visible change
Rename the strategies tab title to **All Stocks** (keep the count + mode suffix:
"All Stocks — Live (20)"). No other UI change in Phase 1.

### 11.2 Dynamic strategy tabs (Phase 2)
- `strategyTabs` gains one tab per non-archived workspace in the active mode, built
  dynamically (create/rename/archive/delete, no restart). "All Stocks" stays first;
  "Trade History" stays last.
- Each workspace tab reuses the existing strategies grid component, filtered to that
  workspace's member strategies (snapshot filter, like the current search filter).
- Tab management UI: right-click tab → rename / archive / delete; a "Manage strategies"
  dialog lists archived ones (hidden by default).
- Deleting = archive + remove active tab; historical records (orders/history/events)
  are retained.

### 11.3 Per-tab P&L summary (Phase 3)
Each workspace tab shows: Realized, Unrealized, Daily P&L, Win Rate, Open/Closed counts,
Total Capital Allocated — computed by `WorkspaceAccounting`.

### 11.4 Top status bar (Phase 3)
Keep current Total P&L behavior but make it the **portfolio-wide** aggregate across
All Stocks + every workspace (already equivalent to "all strategies in mode", so this is
mostly a labeling/verification change with a test asserting totals == Σ tabs).

### 11.5 Risk dashboard (Phase 4)
New panel/dialog: exposure by strategy & by symbol, P&L by strategy, capital allocation,
largest winner/loser, performance ranking, and reconciliation/over-exposure/concentration
warnings. Reads cached snapshots + the reconciliation report.

---

## 12. Smart Picks integration

Smart Picks becomes the **strategy creation center**. Today
(`configureSmartPicksMenu`, `SmartPicksTrendingStocksDialog`, `StrategyUniverse`) it
suggests *stocks*. We add a **strategy templates** surface:

- Template catalog (static, code-defined first; persisted later): ORB Engine, VWAP Desk,
  Momentum Lab, Swing Vault, Dip Hunter, Profit Shield, Earnings Hunter, Manual Trades,
  Custom Strategy — each with a `name`, `code`, and description.
- Each template card has a **Create** button → `WorkspaceService.create(template, mode)`
  → new tab appears immediately (no restart) → ready to trade.
- Existing stock-suggestion flows remain available inside a workspace.

This is Phase 2 work, layered on the Phase 1 foundation.

---

## 13. Mode awareness & auditability

- Every workspace, query, aggregation, tab, and `client_order_id` is scoped by
  `StrategyMode`. Paper and Live data never mix; the active view mode drives all lists.
- Auditability: a small append-only audit record (reuse
  `Sqlite*ExecutionEventRepository` style or a new `workspace_audit` table) capturing
  `{workspace, mode, action, symbol, orderId, timestamp, result}` for create/rename/
  archive/assign/sell/reconcile. Required for debugging/reporting.

---

## 14. Phasing & PR plan

| Phase | Scope | Risk | Key deliverables |
| --- | --- | --- | --- |
| **1 — Foundation** | model + persistence + id scheme + tab rename | Low (additive) | `StrategyWorkspace`, migration 008, `SqliteWorkspaceRepository`, `Strategy.workspaceId`, `ClientOrderId` util, rename tab, tests #8/#13/#14/#15 + id/format tests |
| **2 — UI & Smart Picks** | dynamic tabs + one-click create | Medium (UI) | dynamic workspace tabs, tab management, Smart Picks templates + Create, tests #6/#7/#9 |
| **3 — Accounting** | per-tab P&L, top-bar aggregation, sell rules | Medium-High (core paths) | `WorkspaceAccounting`, per-tab P&L panel, top-bar verify, over-sell guard, `client_order_id` adoption in order path, tests #1/#2/#3/#4/#5/#10/#11 |
| **4 — Reconciliation + Risk** | reconciliation engine + risk dashboard | Medium | `ReconciliationService`, risk dashboard panel, warnings, test #12 |

Each phase is its own PR, independently shippable, behind the backward-compat guarantee
(nothing user-visible breaks if a later phase is not yet merged).

---

## 15. Test matrix (maps the 15 required tests)

| # | Required test | Phase | Where |
| --- | --- | --- | --- |
| 1 | Same stock owned by multiple strategies | 3 | `WorkspaceAccountingTest` |
| 2 | Strategy-specific buy averaging | 3 | `WorkspaceAccountingTest` |
| 3 | Strategy-specific selling | 3 | `StrategyServiceTest` (sell scope) |
| 4 | Over-sell prevention | 3 | `StrategyServiceTest` |
| 5 | Mode isolation | 1→3 | `WorkspaceServiceTest`, `WorkspaceAccountingTest` |
| 6 | Dynamic strategy creation | 2 | `WorkspaceService` + UI test |
| 7 | Smart Picks one-click create | 2 | Smart Picks controller test |
| 8 | Strategy persistence | 1 | `SqliteWorkspaceRepositoryTest` |
| 9 | Deletion/archive | 2 | `WorkspaceServiceTest` |
| 10 | P&L aggregation | 3 | `WorkspaceAccountingTest` |
| 11 | Top status bar totals | 3 | status-bar/aggregation test |
| 12 | Reconciliation mismatches | 4 | `ReconciliationServiceTest` |
| 13 | Backward-compat migration | 1 | `AppDatabase` migration test |
| 14 | Existing users open upgraded product | 1 | migration + load test |
| 15 | All Stocks tab behavior | 1 | tab-title + filter test |

---

## 16. Open questions

- **Q1.** Does `DuplicateSymbolPolicy` currently allow two active `Strategy` rows for the
  same symbol/mode? If not, Phase 3 must scope the policy by workspace.
- **Q2.** Embed `stage` in `client_order_id` too, or keep the spec's exact format and rely
  on the persisted `StrategyOrder.stage`? (Default: keep spec format.)
- **Q3.** Are workspace **templates** static-in-code for v1, or persisted/editable from the
  start? (Default: static in v1, persisted later.)
- **Q4.** "Win Rate" definition — closed strategies with realized P&L > 0 over total
  closed? Confirm the formula for the P&L summary.
- **Q5.** Hard-delete policy — only allow hard delete of an empty, never-traded workspace;
  otherwise archive. Confirm.

---

## 17. Non-goals (v1)

- No attempt to make Alpaca track per-strategy positions (broker stays blended).
- No automatic assignment of existing positions to workspaces.
- No cross-mode workspaces.
- No silent auto-correction during reconciliation.
