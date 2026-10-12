package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.List;
import java.util.Optional;

/**
 * Review dialog for Reconcile from Broker Fills. It lists each record the broker's order history says
 * is missing or wrong, ticked by default; the operator unticks any to leave alone and applies the
 * rest. Nothing is sent to the broker: applying only corrects NeuralArc's own order records.
 */
final class BrokerFillReconcileDialog {
    private static final Color MUTED = new Color(120, 124, 135);
    private static final Font UI_FONT = FontLoader.ui(Font.PLAIN, 11f);
    private static final Font UI_BOLD_FONT = FontLoader.ui(Font.BOLD, 11f);
    private static final Font TITLE_FONT = FontLoader.ui(Font.BOLD, 14f);
    private static final Font DESCRIPTION_FONT = FontLoader.ui(Font.PLAIN, 10f);
    private static final int VISIBLE_ROWS = 10;
    private static final int PROSE_WIDTH = 720;

    private final BrokerFillReconcileTableModel model;
    private final JLabel selectionSummary = label("", UI_BOLD_FONT, null);
    private final JLabel effectSummary = label("", UI_FONT, MUTED);
    private final JButton submitButton = new JButton();
    private final JPanel root;
    private boolean confirmed;

    BrokerFillReconcileDialog(List<BrokerFillReconciler.Proposal> proposals, String scopeLabel) {
        model = new BrokerFillReconcileTableModel(proposals);
        root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(14, 16, 12, 16));
        root.add(header(scopeLabel), BorderLayout.NORTH);
        root.add(table(), BorderLayout.CENTER);
        root.add(footer(), BorderLayout.SOUTH);
        model.setOnChange(this::refreshSummary);
        refreshSummary();
    }

    static Optional<List<BrokerFillReconciler.Proposal>> show(
            Component parent, List<BrokerFillReconciler.Proposal> proposals, String scopeLabel) {
        BrokerFillReconcileDialog content = new BrokerFillReconcileDialog(proposals, scopeLabel);
        Frame owner = JOptionPane.getFrameForComponent(parent);
        JDialog dialog = new JDialog(owner, "Reconcile from Broker Fills", true);
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
        return content.confirmed ? Optional.of(content.model.selected()) : Optional.empty();
    }

    BrokerFillReconcileTableModel model() {
        return model;
    }

    boolean submitEnabled() {
        return submitButton.isEnabled();
    }

    String submitText() {
        return submitButton.getText();
    }

    private JComponent header(String scopeLabel) {
        JPanel panel = new JPanel(new BorderLayout(0, 3));
        panel.setOpaque(false);
        panel.add(label("Reconcile from broker fills", TITLE_FONT, null), BorderLayout.NORTH);
        panel.add(label("<html><body style='width:" + PROSE_WIDTH + "px'>These positions hold shares that their own orders do not explain. "
                + "Below, each fix the broker's order history supports"
                + (scopeLabel == null || scopeLabel.isBlank() ? "" : " for <b>" + escape(scopeLabel) + "</b>")
                + ". Applying one only corrects NeuralArc's order record &ndash; nothing is sent to the broker. "
                + "Untick any you want to leave as they are.</body></html>", DESCRIPTION_FONT, MUTED), BorderLayout.CENTER);
        return panel;
    }

    private JComponent table() {
        JPanel section = new JPanel(new BorderLayout(0, 6));
        section.setOpaque(false);
        section.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0,
                ThemeColors.color("NeuralArc.Section.border", new Color(190, 194, 202))));
        JPanel toolbar = new JPanel(new BorderLayout());
        toolbar.setOpaque(false);
        toolbar.setBorder(new EmptyBorder(6, 0, 0, 0));
        toolbar.add(selectionSummary, BorderLayout.WEST);
        JPanel links = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        links.setOpaque(false);
        links.add(smallButton("Select all", model::selectAll));
        links.add(smallButton("Clear", model::clearSelection));
        toolbar.add(links, BorderLayout.EAST);
        section.add(toolbar, BorderLayout.NORTH);

        JTable grid = new JTable(model);
        grid.setFont(UI_FONT);
        grid.getTableHeader().setFont(UI_BOLD_FONT);
        grid.setRowHeight(22);
        grid.setFillsViewportHeight(true);
        grid.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        grid.setAutoResizeMode(JTable.AUTO_RESIZE_LAST_COLUMN);
        grid.getTableHeader().setReorderingAllowed(false);
        RowRenderer renderer = new RowRenderer(model);
        for (int i = 0; i < grid.getColumnCount(); i++) {
            TableColumn column = grid.getColumnModel().getColumn(i);
            if (i == BrokerFillReconcileTableModel.INCLUDE) {
                column.setMinWidth(28);
                column.setMaxWidth(28);
                continue;
            }
            column.setCellRenderer(renderer);
            column.setPreferredWidth(switch (model.getColumnName(i)) {
                case "Symbol", "Side", "Shares" -> 60;
                case "Fix" -> 120;
                case "Price", "Effect" -> 76;
                case "When" -> 96;
                default -> 340;
            });
        }
        JScrollPane scroll = new JScrollPane(grid);
        scroll.setColumnHeaderView(grid.getTableHeader());
        scroll.setPreferredSize(new Dimension(0, grid.getRowHeight() * VISIBLE_ROWS + 26));
        section.add(scroll, BorderLayout.CENTER);
        return section;
    }

    private JComponent footer() {
        JPanel panel = new JPanel(new BorderLayout(12, 0));
        panel.setOpaque(false);
        panel.add(effectSummary, BorderLayout.CENTER);
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

    private void refreshSummary() {
        int selected = model.selectedCount();
        selectionSummary.setText(selected + " of " + model.fixableCount() + " selected"
                + (model.reviewCount() > 0 ? "  ·  " + model.reviewCount() + " need review" : ""));
        effectSummary.setText(selected == 0
                ? (model.fixableCount() == 0 ? "Nothing here can be fixed automatically; see the notes." : "Tick at least one fix to continue.")
                : "Adds " + model.selectedShareDelta().stripTrailingZeros().toPlainString() + " net share(s) to the order records.");
        submitButton.setEnabled(selected > 0);
        submitButton.setText(selected == 0 ? "Apply" : "Apply " + selected + " Fix" + (selected == 1 ? "" : "es"));
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

    /** Greys rows that cannot be applied and right-aligns the numbers. */
    private static final class RowRenderer extends DefaultTableCellRenderer {
        private final BrokerFillReconcileTableModel model;

        private RowRenderer(BrokerFillReconcileTableModel model) {
            this.model = model;
        }

        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean selected, boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            BrokerFillReconciler.Proposal proposal = model.proposalAt(table.convertRowIndexToModel(row));
            String name = model.getColumnName(table.convertColumnIndexToModel(column));
            setHorizontalAlignment(switch (name) {
                case "Shares", "Price", "Effect" -> SwingConstants.RIGHT;
                default -> SwingConstants.LEFT;
            });
            if (!selected) {
                setForeground(proposal.applicable() ? table.getForeground() : MUTED);
            }
            setToolTipText("Why".equals(name) ? proposal.note() : null);
            return this;
        }
    }
}
