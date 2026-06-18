# NeuralArc Linux Release 1.5.5

## Artifact
- File: NeuralArc-1.5.5.deb
- Path: artifacts/linux/NeuralArc-1.5.5.deb

## Install
1. Install the DEB package (for Debian/Ubuntu-based distributions).
2. Launch NeuralArc from applications menu.

## Verify checksum (optional)
zsh:
  cd /Users/gopimac/Documents/Workspace/NeuralArc
  sha256sum artifacts/linux/NeuralArc-1.5.5.deb

## Changes
- Merge pull request #50 from haigopi/claude/zen-ptolemy-9e2n8c (7550094)
- Implement ORB scheduling and fix Gap Rocket empty discovery (6a914b0)
- Merge pull request #49 from haigopi/claude/zen-ptolemy-9e2n8c (a86cfa2)
- Fix ORB strategy: enforce latestEntryTimeEt, add codec, harden config validation (f070483)
- Relax Gap Rocket filters and fix hardcoded SPY/QQQ green flags (038dd39)
- Merge pull request #48 from haigopi/claude/liquidate-pnl-and-logs (1e4764e)
- Make broker API JSON logging a Settings toggle (default OFF) (99869b2)
- Fix Liquidate Portfolio P&L 0-vs-status-bar; stop logging request/response JSON (264ede8)
- Merge pull request #47 from haigopi/codex/fix-liquidate-portfolio-persistence-and-ui-issues (306f052)
- Persist liquidate portfolio monitoring state (4816f19)
- Merge pull request #45 from haigopi/codex/implement-orb-engine-smart-picks-plan.md (e7d28ae)
- Merge pull request #46 from haigopi/claude/pnl-single-source-of-truth (a27ab7b)
- Rainbow capture P&L; show it only when enabled and per strategy tab (753870f)
- feat: wire ORB engine workspace analysis (11ce06a)
- Capture P&L matches tab/context; rainbow header P&L text (f7950e6)
- Centralize P&L on a single source of truth; fix status-bar vs All Stocks mismatch (bcf1207)
- feat: add ORB range analysis foundation (035baa0)
- Merge pull request #44 from haigopi/codex/implement-orb-engine-from-smart-picks (25635de)
- docs: add ORB Engine implementation plan (9edfe78)
- Merge pull request #41 from haigopi/claude/alpaca-news-prefilter (7213f28)
- Merge pull request #43 from haigopi/codex/fix-pl-mismatch-issue-and-refactor-calculations (d731e73)
- Fix scoped portfolio PnL totals (214ba08)
- Merge pull request #42 from haigopi/claude/limit-buy-status-and-cancel (ad4765e)
- Show held position alongside a separate pending limit-buy (req 2) (d7fe587)
- Fix pending limit-buy status on restart; add right-click cancel (c69c541)
- Add Alpaca News pre-filter before AI catalyst analysis (b21f249)
- Merge pull request #40 from haigopi/codex/fix-strategy-independence-in-live-and-paper-modes (271b504)
- Isolate Gap Rocket schedules by workspace mode (df6201e)
- Minor Udopates (6ba3dc8)
- Merge pull request #39 from haigopi/codex/fix-liquidate-portfolio-button-state-issue (b833989)
- Scope liquidate portfolio UI state by mode and tab (92eecc1)
- Merge pull request #37 from haigopi/claude/fix-collapsible-sections (9b6c0d6)
- Merge pull request #38 from haigopi/codex/fix-liquidate-portfolio-functionality-issues (e363172)
- Fix mode-scoped portfolio liquidation (a81dc42)
- Fix Logs section expand and cross-section toggle interference (ed4cc22)

