package com.neuralarc.ui;

import com.neuralarc.util.FontLoader;

import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.Font;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * One dialog for every way of withdrawing buys, with a live count beside each kind so the operator
 * sees what a tick will touch before confirming. Kinds with nothing to cancel are shown disabled.
 */
final class CancelBuyOrdersDialog {
    private CancelBuyOrdersDialog() {
    }

    static Optional<Set<CancelBuyOrdersSelection.Kind>> show(Component parent, Map<CancelBuyOrdersSelection.Kind, Integer> counts) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        JLabel intro = new JLabel("<html><body style='width:380px'>Choose which buys to cancel. Open positions,"
                + " sell orders and stop losses are not touched.</body></html>");
        intro.setFont(FontLoader.ui(Font.PLAIN, 12f));
        intro.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(intro);

        Map<CancelBuyOrdersSelection.Kind, JCheckBox> boxes = new java.util.EnumMap<>(CancelBuyOrdersSelection.Kind.class);
        for (CancelBuyOrdersSelection.Kind kind : CancelBuyOrdersSelection.Kind.values()) {
            int count = counts.getOrDefault(kind, 0);
            JCheckBox box = new JCheckBox(kind.label() + " (" + count + ")", count > 0);
            box.setEnabled(count > 0);
            box.setToolTipText(kind.description());
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel detail = new JLabel("<html><body style='width:360px;color:#8e97a8;font-size:9px'>"
                    + kind.description() + "</body></html>");
            detail.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(javax.swing.Box.createVerticalStrut(8));
            panel.add(box);
            panel.add(detail);
            boxes.put(kind, box);
        }

        int choice = JOptionPane.showConfirmDialog(parent, panel, "Cancel Buy Orders",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return Optional.empty();
        }
        Set<CancelBuyOrdersSelection.Kind> ticked = EnumSet.noneOf(CancelBuyOrdersSelection.Kind.class);
        boxes.forEach((kind, box) -> {
            if (box.isSelected()) {
                ticked.add(kind);
            }
        });
        return Optional.of(ticked);
    }
}
