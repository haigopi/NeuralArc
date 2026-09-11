package com.neuralarc.ui;

import com.neuralarc.model.TimeInForce;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.Monetary;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
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
import java.awt.GridLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Review-and-plan dialog for averaging down losing positions.
 *
 * <p>Laid out top to bottom in the order an operator decides: which positions (every losing
 * position is listed, with a checkbox to leave any out and locked rows explaining why they cannot
 * take a buy), how to buy, and how many shares. Controls that do not apply to the current choice are
 * disabled rather than hidden, so the layout never jumps, and every row previews the exact order the
 * plan will submit. The primary button states how many orders go out.
 */
final class AverageLosingPositionsDialog {
    private static final Color MUTED = new Color(120, 124, 135);
    private static final Color LOSS = ThemeColors.color("NeuralArc.pnlNegative", new Color(240, 113, 120));
    private static final Font UI_FONT = FontLoader.ui(Font.PLAIN, 11f);
    private static final Font UI_BOLD_FONT = FontLoader.ui(Font.BOLD, 11f);
    private static final Font TITLE_FONT = FontLoader.ui(Font.BOLD, 14f);
    private static final Font DESCRIPTION_FONT = FontLoader.ui(Font.PLAIN, 10f);
    private static final int VISIBLE_ROWS = 9;
    /** Text width of full-width prose; each plan column wraps at half of it. */
    private static final int PROSE_WIDTH = 760;
    private static final int COLUMN_PROSE_WIDTH = 340;

    private final JRadioButton limitOrder = new JRadioButton("Patient – limit buy below market", true);
    private final JRadioButton marketOrder = new JRadioButton("Immediate – market buy now");
    private final JSpinner discountSpinner = new JSpinner(new SpinnerNumberModel(1.0d, 0.0d, 99.0d, 0.25d));
    private final JRadioButton dayTimeInForce = new JRadioButton("DAY – expires at session close");
    private final JRadioButton gtcTimeInForce = new JRadioButton("GTC – works until filled or cancelled");
    private final JLabel discountLabel = label("Pullback below market (%)", UI_FONT, null);
    private final JLabel timeInForceLabel = label("Time in force", UI_FONT, null);
    private final JRadioButton currentQuantity = new JRadioButton("Double-down – buy the shares already held", true);
    private final JRadioButton fixedQuantity = new JRadioButton("Controlled add – fixed shares per position");
    private final JSpinner quantitySpinner = new JSpinner(new SpinnerNumberModel(1, 1, 1_000_000, 1));
    private final JLabel quantityLabel = label("Shares per position", UI_FONT, null);

    private final AverageDownTableModel model;
    private final JLabel selectionSummary = label("", UI_BOLD_FONT, null);
    private final JLabel orderSummary = label("", UI_FONT, MUTED);
    private final JButton submitButton = new JButton();
    private final JPanel root;
    private boolean confirmed;

    /**
     * @param candidates        every losing position in scope, selectable or locked.
     * @param scopeLabel        where the positions come from, e.g. "ORB Engine · Live".
     * @param defaultTimeInForce the manual buy time in force saved in Settings.
     */
    AverageLosingPositionsDialog(
            List<AverageDownCandidates.Candidate> candidates,
            String scopeLabel,
            TimeInForce defaultTimeInForce
    ) {
        ButtonGroup orderType = new ButtonGroup();
        orderType.add(limitOrder);
        orderType.add(marketOrder);
        ButtonGroup timeInForce = new ButtonGroup();
        timeInForce.add(dayTimeInForce);
        timeInForce.add(gtcTimeInForce);
        (defaultTimeInForce == TimeInForce.GTC ? gtcTimeInForce : dayTimeInForce).setSelected(true);
        ButtonGroup quantityMode = new ButtonGroup();
        quantityMode.add(currentQuantity);
        quantityMode.add(fixedQuantity);

        model = new AverageDownTableModel(candidates, currentPlan());
        root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(14, 16, 12, 16));
        root.add(header(scopeLabel), BorderLayout.NORTH);

        JPanel body = new JPanel(new BorderLayout(0, 10));
        body.setOpaque(false);
        body.add(positionsSection(), BorderLayout.CENTER);
        JPanel plan = new JPanel(new GridLayout(1, 2, 12, 0));
        plan.setOpaque(false);
        plan.add(executionSection());
        plan.add(sizingSection());
        body.add(plan, BorderLayout.SOUTH);
        root.add(body, BorderLayout.CENTER);
        root.add(footer(), BorderLayout.SOUTH);

