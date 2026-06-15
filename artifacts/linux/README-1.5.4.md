# NeuralArc Linux Release 1.5.4

## Artifact
- File: NeuralArc-1.5.4.deb
- Path: artifacts/linux/NeuralArc-1.5.4.deb

## Install
1. Install the DEB package (for Debian/Ubuntu-based distributions).
2. Launch NeuralArc from applications menu.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  sha256sum artifacts/linux/NeuralArc-1.5.4.deb

## Changes
- Merge pull request #36 from haigopi/claude/epic-gates-jlnwiu (ee524d2)
- Extract GapAndGoCoordinator, add schedule badge, fix flaky tests (970cb3b)
- Phase 4: autonomous premarket scheduling for gap-and-go (f9bf42a)
- Phase 3: consolidate gap-and-go actions into one Run control (47beb31)
- Migrate project to Java 21 (3470bfb)
- Phase 2: live news-catalyst analysis via the AI web-search provider (f36e120)
- Phase 1: auto-discover gap-and-go candidates from Alpaca screener (1cf6d40)
- docs: keep TradingFrame thin — extract gap-and-go logic into separate classes (4a6c4cf)
- docs: add autonomous gap-and-go discovery, news analysis & scheduling plan (518b252)
- Merge pull request #35 from haigopi/codex/fix-section-borders-for-titles-and-labels (e08c5df)
- Make collapsible section titles part of borders (13670c4)
- Update GapRocketPanel.java (a9764a4)
- Merge pull request #34 from haigopi/codex/enable-edit-mode-on-row-double-click (2016f23)
- Persist Gap Rocket live candidate symbols (3817a01)
- Merge pull request #33 from haigopi/codex/fix-grid-action-button-positioning (8ff3caa)
- Fix selected strategy action row alignment (a57e1f3)
- Merge pull request #32 from haigopi/codex/remove-grid-borders-and-update-color (f5fea83)
- Soften strategy grid borders (ff3d58d)
- Merge pull request #31 from haigopi/codex/restore-column-borders-and-adjust-styles (38cb833)
- Restore strategy grid borders (908b80c)
- Merge pull request #30 from haigopi/codex/fix-logs-section-collapsing-issue (f334867)
- Fix collapsible section layout (06281b5)
- Merge pull request #29 from haigopi/codex/open-default-tab-when-no-stocks-left (0419f0e)
- Refine gap rocket empty state and entries (f51335c)
- Merge pull request #28 from haigopi/codex/make-logs-and-sections-collapsible (59c99f0)
- Make operator detail sections collapsible (56c9839)
- Update TradingFrame.java (221c672)
- Merge pull request #26 from haigopi/claude/dedupe-gaprocket-momentum (16bb597)
- Merge pull request #27 from haigopi/codex/fix-stock-display-issues-in-gap-rocket-tab (59623c7)
- Fix Gap Rocket workspace visibility (94f6bd4)
- De-duplicate momentum theme: fold Momentum Lab into Gap Rocket (e44d62a)
- Merge pull request #25 from haigopi/codex/implement-gap-rocket-strategy-in-neuralarc (d497302)
- Suppress broker PnL for pending Gap Rocket rows (e4e0cae)
- Isolate Gap Rocket pending PnL (12373b9)
- Add Gap Rocket order placement controls (c4f236e)
- Include Gap Rocket recommendation rows in grid (3e91b8a)
- Clarify Gap Rocket empty-state trading terms (5f7e3fa)
- Show existing Gap Rocket grid for duplicates (d38c5ab)
- Add Gap Rocket tab field guidance (25f4c72)
- Show Gap Rocket grid after analysis (158ad95)
- Add Gap Rocket recommendations from analyze dialog (32d99a8)
- Refine Gap Rocket empty-state copy and tooltips (23b6546)
- Show Gap Rocket empty-state analyze action (4c6476e)
- Add Gap Rocket smart picks strategy foundation (2cd1f34)
- Merge pull request #24 from haigopi/claude/phase4-strategy-workspaces (9fd642c)
- Merge pull request #20 from haigopi/claude/phase1-strategy-workspaces (677cd5b)
- Don't duplicate workspace tabs: find-or-create and highlight existing tab (8d423d1)
- Phase 4: reconciliation engine + strategy risk dashboard (01aedd1)
- Complete Phase 3: wire stage-embedded order id into the order path + per-tab P&L (9384ca7)
- Phase 3: workspace accounting engine, delete-when-empty, stage-embedded order id (7a1b0e2)
- Phase 2: dynamic strategy-workspace tabs + Smart Picks one-click create (42659d3)
- Phase 1: Strategy Workspaces foundation (additive, backward-compatible) (c542be1)
- Merge pull request #19 from haigopi/claude/design-strategy-workspaces (5be8b4b)
- Design doc: Smart Picks Driven Strategy Workspaces (phased plan) (e87d0e8)
- Merge pull request #18 from haigopi/claude/promote-dialog-and-network-usage (8e20216)
- Consistent loss-buy-levels toggle in promote dialog; show API usage on WiFi click (45feb3e)
- Agent Udpates (da4e920)
- Merge pull request #17 from haigopi/claude/widen-action-column (61df274)
- Widen strategy-grid Actions column so buttons clear the right edge (f4cc035)
- Merge pull request #16 from haigopi/claude/action-column-fit (e4e47c0)
- Stop action buttons being cut off and overlapping in the grid (81f2092)
- Merge pull request #15 from haigopi/claude/strategy-grid-action-column-fixes (964c94f)
- Fix strategy grid action column, symbol width, and Liquidate button clipping (03ef3e5)
- Merge pull request #14 from haigopi/claude/vibrant-rubin-rzvl88 (7a652cb)
- Theme Live Promotion dialog and make loss buy levels optional (a42c508)

