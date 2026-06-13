package com.neuralarc.util;

import com.neuralarc.model.StrategyMode;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Structured Alpaca {@code client_order_id} encoding strategy-workspace ownership for
 * reconciliation, auditing, and historical analysis.
 *
 * <pre>
 *   NA_&lt;MODE&gt;_&lt;STRATEGYCODE&gt;_&lt;SYMBOL&gt;_&lt;STAGE&gt;_&lt;TIMESTAMP&gt;_&lt;SHORTUUID&gt;
 *   NA_PAPER_ORB_NVDA_BASE_BUY_20260613103015_A1B2
 *   NA_LIVE_VWAP_TSLA_TARGET_SELL_20260613110522_B8C4
 * </pre>
 *
 * <ul>
 *   <li>{@code MODE} — {@code PAPER} | {@code LIVE}</li>
 *   <li>{@code STRATEGYCODE} — workspace code, or {@code ALL} when the strategy is unassigned</li>
 *   <li>{@code STAGE} — the order stage (e.g. {@code BASE_BUY}, {@code TARGET_SELL})</li>
 *   <li>{@code TIMESTAMP} — {@code yyyyMMddHHmmss} in UTC</li>
 *   <li>{@code SHORTUUID} — first 4 hex chars of a random UUID</li>
 * </ul>
 *
 * <p>Stage names may themselves contain underscores ({@code BASE_BUY}), so the structure is parsed
 * from the fixed-position MODE/SYMBOL/TIMESTAMP/UUID anchors rather than a naive split.
 *
 * <p>Alpaca limits {@code client_order_id} to 128 characters; {@link #build} sanitizes and
 * truncates the symbol/code segments so the result always fits.
 */
public final class ClientOrderId {
    public static final String PREFIX = "NA";
    public static final String UNASSIGNED_CODE = "ALL";
    private static final int MAX_SEGMENT = 16;
    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private ClientOrderId() {
    }

    public static String build(StrategyMode mode, String strategyCode, String symbol, String stage, Instant when) {
        String modeSegment = mode == null ? StrategyMode.PAPER.name() : mode.name();
        String codeSegment = sanitize(strategyCode, UNASSIGNED_CODE);
        String symbolSegment = sanitize(symbol, "NA");
        String stageSegment = sanitizeStage(stage);
        String timestamp = TIMESTAMP.format(when == null ? Instant.now() : when);
        String shortUuid = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        return String.join("_", PREFIX, modeSegment, codeSegment, symbolSegment, stageSegment, timestamp, shortUuid);
    }

    public static String build(StrategyMode mode, String strategyCode, String symbol, String stage) {
        return build(mode, strategyCode, symbol, stage, Instant.now());
    }

    /**
     * Parses a value produced by {@link #build}; returns empty for legacy/foreign ids.
     *
     * <p>The stage segment may itself contain underscores (e.g. {@code BASE_BUY}), so the id is
     * parsed from its fixed anchors — {@code NA}/mode/code/symbol at the front and
     * timestamp/uuid at the back — with everything between treated as the stage.
     */
    public static Optional<Parsed> parse(String clientOrderId) {
        if (clientOrderId == null) {
            return Optional.empty();
        }
        String[] parts = clientOrderId.split("_");
        if (parts.length < 7 || !PREFIX.equals(parts[0])) {
            return Optional.empty();
        }
        StrategyMode mode;
        try {
            mode = StrategyMode.valueOf(parts[1]);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        String code = parts[2];
        String symbol = parts[3];
        String timestamp = parts[parts.length - 2];
        String shortUuid = parts[parts.length - 1];
        StringBuilder stage = new StringBuilder();
        for (int i = 4; i < parts.length - 2; i++) {
            if (stage.length() > 0) {
                stage.append('_');
            }
            stage.append(parts[i]);
        }
        return Optional.of(new Parsed(mode, code, symbol, stage.toString(), timestamp, shortUuid));
    }

    private static String sanitize(String raw, String fallback) {
        if (raw == null) {
            return fallback;
        }
        StringBuilder builder = new StringBuilder();
        for (char c : raw.toUpperCase().toCharArray()) {
            if (Character.isLetterOrDigit(c) && builder.length() < MAX_SEGMENT) {
                builder.append(c);
            }
        }
        return builder.isEmpty() ? fallback : builder.toString();
    }

    /** Keeps {@code A-Z}, {@code 0-9} and underscores so multi-word stage names survive. */
    private static String sanitizeStage(String raw) {
        if (raw == null) {
            return "NA";
        }
        StringBuilder builder = new StringBuilder();
        for (char c : raw.toUpperCase().toCharArray()) {
            if ((Character.isLetterOrDigit(c) || c == '_') && builder.length() < MAX_SEGMENT) {
                builder.append(c);
            }
        }
        return builder.isEmpty() ? "NA" : builder.toString();
    }

    public record Parsed(StrategyMode mode, String strategyCode, String symbol, String stage, String timestamp, String shortUuid) {
    }
}
