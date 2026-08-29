package com.neuralarc.ui;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

final class PortfolioStockImportDialog extends JDialog {
    private static final String EXAMPLE_TEXT = """
            @everyone type: DT/Swing
            Symbol: $MDB
            Entry: Entered @ 451
            Stop: Below 446 (Aggressive) - 15% from the entry.
            Targets: 466

            @everyone type: DT/Swing
            Symbol: $TTAN
            Entry: Entered @102.38
            Stop: Below 98 (Aggressive) - 15% from the entry.
            Targets: 109

            @everyone type: DT/Swing
            Symbol: $TEAM
            Entry: Entered @ 191.60
            Stop: Below 183 (Very strict stop) (Aggressive) - 15% from the entry.
            Targets: 204 - 218

            WALL STREET'S MOST BULLISH PRICE TARGETS FOR POPULAR STOCKS
            AI Utilities
            • $IREN $105 (+151%)
            • $NBIS $410 (+87%)
            • $HUT $273 (+238%)
            """;

    private final JTextArea inputArea = new JTextArea(18, 54);
    private ImportSelection selection;

    private PortfolioStockImportDialog(Frame owner) {
        super(owner, "Import Stocks", true);
        setLayout(new BorderLayout(12, 12));

        JLabel description = new JLabel("<html><body style='width:520px'>"
                + "<b>Paste stock alerts to create manual pending-review strategies.</b><br><br>"
                + "Accepted format example:<br><br><pre style='font-family:monospace'>"
                + EXAMPLE_TEXT
                + "</pre>"
                + "</body></html>");

        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new java.awt.Color(70, 76, 90), 1, true),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
        ));

        JPanel center = new JPanel(new BorderLayout(8, 8));
        center.add(description, BorderLayout.NORTH);
        center.add(new JScrollPane(inputArea), BorderLayout.CENTER);

        javax.swing.JButton importButton = new javax.swing.JButton("Import");
        javax.swing.JButton cancelButton = new javax.swing.JButton("Cancel");
        importButton.addActionListener(e -> submit());
        cancelButton.addActionListener(e -> {
            selection = null;
            setVisible(false);
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        actions.add(cancelButton);
        actions.add(importButton);

        add(center, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);
        pack();
        setLocationRelativeTo(owner);
    }

    static ImportSelection show(Component parent) {
        Frame owner = JOptionPane.getFrameForComponent(parent);
        PortfolioStockImportDialog dialog = new PortfolioStockImportDialog(owner);
        dialog.setVisible(true);
        return dialog.selection;
    }

    private void submit() {
        try {
            List<ImportedStockDraft> drafts = parse(inputArea.getText());
            if (drafts.isEmpty()) {
                throw new IllegalArgumentException("Paste at least one stock alert to import.");
            }
            selection = new ImportSelection(drafts);
            setVisible(false);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(
                    this,
                    ex.getMessage(),
                    "Import Stocks",
                    JOptionPane.WARNING_MESSAGE
            );
        }
    }

    static List<ImportedStockDraft> parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        List<ImportedStockDraft> bullishListDrafts = parseBullishTargetList(rawText);
        if (!bullishListDrafts.isEmpty()) {
            return bullishListDrafts;
        }
        List<ImportedStockDraft> drafts = new ArrayList<>();
        String[] blocks = rawText.trim().split("(\\r?\\n){2,}");
        for (String block : blocks) {
            ImportedStockDraft draft = parseBlock(block);
            if (draft != null) {
                drafts.add(draft);
            }
        }
        return drafts;
    }

    private static List<ImportedStockDraft> parseBullishTargetList(String rawText) {
        List<ImportedStockDraft> drafts = new ArrayList<>();
        for (String rawLine : rawText.split("\\r?\\n")) {
            String line = normalizeBullet(rawLine);
            if (line.isBlank() || !line.contains("$")) {
                continue;
            }
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "^\\$([A-Z.]+)\\s+\\$([0-9][0-9,]*(?:\\.\\d+)?)\\s*\\(\\+?([0-9]+(?:\\.\\d+)?)%\\)?",
                    java.util.regex.Pattern.CASE_INSENSITIVE
            ).matcher(line);
            if (!matcher.find()) {
                continue;
            }
            String symbol = matcher.group(1).toUpperCase();
            BigDecimal target = parseMoney(matcher.group(2));
            BigDecimal upsidePercent = new BigDecimal(matcher.group(3));
            if (target.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            BigDecimal entry = deriveEntryFromTargetUpside(target, upsidePercent);
            BigDecimal stop = deriveStopFromEntry(entry);
            drafts.add(new ImportedStockDraft(symbol, entry, stop, List.of(target)));
        }
        return drafts;
    }

    private static ImportedStockDraft parseBlock(String block) {
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
            if (normalized.startsWith("symbol:")) {
                symbol = parseSymbol(line.substring(line.indexOf(':') + 1));
            } else if (normalized.startsWith("entry:")) {
                entry = firstNumber(line.substring(line.indexOf(':') + 1));
            } else if (normalized.startsWith("stop:")) {
                stop = firstNumber(line.substring(line.indexOf(':') + 1));
            } else if (normalized.startsWith("targets:")) {
                targets = allNumbers(line.substring(line.indexOf(':') + 1));
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
        return new ImportedStockDraft(symbol, entry, stop, targets);
    }

    private static String parseSymbol(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        normalized = normalized.replace("$", "");
        int spaceIndex = normalized.indexOf(' ');
        return spaceIndex >= 0 ? normalized.substring(0, spaceIndex).trim() : normalized;
    }

    private static String normalizeBullet(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("•", "")
                .replace("⁠", "")
                .trim();
    }

    private static BigDecimal firstNumber(String value) {
        List<BigDecimal> numbers = allNumbers(value);
        return numbers.isEmpty() ? BigDecimal.ZERO : numbers.getFirst();
    }

    private static BigDecimal parseMoney(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        return new BigDecimal(value.replace(",", "").trim());
    }

    private static BigDecimal deriveEntryFromTargetUpside(BigDecimal target, BigDecimal upsidePercent) {
        if (target == null || target.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        if (upsidePercent == null || upsidePercent.signum() <= 0) {
            return target;
        }
        BigDecimal multiplier = BigDecimal.ONE.add(upsidePercent.divide(new BigDecimal("100"), 8, java.math.RoundingMode.HALF_UP));
        return com.neuralarc.util.Monetary.round(target.divide(multiplier, 8, java.math.RoundingMode.HALF_UP));
    }

    private static BigDecimal deriveStopFromEntry(BigDecimal entry) {
        if (entry == null || entry.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return com.neuralarc.util.Monetary.round(entry.multiply(new BigDecimal("0.85")));
    }

    private static List<BigDecimal> allNumbers(String value) {
        List<BigDecimal> numbers = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return numbers;
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(value);
        while (matcher.find()) {
            numbers.add(new BigDecimal(matcher.group()));
        }
        return numbers;
    }

    record ImportSelection(List<ImportedStockDraft> drafts) {
        ImportSelection {
            drafts = drafts == null ? List.of() : List.copyOf(drafts);
        }
    }

    record ImportedStockDraft(String symbol, BigDecimal recommendedEntry, BigDecimal stopLoss, List<BigDecimal> targets) {
        ImportedStockDraft {
            targets = targets == null ? List.of() : List.copyOf(targets);
        }
    }
}
