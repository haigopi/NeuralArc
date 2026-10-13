package com.neuralarc.ui;

import com.neuralarc.model.SellSubmissionType;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.Monetary;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Review-and-sell dialog for selling every position past a profit or loss cut-off.
 *
 * <p>Laid out in the order an operator decides: the rule (a single sentence they complete), the
 * positions it picks (all ticked, each one a checkbox away from being kept), and how to sell. The
 * grid re-filters as the rule is edited, so the operator sees the effect of every change before
 * anything is sent, and the primary button states how many sells go out.
 */
final class ThresholdSellDialog {
    private static final Color MUTED = new Color(120, 124, 135);
    private static final Color GAIN = ThemeColors.color("NeuralArc.pnlPositive", new Color(108, 203, 129));
    private static final Color LOSS = ThemeColors.color("NeuralArc.pnlNegative", new Color(240, 113, 120));
    private static final Font UI_FONT = FontLoader.ui(Font.PLAIN, 11f);
    private static final Font UI_BOLD_FONT = FontLoader.ui(Font.BOLD, 11f);
    private static final Font TITLE_FONT = FontLoader.ui(Font.BOLD, 14f);
    private static final Font DESCRIPTION_FONT = FontLoader.ui(Font.PLAIN, 10f);
    private static final int VISIBLE_ROWS = 9;
    private static final int PROSE_WIDTH = 640;

    private final JComboBox<String> side = new JComboBox<>(new String[] {"profit", "loss", "profit or loss"});
    private final JComboBox<String> comparison = new JComboBox<>(new String[] {"more than", "less than"});
    private final JSpinner valueSpinner = new JSpinner(new SpinnerNumberModel(5.0d, 0.0d, 1_000_000.0d, 0.5d));
    private final JComboBox<String> unit = new JComboBox<>(new String[] {"%", "$"});
    private final JCheckBox valueCapEnabled = new JCheckBox("Only stocks with");
    private final JComboBox<String> valueBasis = new JComboBox<>(new String[] {"position value", "share price"});
    private final JSpinner valueCapSpinner = new JSpinner(new SpinnerNumberModel(10.0d, 0.0d, 1_000_000.0d, 1.0d));
    private final JRadioButton limitOrder = new JRadioButton("Patient – limit sell near the last price", true);
    private final JRadioButton marketOrder = new JRadioButton("Immediate – market sell now");

    private final ThresholdSellTableModel model;
    private final JLabel ruleSummary = label("", UI_FONT, MUTED);
    private final JLabel selectionSummary = label("", UI_BOLD_FONT, null);
    private final JLabel orderSummary = label("", UI_FONT, MUTED);
    private final JButton submitButton = new JButton();
    private final JPanel root;
    private boolean confirmed;

    /**
     * @param candidates the open positions that can be sold, whatever the rule.
     * @param scopeLabel where they come from, e.g. "All Stocks · Live".
     */
    ThresholdSellDialog(List<ThresholdSellCandidates.Candidate> candidates, String scopeLabel) {
        ButtonGroup orderType = new ButtonGroup();
        orderType.add(limitOrder);
        orderType.add(marketOrder);
        valueSpinner.setPreferredSize(new Dimension(84, valueSpinner.getPreferredSize().height));
        valueCapSpinner.setPreferredSize(new Dimension(84, valueCapSpinner.getPreferredSize().height));
        valueBasis.setEnabled(false);
        valueCapSpinner.setEnabled(false);

        model = new ThresholdSellTableModel(candidates, currentRule());
        root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(14, 16, 12, 16));
        root.add(header(scopeLabel), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(ruleSection(), BorderLayout.NORTH);
        body.add(positionsSection(), BorderLayout.CENTER);
        body.add(executionSection(), BorderLayout.SOUTH);
        root.add(body, BorderLayout.CENTER);
        root.add(footer(), BorderLayout.SOUTH);

        side.addActionListener(e -> ruleChanged());
        comparison.addActionListener(e -> ruleChanged());
        unit.addActionListener(e -> ruleChanged());
        valueSpinner.addChangeListener(e -> ruleChanged());
        valueCapEnabled.addActionListener(e -> {
            valueBasis.setEnabled(valueCapEnabled.isSelected());
            valueCapSpinner.setEnabled(valueCapEnabled.isSelected());
            ruleChanged();
        });
        valueBasis.addActionListener(e -> ruleChanged());
        valueCapSpinner.addChangeListener(e -> ruleChanged());
        limitOrder.addActionListener(e -> refreshSummary());
        marketOrder.addActionListener(e -> refreshSummary());
        model.setOnChange(this::refreshSummary);
        refreshSummary();
    }

