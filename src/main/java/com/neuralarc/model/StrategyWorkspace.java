package com.neuralarc.model;

import java.time.Instant;

/**
 * A higher-level grouping ("book") of per-symbol {@link Strategy} rows, e.g. "ORB Engine" or
 * "VWAP Desk".
 *
 * <p>This is the strategy-centric concept from the Strategy Workspaces feature. It deliberately
 * does <em>not</em> replace {@link Strategy} (which remains a per-symbol automated trade plan):
 * a workspace simply groups existing strategies, so strategy-level positions and P&amp;L are
 * aggregated from the member strategies rather than tracked in a new ledger.
 *
 * <p>Workspaces are mode-scoped ({@link StrategyMode#PAPER} / {@link StrategyMode#LIVE}); Paper and
 * Live data never mix. The short {@code code} is used inside Alpaca {@code client_order_id} values
 * (see {@code com.neuralarc.util.ClientOrderId}) for reconciliation and auditing.
 *
 * <p>Instances are immutable; edits (rename / archive) produce a new instance via the {@code with*}
 * helpers so repositories can cache safely.
 */
public record StrategyWorkspace(
        String id,
        String name,
        String code,
        StrategyMode mode,
        boolean archived,
        Instant createdAt,
        Instant updatedAt
) {
    /** Reserved code that represents the unassigned "All Stocks" view; never a real workspace. */
    public static final String ALL_STOCKS_CODE = "ALL";

    public StrategyWorkspace {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Workspace id must not be blank");
        }
        name = name == null ? "" : name.trim();
        code = normalizeCode(code);
        if (mode == null) {
            throw new IllegalArgumentException("Workspace mode must not be null");
        }
        Instant now = Instant.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = updatedAt == null ? createdAt : updatedAt;
    }

    public StrategyWorkspace withName(String newName) {
        return new StrategyWorkspace(id, newName, code, mode, archived, createdAt, Instant.now());
    }

    public StrategyWorkspace withArchived(boolean newArchived) {
        return new StrategyWorkspace(id, name, code, mode, newArchived, createdAt, Instant.now());
    }

    /**
     * Normalizes a free-text label into a short, uppercase, alphanumeric workspace code
     * (max 8 chars). Falls back to {@code "STRAT"} when nothing usable remains.
     */
    public static String normalizeCode(String raw) {
        if (raw == null) {
            return "STRAT";
        }
        StringBuilder builder = new StringBuilder();
        for (char c : raw.toUpperCase().toCharArray()) {
            if (Character.isLetterOrDigit(c) && builder.length() < 8) {
                builder.append(c);
            }
        }
        return builder.isEmpty() ? "STRAT" : builder.toString();
    }
}
