package com.neuralarc.agent;

import org.json.JSONObject;

import java.util.List;
import java.util.Locale;

/**
 * Typed, validated access to whatever JSON the model produced for a tool call.
 *
 * <p>Models send strings for numbers, lowercase tickers, out-of-range look-backs and missing fields.
 * Every accessor here either returns a value the service layer can safely take, or throws a
 * {@link ToolArgumentException} explaining the accepted shape. Numeric bounds clamp nothing silently
 * — a model that asks for ten years of bars is told the limit rather than quietly given sixty days.
 */
public final class ToolArguments {
    /** Long enough for every US equity ticker, short enough to reject a sentence. */
    private static final int MAX_SYMBOL_LENGTH = 8;

    private final JSONObject raw;

    public ToolArguments(JSONObject raw) {
        this.raw = raw == null ? new JSONObject() : raw;
    }

    /** A broker-ready uppercase ticker. Symbols are normalized uppercase everywhere in this app. */
    public String symbol(String name) throws ToolArgumentException {
        String value = requiredString(name).toUpperCase(Locale.ROOT);
        if (value.length() > MAX_SYMBOL_LENGTH || !value.matches("[A-Z][A-Z.]*")) {
            throw new ToolArgumentException("'" + name + "' must be a stock ticker such as AAPL, not \"" + value + "\".");
        }
        return value;
    }

    public String requiredString(String name) throws ToolArgumentException {
        Object value = raw.opt(name);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ToolArgumentException("'" + name + "' is required.");
        }
        return String.valueOf(value).trim();
    }

    public String choice(String name, List<String> allowed, String fallback) throws ToolArgumentException {
        Object value = raw.opt(name);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        String candidate = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(candidate)) {
            throw new ToolArgumentException("'" + name + "' must be one of " + String.join(", ", allowed) + ".");
        }
        return candidate;
    }

    /** An integer within {@code [min, max]}; absent means {@code fallback}. Accepts "30" as 30. */
    public int integer(String name, int min, int max, int fallback) throws ToolArgumentException {
        Object value = raw.opt(name);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        int parsed;
        try {
            parsed = value instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new ToolArgumentException("'" + name + "' must be a whole number between " + min + " and " + max + ".");
        }
        if (parsed < min || parsed > max) {
            throw new ToolArgumentException("'" + name + "' must be between " + min + " and " + max + ", was " + parsed + ".");
        }
        return parsed;
    }

    public boolean bool(String name, boolean fallback) {
        Object value = raw.opt(name);
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.equalsIgnoreCase("true") || text.equalsIgnoreCase("yes") ? true
                : text.equalsIgnoreCase("false") || text.equalsIgnoreCase("no") ? false : fallback;
    }

    public JSONObject raw() {
        return raw;
    }
}
