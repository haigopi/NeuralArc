package com.neuralarc.ui;

import com.neuralarc.model.TimeInForce;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.Monetary;

import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.List;
import java.util.Optional;

/**
 * Change Shares & Time In Force: set the share count and DAY/GTC for the unfilled entries in view,
 * globally, individually, or globally with some rows excluded. Entries already working at the broker are
 * cancelled and placed again with the new values; ones not placed yet are only updated.
 */
final class SharesAndTimeInForceDialog extends JDialog {
    private static final String KEEP = "Keep current";

    private final SharesAndTimeInForcePlan plan;
    private final PlanTableModel model;
    private final JLabel summary = new JLabel(" ");
    private List<SharesAndTimeInForcePlan.Change> result;

    SharesAndTimeInForceDialog(Component parent, SharesAndTimeInForcePlan plan, String scopeLabel) {
        super(parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent),
                "Change Shares & Time In Force", ModalityType.APPLICATION_MODAL);
        DialogCloseActions.bindEscapeToClose(this);
        this.plan = plan;
        this.model = new PlanTableModel();

        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        JLabel intro = new JLabel(CompactFormLayout.wrapped("Unfilled entries in " + scopeLabel + ". Set one value for"
                + " every included row, then adjust or exclude rows individually. Entries already working at the"
                + " broker are cancelled and placed again with the new values; entries not placed yet are only"
                + " updated. Positions that already hold shares are not listed.", 620));
        intro.setFont(FontLoader.ui(Font.PLAIN, 11f));

        JSpinner globalShares = new JSpinner(new SpinnerNumberModel(0, 0, 1_000_000, 1));
        globalShares.setToolTipText("0 keeps each row's current share count");
        JComboBox<Object> globalTif = new JComboBox<>(new Object[]{KEEP, TimeInForce.DAY, TimeInForce.GTC});
        JButton applyGlobal = new JButton("Apply to Included Rows");
        applyGlobal.addActionListener(event -> {
            Object tif = globalTif.getSelectedItem();
            plan.applyToIncluded((Integer) globalShares.getValue(), tif instanceof TimeInForce value ? value : null);
            model.fireTableDataChanged();
            refreshSummary();
        });
        JButton includeAll = new JButton("Include All");
        includeAll.addActionListener(event -> setAllIncluded(true));
        JButton excludeAll = new JButton("Exclude All");
        excludeAll.addActionListener(event -> setAllIncluded(false));
        JPanel global = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        global.add(new JLabel("Shares for all:"));
        global.add(globalShares);
        global.add(new JLabel("Time in force:"));
        global.add(globalTif);
        global.add(applyGlobal);
        global.add(includeAll);
        global.add(excludeAll);
        JPanel north = new JPanel(new BorderLayout(0, 8));
        north.add(intro, BorderLayout.NORTH);
        north.add(global, BorderLayout.SOUTH);
        content.add(north, BorderLayout.NORTH);

        JTable table = new JTable(model);
        table.setRowHeight(24);
        table.getColumnModel().getColumn(7).setCellEditor(new DefaultCellEditor(new JComboBox<>(TimeInForce.values())));
        table.getColumnModel().getColumn(0).setMaxWidth(70);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(1).setPreferredWidth(70);
        model.addTableModelListener(event -> refreshSummary());
        JScrollPane scroll = new JScrollPane(table);
        scroll.setPreferredSize(new Dimension(760, Math.min(420, 60 + plan.rows().size() * 24)));
        content.add(scroll, BorderLayout.CENTER);

        JButton cancel = new JButton("Cancel");
        DialogButtonStyles.apply(cancel, "icons/close.svg");
        cancel.addActionListener(event -> dispose());
        JButton apply = new JButton("Apply Changes");
        DialogButtonStyles.apply(apply, "icons/apply.svg");
        apply.addActionListener(event -> {
            if (table.isEditing()) {
                table.getCellEditor().stopCellEditing();
            }
            result = plan.changes();
            dispose();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel);
        buttons.add(apply);
        JPanel south = new JPanel(new BorderLayout());
        summary.setFont(FontLoader.ui(Font.PLAIN, 11f));
        south.add(summary, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.EAST);
        content.add(south, BorderLayout.SOUTH);
        setContentPane(content);
        refreshSummary();
        pack();
        setLocationRelativeTo(parent);
    }

    /** The chosen changes, or empty when cancelled. */
    Optional<List<SharesAndTimeInForcePlan.Change>> showDialog() {
        setVisible(true);
        return Optional.ofNullable(result);
    }

    private void setAllIncluded(boolean included) {
        plan.rows().forEach(row -> row.included = included);
        model.fireTableDataChanged();
    }

    private void refreshSummary() {
        List<SharesAndTimeInForcePlan.Change> changes = plan.changes();
        long working = changes.stream().filter(SharesAndTimeInForcePlan.Change::workingAtBroker).count();
        summary.setText(changes.isEmpty()
                ? "No changes yet."
                : changes.size() + " row(s) will change; " + working + " working order(s) will be cancelled and placed again.");
    }

    private final class PlanTableModel extends AbstractTableModel {
        private final String[] columns = {"Include", "Symbol", "Entry Order", "Base Limit",
                "Current Shares", "New Shares", "Current TIF", "New TIF"};

        @Override public int getRowCount() { return plan.rows().size(); }
        @Override public int getColumnCount() { return columns.length; }
        @Override public String getColumnName(int column) { return columns[column]; }

        @Override
        public Class<?> getColumnClass(int column) {
            return switch (column) {
                case 0 -> Boolean.class;
                case 4, 5 -> Integer.class;
                case 7 -> TimeInForce.class;
                default -> String.class;
            };
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return column == 0 || ((column == 5 || column == 7) && plan.rows().get(row).included);
        }

        @Override
        public Object getValueAt(int rowIndex, int column) {
            SharesAndTimeInForcePlan.Row row = plan.rows().get(rowIndex);
            return switch (column) {
                case 0 -> row.included;
                case 1 -> row.symbol;
                case 2 -> row.workingAtBroker ? "Working at broker" : "Not placed yet";
                case 3 -> "$" + Monetary.round(row.baseLimit).toPlainString();
                case 4 -> row.currentQuantity;
                case 5 -> row.quantity;
                case 6 -> row.currentTimeInForce;
                default -> row.timeInForce;
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int column) {
            SharesAndTimeInForcePlan.Row row = plan.rows().get(rowIndex);
            if (column == 0 && value instanceof Boolean included) {
                row.included = included;
            } else if (column == 5 && value instanceof Integer quantity && quantity > 0) {
                row.quantity = quantity;
            } else if (column == 7 && value instanceof TimeInForce tif) {
                row.timeInForce = tif;
            }
            fireTableRowsUpdated(rowIndex, rowIndex);
        }
    }
}
