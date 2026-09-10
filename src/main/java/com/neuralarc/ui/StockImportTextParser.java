package com.neuralarc.ui;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses pasted stock lists into {@link PortfolioStockImportDialog.ImportedStockDraft}s.
 *
 * <p>Three shapes are recognised, tried in order of how specific they are:
 * <ol>
 *   <li><b>Alert blocks</b> — {@code Symbol:} / {@code Entry:} / {@code Stop:} / {@code Targets:}.</li>
 *   <li><b>Analyst target lists</b> — {@code • $NBIS $410 (+87%)}, where the entry is back-solved
 *       from the target and its quoted upside.</li>
 *   <li><b>Ticker lists</b> — {@code 1. Nebius ~ $NBIS}, which carry no prices at all. These are
 *       marked {@link PortfolioStockImportDialog.PricingMode#AUTO_LONG_TERM} so the importer derives
 *       the entry, stop and target from live market data instead of inventing them here.</li>
 * </ol>
 */
final class StockImportTextParser {
    /** A ticker after an optional {@code $}: 1-5 letters, with an optional class suffix (BRK.B). */
    private static final Pattern TICKER = Pattern.compile("\\$([A-Za-z]{1,5}(?:\\.[A-Za-z]{1,2})?)\\b");
    private static final Pattern ANALYST_TARGET = Pattern.compile(
            "^\\$([A-Z.]+)\\s+\\$([0-9][0-9,]*(?:\\.\\d+)?)\\s*\\(\\+?([0-9]+(?:\\.\\d+)?)%\\)?",
            Pattern.CASE_INSENSITIVE);
    /** A leading list marker such as "1.", "12)" or "-" that is numbering, not data. */
    private static final Pattern LIST_MARKER = Pattern.compile("^\\s*(?:\\d{1,3}\\s*[.)\\]]|[-*•>])\\s*");
    private static final Pattern ALERT_BLOCK_KEYWORD = Pattern.compile("(?im)^\\s*symbol\\s*:");
    private static final Pattern ANY_NUMBER = Pattern.compile("\\d");
    private static final Pattern DECIMAL = Pattern.compile("\\d+(?:\\.\\d+)?");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ANALYST_STOP_FACTOR = new BigDecimal("0.85");

    private StockImportTextParser() {
    }

    static List<PortfolioStockImportDialog.ImportedStockDraft> parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        List<PortfolioStockImportDialog.ImportedStockDraft> analystTargets = parseAnalystTargetList(rawText);
        if (!analystTargets.isEmpty()) {
            return analystTargets;
        }
        // Only consider a bare ticker list when the text is not an alert block: an alert's
        // "Symbol: $MDB" line is itself a ticker with no price and would otherwise match.
        if (!ALERT_BLOCK_KEYWORD.matcher(rawText).find()) {
            List<PortfolioStockImportDialog.ImportedStockDraft> tickers = parseTickerList(rawText);
            if (!tickers.isEmpty()) {
                return tickers;
            }
        }
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = new ArrayList<>();
        for (String block : rawText.trim().split("(\\r?\\n){2,}")) {
            PortfolioStockImportDialog.ImportedStockDraft draft = parseAlertBlock(block);
            if (draft != null) {
                drafts.add(draft);
            }
        }
        return drafts;
    }

    /**
     * Reads a plain watchlist — {@code 1. Nebius ~ $NBIS}, {@code - $PLTR}, or just {@code $ORCL}.
     * A line only qualifies when nothing price-like remains after the list numbering is removed, so
     * a line that does carry prices falls through to a format that can actually use them. Repeated
     * tickers collapse to their first appearance.
     */
    static List<PortfolioStockImportDialog.ImportedStockDraft> parseTickerList(String rawText) {
        Set<String> symbols = new LinkedHashSet<>();
        for (String rawLine : rawText.split("\\r?\\n")) {
            String line = LIST_MARKER.matcher(normalize(rawLine)).replaceFirst("");
            if (line.isBlank()) {
                continue;
            }
            Matcher ticker = TICKER.matcher(line);
            if (!ticker.find()) {
                continue;
            }
            // Anything numeric left over means this line is priced; let another parser handle it.
            if (ANY_NUMBER.matcher(TICKER.matcher(line).replaceAll("")).find()) {
                continue;
            }
            symbols.add(ticker.group(1).toUpperCase());
        }
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = new ArrayList<>();
        for (String symbol : symbols) {
            drafts.add(PortfolioStockImportDialog.ImportedStockDraft.autoLongTerm(symbol));
        }
        return drafts;
    }

    private static List<PortfolioStockImportDialog.ImportedStockDraft> parseAnalystTargetList(String rawText) {
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = new ArrayList<>();
        for (String rawLine : rawText.split("\\r?\\n")) {
            String line = normalize(rawLine);
            if (line.isBlank() || !line.contains("$")) {
                continue;
            }
            Matcher matcher = ANALYST_TARGET.matcher(line);
            if (!matcher.find()) {
                continue;
            }
            BigDecimal target = parseMoney(matcher.group(2));
            if (target.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal entry = entryFromTargetUpside(target, new BigDecimal(matcher.group(3)));
            drafts.add(new PortfolioStockImportDialog.ImportedStockDraft(
                    matcher.group(1).toUpperCase(), entry, stopFromEntry(entry), List.of(target)));
        }
        return drafts;
    }

    private static PortfolioStockImportDialog.ImportedStockDraft parseAlertBlock(String block) {
        String symbol = "";
        BigDecimal entry = BigDecimal.ZERO;
        BigDecimal stop = BigDecimal.ZERO;
        List<BigDecimal> targets = new ArrayList<>();
        for (String rawLine : block.split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank()) {
                continue;
            }
            String normalized = line.toLowerCase();
            String value = line.substring(line.indexOf(':') + 1);
            if (normalized.startsWith("symbol:")) {
                symbol = parseSymbol(value);
            } else if (normalized.startsWith("entry:")) {
                entry = firstNumber(value);
            } else if (normalized.startsWith("stop:")) {
                stop = firstNumber(value);
            } else if (normalized.startsWith("targets:")) {
                targets = allNumbers(value);
            }
        }
        if (symbol.isBlank()) {
            throw new IllegalArgumentException("Each import block must include a Symbol line.");
        }
        if (entry.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Missing valid entry price for " + symbol + ".");
        }
        if (stop.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Missing valid stop price for " + symbol + ".");
        }
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("Missing at least one target price for " + symbol + ".");
        }
        return new PortfolioStockImportDialog.ImportedStockDraft(symbol, entry, stop, targets);
    }

    /**
     * Strips bullets and the invisible formatting characters that survive a copy/paste out of chat
     * apps and messaging clients — zero-width joiners, word joiners and non-breaking spaces — which
     * otherwise sit between the list number and the ticker and defeat plain matching.
     */
    private static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replaceAll("[\\p{Cf}\\u200B]", "")
                .replace("\u2022", " ")
                .replace("\u00B7", " ")
                .trim();
    }

    private static String parseSymbol(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase().replace("$", "");
        int spaceIndex = normalized.indexOf(' ');
        return spaceIndex >= 0 ? normalized.substring(0, spaceIndex).trim() : normalized;
    }

    private static BigDecimal entryFromTargetUpside(BigDecimal target, BigDecimal upsidePercent) {
        if (target == null || target.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (upsidePercent == null || upsidePercent.signum() <= 0) {
            return target;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(upsidePercent.divide(HUNDRED, 8, RoundingMode.HALF_UP));
        return Monetary.round(target.divide(multiplier, 8, RoundingMode.HALF_UP));
    }

    private static BigDecimal stopFromEntry(BigDecimal entry) {
        if (entry == null || entry.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return Monetary.round(entry.multiply(ANALYST_STOP_FACTOR));
    }

    private static BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.replace(",", "").trim());
    }

    private static BigDecimal firstNumber(String value) {
        List<BigDecimal> numbers = allNumbers(value);
        return numbers.isEmpty() ? BigDecimal.ZERO : numbers.getFirst();
    }

    private static List<BigDecimal> allNumbers(String value) {
        List<BigDecimal> numbers = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return numbers;
        }
        Matcher matcher = DECIMAL.matcher(value);
        while (matcher.find()) {
            numbers.add(new BigDecimal(matcher.group()));
        }
        return numbers;
    }
}