    static Optional<ThresholdSellSelection> show(Component parent, List<ThresholdSellCandidates.Candidate> candidates, String scopeLabel) {
        ThresholdSellDialog content = new ThresholdSellDialog(candidates, scopeLabel);
        Frame owner = JOptionPane.getFrameForComponent(parent);
        JDialog dialog = new JDialog(owner, "Sell by Profit / Loss Threshold", true);
        dialog.setContentPane(content.root);
        content.submitButton.addActionListener(e -> {
            content.confirmed = true;
            dialog.dispose();
        });
        dialog.getRootPane().setDefaultButton(content.submitButton);
        dialog.pack();
        dialog.setMinimumSize(dialog.getSize());
        DialogSizing.growTall(dialog, dialog.getHeight());
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return content.confirmed ? Optional.of(content.selection()) : Optional.empty();
    }

    JPanel root() {
        return root;
    }

    ThresholdSellTableModel model() {
        return model;
    }

    ThresholdSellSelection selection() {
        return new ThresholdSellSelection(
                currentRule(),
                marketOrder.isSelected() ? SellSubmissionType.MARKET : SellSubmissionType.LIMIT,
                model.selectedIds());
    }

    boolean submitEnabled() {
        return submitButton.isEnabled();
    }

    String submitText() {
        return submitButton.getText();
    }

    String ruleSummaryText() {
        return ruleSummary.getText();
    }

    void chooseRule(String sideName, String comparisonName, double value, String unitName) {
        side.setSelectedItem(sideName);
        comparison.setSelectedItem(comparisonName);
        unit.setSelectedItem(unitName);
        valueSpinner.setValue(value);
    }

    /** Turns on the "value under $N" cap; {@code basisName} is "position value" or "share price". */
    void chooseValueCap(String basisName, double amount) {
        valueCapEnabled.setSelected(true);
        valueBasis.setEnabled(true);
        valueCapSpinner.setEnabled(true);
        valueBasis.setSelectedItem(basisName);
        valueCapSpinner.setValue(amount);
        ruleChanged();
    }

    void chooseMarketOrder() {
        marketOrder.doClick();
    }

    private JComponent header(String scopeLabel) {
        JPanel panel = new JPanel(new BorderLayout(0, 3));
        panel.setOpaque(false);
        panel.add(label("Sell by profit / loss threshold", TITLE_FONT, null), BorderLayout.NORTH);
        panel.add(label("<html><body style='width:" + PROSE_WIDTH + "px'>Sells every open position past the cut-off you set, "
                + "one sell order per ticked row"
                + (scopeLabel == null || scopeLabel.isBlank() ? "" : ", from <b>" + escape(scopeLabel) + "</b>")
                + ". Positions that already have a sell working are not listed.</body></html>",
                DESCRIPTION_FONT, MUTED), BorderLayout.CENTER);
        return panel;
    }

