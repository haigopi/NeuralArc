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
 *   NA_&lt;MODE&gt;_&lt;STRATEGYCODE&gt;_&lt;SYMBOL&gt;_&lt;TIMESTAMP&gt;_&lt;SHORTUUID&gt;
 *   NA_PAPER_ORB_NVDA_20260613103015_A1B2
 *   NA_LIVE_VWAP_TSLA_20260613110522_B8C4
 * </pre>
 *
 * <ul>
 *   <li>{@code MODE} — {@code PAPER} | {@code LIVE}</li>
 *   <li>{@code STRATEGYCODE} — workspace code, or {@code ALL} when the strategy is unassigned</li>
 *   <li>{@code TIMESTAMP} — {@code yyyyMMddHHmmss} in UTC</li>
 *   <li>{@code SHORTUUID} — first 4 hex chars of a random UUID</li>
 * </ul>
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

    public static String build(StrategyMode mode, String strategyCode, String symbol, Instant when) {
        String modeSegment = mode == null ? StrategyMode.PAPER.name() : mode.name();
        String codeSegment = sanitize(strategyCode, UNASSIGNED_CODE);
        String symbolSegment = sanitize(symbol, "NA");
        String timestamp = TIMESTAMP.format(when == null ? Instant.now() : when);
        String shortUuid = UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        return String.join("_", PREFIX, modeSegment, codeSegment, symbolSegment, timestamp, shortUuid);
    }

    public static String build(StrategyMode mode, String strategyCode, String symbol) {
        return build(mode, strategyCode, symbol, Instant.now());
    }

    /** Parses a value produced by {@link #build}; returns empty for legacy/foreign ids. */
    public static Optional<Parsed> parse(String clientOrderId) {
        if (clientOrderId == null) {
            return Optional.empty();
        }
        String[] parts = clientOrderId.split("_");
        if (parts.length != 6 || !PREFIX.equals(parts[0])) {
            return Optional.empty();
        }
        StrategyMode mode;
        try {
            mode = StrategyMode.valueOf(parts[1]);
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
        return Optional.of(new Parsed(mode, parts[2], parts[3], parts[4], parts[5]));
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

    public record Parsed(StrategyMode mode, String strategyCode, String symbol, String timestamp, String shortUuid) {
    }
}