        limitOrder.addActionListener(e -> planChanged());
        marketOrder.addActionListener(e -> planChanged());
        dayTimeInForce.addActionListener(e -> planChanged());
        gtcTimeInForce.addActionListener(e -> planChanged());
        currentQuantity.addActionListener(e -> planChanged());
        fixedQuantity.addActionListener(e -> planChanged());
        discountSpinner.addChangeListener(e -> planChanged());
        quantitySpinner.addChangeListener(e -> planChanged());
        model.setOnChange(this::refreshSummary);
        planChanged();
    }

    static Optional<AverageLosingPositionsSelection> show(
            Component parent,
            List<AverageDownCandidates.Candidate> candidates,
            String scopeLabel,
            TimeInForce defaultTimeInForce
    ) {
        AverageLosingPositionsDialog content = new AverageLosingPositionsDialog(candidates, scopeLabel, defaultTimeInForce);
        Frame owner = JOptionPane.getFrameForComponent(parent);
        JDialog dialog = new JDialog(owner, "Average Down Losing Positions", true);
        dialog.setContentPane(content.root);
        content.submitButton.addActionListener(e -> {
            content.confirmed = true;
            dialog.dispose();
        });
        dialog.getRootPane().setDefaultButton(content.submitButton);
        dialog.pack();
        dialog.setMinimumSize(dialog.getSize());
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
        return content.confirmed ? Optional.of(content.selection()) : Optional.empty();
    }

    JPanel root() {
        return root;
    }

    AverageDownTableModel model() {
        return model;
    }

    AverageLosingPositionsSelection selection() {
        AverageLosingPositionsSelection plan = currentPlan();
        return new AverageLosingPositionsSelection(
                plan.orderType(),
                plan.quantityMode(),
                plan.quantity(),
                plan.limitDiscountPercent(),
                plan.timeInForce(),
                model.selectedIds()
        );
    }

    boolean fixedQuantityEditable() {
        return quantitySpinner.isEnabled();
    }

    boolean limitControlsEditable() {
        return discountSpinner.isEnabled() && dayTimeInForce.isEnabled() && gtcTimeInForce.isEnabled();
    }

    boolean submitEnabled() {
        return submitButton.isEnabled();
    }

    String submitText() {
        return submitButton.getText();
    }

    void chooseMarketOrder() {
        marketOrder.doClick();
    }

    void chooseFixedQuantity(int shares) {
        fixedQuantity.doClick();
        quantitySpinner.setValue(shares);
    }

    private JComponent header(String scopeLabel) {
        JPanel panel = new JPanel(new BorderLayout(0, 3));
        panel.setOpaque(false);
        panel.add(label("Average down losing positions", TITLE_FONT, null), BorderLayout.NORTH);
        panel.add(label("<html><body style='width:" + PROSE_WIDTH + "px'>Places one buy order for each position you keep ticked below"
                + (scopeLabel == null || scopeLabel.isBlank() ? "" : ", from <b>" + escape(scopeLabel) + "</b>")
                + ". Averaging down lowers your cost basis but adds to positions already moving against you.</body></html>",
                DESCRIPTION_FONT, MUTED), BorderLayout.CENTER);
        return panel;
    }

    private JComponent positionsSection() {
        JPanel section = section("1  ·  Positions");
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
        section.add(description("Every losing position in scope is listed. Untick any you want to leave out. "
                + "Where a target sell is working, it is resized to your new share count and repriced from the "
                + "new average once the buy fills. Greyed rows cannot take a buy right now; the Note says why.",
                PROSE_WIDTH), BorderLayout.SOUTH);
        return section;
    }

    private void configureColumns(JTable table) {
        RowRenderer renderer = new RowRenderer(model);
        for (int i = 0; i < table.getColumnCount(); i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            if (i == AverageDownTableModel.INCLUDE) {
                column.setMaxWidth(28);
                column.setMinWidth(28);
                continue;
            }
            column.setCellRenderer(renderer);
            String name = model.getColumnName(i);
            column.setPreferredWidth(switch (name) {
                case "Symbol", "Shares", "Buy Qty" -> 56;
                case "Workspace" -> 110;
                case "Avg Entry", "Last", "Buy At" -> 72;
                case "P&L" -> 118;
                default -> 230;
            });
        }
    }

    private JComponent executionSection() {
        JPanel section = section("2  ·  How to buy");
        GridBagConstraints gbc = gbc(0);
        section.add(limitOrder, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(2, 24, 2, 0);
        section.add(labelled(discountLabel, discountSpinner), gbc);
        gbc.gridy++;
        section.add(timeInForceLabel, gbc);
        gbc.gridy++;
        section.add(dayTimeInForce, gbc);
        gbc.gridy++;
        section.add(gtcTimeInForce, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(6, 0, 2, 0);
        section.add(marketOrder, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(6, 0, 0, 0);
        section.add(description("Patient buys wait for a pullback and may not fill; e.g. AAPL at $100 with a 2% "
                + "pullback places a limit at $98. Immediate buys fill now at whatever the market pays. "
                + "Time in force applies to limit buys only.", COLUMN_PROSE_WIDTH), gbc);
        return section;
    }

    private JComponent sizingSection() {
        JPanel section = section("3  ·  How many shares");
        GridBagConstraints gbc = gbc(0);
        section.add(currentQuantity, gbc);
        gbc.gridy++;
        section.add(fixedQuantity, gbc);
        gbc.gridy++;
        gbc.insets = new Insets(2, 24, 2, 0);
        section.add(labelled(quantityLabel, quantitySpinner), gbc);
        gbc.gridy++;
        gbc.insets = new Insets(6, 0, 0, 0);
        section.add(description("Double-down matches each position's current share count, doubling it. "
                + "Controlled add buys the same fixed number of shares for every ticked position – "
                + "a smaller, even increase in risk.", COLUMN_PROSE_WIDTH), gbc);
        gbc.gridy++;
        gbc.weighty = 1;
        section.add(javax.swing.Box.createGlue(), gbc);
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

    private void planChanged() {
        boolean limit = limitOrder.isSelected();
        discountSpinner.setEnabled(limit);
        discountLabel.setEnabled(limit);
        timeInForceLabel.setEnabled(limit);
        dayTimeInForce.setEnabled(limit);
        gtcTimeInForce.setEnabled(limit);
        boolean fixed = fixedQuantity.isSelected();
        quantitySpinner.setEnabled(fixed);
        quantityLabel.setEnabled(fixed);
        if (model != null) {
            model.setPlan(currentPlan());
        }
    }

    private void refreshSummary() {
        int selected = model.selectedCount();
        int locked = model.lockedCount();
        selectionSummary.setText(selected + " of " + model.selectableCount() + " selected"
                + (locked > 0 ? "  ·  " + locked + " locked" : ""));
        boolean limit = limitOrder.isSelected();
        String kind = limit ? "limit buy" : "market buy";
        int unpriced = model.unpricedSelectedCount();
        String summary = selected == 0
                ? "Tick at least one position to continue."
                : selected + " " + kind + (selected == 1 ? "" : "s")
                        + (limit ? " (" + (gtcTimeInForce.isSelected() ? "GTC" : "DAY") + ")" : "")
                        + "  ·  est. $" + Monetary.round(model.estimatedCost()).toPlainString()
                        + (unpriced > 0 ? "  ·  " + unpriced + " without a market price will be skipped" : "");
        orderSummary.setText(summary);
        submitButton.setEnabled(selected > 0);
        submitButton.setText(selected == 0
                ? "Average Down"
                : "Average Down " + selected + " Position" + (selected == 1 ? "" : "s"));
    }

    private AverageLosingPositionsSelection currentPlan() {
        return new AverageLosingPositionsSelection(
                marketOrder.isSelected()
                        ? AverageLosingPositionsSelection.OrderType.MARKET
                        : AverageLosingPositionsSelection.OrderType.LIMIT_BELOW_MARKET,
                fixedQuantity.isSelected()
                        ? AverageLosingPositionsSelection.QuantityMode.FIXED_INPUT_QUANTITY
                        : AverageLosingPositionsSelection.QuantityMode.CURRENT_POSITION_QUANTITY,
                ((Number) quantitySpinner.getValue()).intValue(),
                BigDecimal.valueOf(((Number) discountSpinner.getValue()).doubleValue()),
                gtcTimeInForce.isSelected() ? TimeInForce.GTC : TimeInForce.DAY
        );
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

    private static JComponent labelled(JLabel label, JSpinner spinner) {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setOpaque(false);
        spinner.setPreferredSize(new Dimension(84, spinner.getPreferredSize().height));
        panel.add(label, BorderLayout.CENTER);
        panel.add(spinner, BorderLayout.EAST);
        return panel;
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

    /** Greys locked rows, reddens losses, and right-aligns the numeric columns. */
    private static final class RowRenderer extends DefaultTableCellRenderer {
        private final AverageDownTableModel model;

        private RowRenderer(AverageDownTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            AverageDownCandidates.Candidate candidate = model.candidateAt(table.convertRowIndexToModel(row));
            String name = model.getColumnName(table.convertColumnIndexToModel(column));
            setHorizontalAlignment(switch (name) {
                case "Symbol", "Workspace", "Note" -> SwingConstants.LEFT;
                default -> SwingConstants.RIGHT;
            });
            if (!selected) {
                if (!candidate.selectable()) {
                    setForeground(MUTED);
                } else if ("P&L".equals(name)) {
                    setForeground(LOSS);
                } else {
                    setForeground(table.getForeground());
                }
            }
            setToolTipText("Note".equals(name) && !candidate.note().isEmpty() ? candidate.note() : null);
            return this;
        }
    }
}