    private JComponent ruleSection() {
        JPanel section = section("1  ·  Rule");
        GridBagConstraints gbc = gbc(0);
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        JPanel sentence = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        sentence.setOpaque(false);
        sentence.add(label("Sell positions with", UI_FONT, null));
        for (JComboBox<String> box : List.of(side, comparison, unit)) {
            box.setFont(UI_FONT);
        }
        sentence.add(side);
        sentence.add(comparison);
        sentence.add(valueSpinner);
        sentence.add(unit);
        section.add(sentence, gbc);
        gbc.gridy++;
        JPanel cap = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        cap.setOpaque(false);
        valueCapEnabled.setFont(UI_FONT);
        valueBasis.setFont(UI_FONT);
        cap.add(valueCapEnabled);
        cap.add(valueBasis);
        cap.add(label("under $", UI_FONT, null));
        cap.add(valueCapSpinner);
        section.add(cap, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(4, 6, 0, 0);
        section.add(ruleSummary, gbc);
        gbc.gridy++;
        section.add(description("% is the gain or loss against what you paid; $ is the dollar P&L on the whole position. "
                + "Use \"profit or loss\" with 0 to pick every position that is not at break-even. "
                + "Tick \"Only stocks with\" to also require a small holding, e.g. position value under $10. "
                + "The number is a size, so \"loss more than 5%\" means down by over 5%, and \"profit less than 5%\" means up, but by under 5%.",
                PROSE_WIDTH), gbc);
        return section;
    }

    private JComponent positionsSection() {
        JPanel section = section("2  ·  Positions");
        section.setLayout(new BorderLayout(0, 6));

        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.add(selectionSummary, BorderLayout.WEST);
        JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        links.setOpaque(false);
        links.add(smallButton("Select all", model::selectAll));
        links.add(smallButton("Clear", model::clearSelection));
        toolbar.add(links, BorderLayout.EAST);
        section.add(toolbar, BorderLayout.NORTH);

        JTable table = new JTable(model);
        table.setFont(UI_FONT);
        table.getTableHeader().setFont(UI_BOLD_FONT);
        table.setRowHeight(22);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        table.getTableHeader().setReorderingAllowed(false);
        configureColumns(table);

        JScrollPane scroll = new JScrollPane(table);
        scroll.setColumnHeaderView(table.getTableHeader());
        scroll.setPreferredSize(new Dimension(0, table.getRowHeight() * VISIBLE_ROWS + 26));
        section.add(scroll, BorderLayout.CENTER);
        section.add(description("Every position the rule picks starts ticked. Untick any you want to keep; "
                + "your choice is remembered if you change the rule.", PROSE_WIDTH), BorderLayout.SOUTH);
        return section;
    }

    private void configureColumns(JTable table) {
        RowRenderer renderer = new RowRenderer(model);
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            if (i == ThresholdSellTableModel.INCLUDE) {
                column.setMaxWidth(28);
                column.setMinWidth(28);
                continue;
            }
            column.setCellRenderer(renderer);
            column.setPreferredWidth(switch (model.getColumnName(i)) {
                case "Symbol", "Shares" -> 60;
                case "Workspace" -> 110;
                case "P&L" -> 150;
                default -> 84;
            });
        }
    }

    private JComponent executionSection() {
        JPanel section = section("3  ·  How to sell");
        GridBagConstraints gbc = gbc(0);
        section.add(limitOrder, gbc);
        gbc.gridy++;
        section.add(marketOrder, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(6, 0, 0, 0);
        section.add(description("Patient sells wait for the price and may not fill. Immediate sells fill now at whatever the "
                + "market pays, so the final price can differ from the last price shown.", PROSE_WIDTH), gbc);
        return section;
    }

    private JComponent footer() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setOpaque(false);
        panel.add(orderSummary, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> {
            confirmed = false;
            java.awt.Window window = javax.swing.SwingUtilities.getWindowAncestor(root);
            if (window != null) {
                window.dispose();
            }
        });
        buttons.add(cancel);
        buttons.add(submitButton);
        panel.add(buttons, BorderLayout.EAST);
        return panel;
    }

    private void ruleChanged() {
        if (model != null) {
            model.setRule(currentRule());
        }
    }

