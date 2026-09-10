package com.neuralarc.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.math.BigDecimal;
import java.util.List;

/**
 * Collects pasted stock lists and turns them into manual pending-review drafts.
 *
 * <p>Layout and validation only: the recognised text formats live in {@link StockImportTextParser}
 * and the guidance panel in {@link StockImportTipsPanel}.
 */
final class PortfolioStockImportDialog extends JDialog {
    private final JTextArea inputArea = new JTextArea(14, 52);
    private ImportSelection selection;

    private PortfolioStockImportDialog(Frame owner) {
        super(owner, "Import Stocks", true);
        setLayout(new BorderLayout(0, 0));

        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(StockImportTipsPanel.monospaceFont());
        inputArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(StockImportTipsPanel.BORDER, 1, true),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createEmptyBorder());
        inputScroll.setPreferredSize(new Dimension(560, 240));

        JPanel editor = new JPanel(new BorderLayout(0, 8));
        editor.setOpaque(false);
        editor.setBorder(BorderFactory.createEmptyBorder(4, 18, 0, 18));
        editor.add(StockImportTipsPanel.editorHeading(), BorderLayout.NORTH);
        editor.add(inputScroll, BorderLayout.CENTER);

        JPanel center = new JPanel(new BorderLayout(0, 0));
        center.setOpaque(false);
        center.add(new StockImportTipsPanel(inputArea::setText), BorderLayout.NORTH);
        center.add(editor, BorderLayout.CENTER);

        JButton importButton = new JButton("Import for Review");
        JButton cancelButton = new JButton("Cancel");
        importButton.addActionListener(e -> submit());
        cancelButton.addActionListener(e -> {
            selection = null;
            setVisible(false);
        });
        getRootPane().setDefaultButton(importButton);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        actions.setOpaque(false);
        actions.setBorder(BorderFactory.createEmptyBorder(14, 18, 16, 18));
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
                throw new IllegalArgumentException(
                        "Nothing recognisable to import. Paste an alert block, an analyst target list, "
                                + "or a plain ticker list such as \"1. Nebius ~ $NBIS\".");
            }
            selection = new ImportSelection(drafts);
            setVisible(false);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Import Stocks", JOptionPane.WARNING_MESSAGE);
        }
    }

    static List<ImportedStockDraft> parse(String rawText) {
        return StockImportTextParser.parse(rawText);
    }

    record ImportSelection(List<ImportedStockDraft> drafts) {
        ImportSelection {
            drafts = drafts == null ? List.of() : List.copyOf(drafts);
        }
    }

    /** How a draft's entry, stop and target are decided. */
    enum PricingMode {
        /** The pasted text carried the prices. */
        EXPLICIT,
        /** The paste was tickers only; the importer derives long-term levels from market data. */
        AUTO_LONG_TERM
    }

    record ImportedStockDraft(
            String symbol,
            BigDecimal recommendedEntry,
            BigDecimal stopLoss,
            List<BigDecimal> targets,
            PricingMode pricingMode
    ) {
        ImportedStockDraft {
            targets = targets == null ? List.of() : List.copyOf(targets);
            pricingMode = pricingMode == null ? PricingMode.EXPLICIT : pricingMode;
        }

        ImportedStockDraft(String symbol, BigDecimal recommendedEntry, BigDecimal stopLoss, List<BigDecimal> targets) {
            this(symbol, recommendedEntry, stopLoss, targets, PricingMode.EXPLICIT);
        }

        /** A ticker with no pasted prices; levels come from the long-term recommendation instead. */
        static ImportedStockDraft autoLongTerm(String symbol) {
            return new ImportedStockDraft(symbol, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), PricingMode.AUTO_LONG_TERM);
        }

        boolean autoPriced() {
            return pricingMode == PricingMode.AUTO_LONG_TERM;
        }
    }
}