    private void refreshSummary() {
        int matched = model.matchCount();
        int selected = model.selectedCount();
        ThresholdSellRule rule = currentRule();
        ruleSummary.setText(matched + " of " + model.totalCount() + " open positions have " + rule.describe() + ".");
        selectionSummary.setText(selected + " of " + matched + " selected");
        boolean market = marketOrder.isSelected();
        orderSummary.setText(selected == 0
                ? (matched == 0 ? "No position matches this rule." : "Tick at least one position to continue.")
                : selected + (market ? " market sell" : " limit sell") + (selected == 1 ? "" : "s")
                        + "  ·  est. proceeds $" + Monetary.round(model.estimatedProceeds()).toPlainString()
                        + "  ·  locks in " + signed(model.estimatedPnl()));
        submitButton.setEnabled(selected > 0);
        submitButton.setText(selected == 0 ? "Sell" : "Sell " + selected + " Position" + (selected == 1 ? "" : "s"));
    }

    private ThresholdSellRule currentRule() {
        return new ThresholdSellRule(
                switch (side.getSelectedIndex()) {
                    case 1 -> ThresholdSellRule.Side.LOSS;
                    case 2 -> ThresholdSellRule.Side.ANY;
                    default -> ThresholdSellRule.Side.PROFIT;
                },
                comparison.getSelectedIndex() == 1
                        ? ThresholdSellRule.Comparison.LESS_THAN
                        : ThresholdSellRule.Comparison.MORE_THAN,
                unit.getSelectedIndex() == 1 ? ThresholdSellRule.Unit.DOLLARS : ThresholdSellRule.Unit.PERCENT,
                BigDecimal.valueOf(((Number) valueSpinner.getValue()).doubleValue()),
                valueCapEnabled.isSelected()
                        ? new ThresholdSellRule.ValueLimit(
                                valueBasis.getSelectedIndex() == 1
                                        ? ThresholdSellRule.ValueBasis.SHARE_PRICE
                                        : ThresholdSellRule.ValueBasis.POSITION_VALUE,
                                BigDecimal.valueOf(((Number) valueCapSpinner.getValue()).doubleValue()))
                        : null);
    }

    private static String signed(BigDecimal value) {
        BigDecimal rounded = Monetary.round(value);
        return (rounded.signum() < 0 ? "-$" : "+$") + rounded.abs().toPlainString();
    }

    private static JPanel section(String title) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0,
                        ThemeColors.color("NeuralArc.Section.border", new Color(190, 194, 202))),
                title);
        border.setTitleFont(UI_BOLD_FONT);
        border.setTitleColor(ThemeColors.color("NeuralArc.Section.titleForeground", new Color(70, 75, 85)));
        panel.setBorder(BorderFactory.createCompoundBorder(border, new EmptyBorder(4, 4, 6, 4)));
        return panel;
    }

    private static GridBagConstraints gbc(int row) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1;
        gbc.insets = new Insets(2, 0, 2, 0);
        return gbc;
    }

    private static JLabel description(String text, int width) {
        JLabel label = new JLabel("<html><body style='width:" + width + "px'>" + escape(text) + "</body></html>");
        label.setFont(DESCRIPTION_FONT);
        label.setForeground(MUTED);
        return label;
    }

    private static JLabel label(String text, Font font, Color color) {
        JLabel label = new JLabel(text);
        label.setFont(font);
        if (color != null) {
            label.setForeground(color);
        }
        return label;
    }

    private static JButton smallButton(String text, Runnable action) {
        JButton button = new JButton(text);
        button.setFont(DESCRIPTION_FONT);
        button.setFocusable(false);
        button.addActionListener(e -> action.run());
        return button;
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Colours P&amp;L by sign and right-aligns the numeric columns. */
    private static final class RowRenderer extends DefaultTableCellRenderer {
        private final ThresholdSellTableModel model;

        private RowRenderer(ThresholdSellTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            ThresholdSellCandidates.Candidate candidate = model.candidateAt(table.convertRowIndexToModel(row));
            String name = model.getColumnName(table.convertColumnIndexToModel(column));
            setHorizontalAlignment(switch (name) {
                case "Symbol", "Workspace" -> SwingConstants.LEFT;
                default -> SwingConstants.RIGHT;
            });
            if (!selected) {
                setForeground("P&L".equals(name)
                        ? (candidate.unrealizedPnl().signum() < 0 ? LOSS : GAIN)
                        : table.getForeground());
            }
            return this;
        }
    }
}
