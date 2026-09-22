package com.neuralarc.ui;

import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.SmartPicksSchedule;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.util.FontLoader;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Sets up a Smart Picks workspace's autonomous scan: when it runs (US Eastern time, on the chosen
 * weekdays), how many shares each pick buys, which recommendation term to use, and whether the picks are
 * placed automatically or only recorded. Opens pre-filled with the workspace's existing schedule, or the
 * strategy's fitted default.
 */
final class SmartPicksScheduleDialog extends JDialog {
    /** Scan times offered: the regular session, every 15 minutes. */
    static final List<LocalTime> SCAN_TIMES = scanTimes();

    private final JComboBox<LocalTime> scanTime = new JComboBox<>(SCAN_TIMES.toArray(new LocalTime[0]));
    private final Map<DayOfWeek, JCheckBox> dayBoxes = new EnumMap<>(DayOfWeek.class);
    private final JSpinner quantity = new JSpinner(new SpinnerNumberModel(1, 1, 100_000, 1));
    private final JComboBox<RecommendationType> term = new JComboBox<>(RecommendationType.values());
    private final JCheckBox placeOrders = new JCheckBox("Place orders automatically after each scan");
    private final SmartPicksWorkspaceKind kind;
    private final String workspaceId;
    private final StrategyMode mode;
    private final String existingId;
    private SmartPicksSchedule result;

    SmartPicksScheduleDialog(Component parent, SmartPicksWorkspaceKind kind, String workspaceId, StrategyMode mode,
                             SmartPicksSchedule existing) {
        super(parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent),
                "Schedule " + kind.title() + " Scan", ModalityType.APPLICATION_MODAL);
        DialogCloseActions.bindEscapeToClose(this);
        this.kind = kind;
        this.workspaceId = workspaceId;
        this.mode = mode;
        this.existingId = existing == null ? null : existing.id();
        SmartPicksSchedule initial = existing != null ? existing : defaults(kind, workspaceId, mode);
        scanTime.setSelectedItem(nearestOffered(initial.scanTimeEt()));
        quantity.setValue(initial.quantity());
        term.setSelectedItem(initial.term());
        placeOrders.setSelected(initial.executeAfterScan());
        buildUi(initial.days());
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(parent);
    }

    /** The saved schedule, or empty when the dialog was cancelled. */
    Optional<SmartPicksSchedule> showDialog() {
        setVisible(true);
        return Optional.ofNullable(result);
    }

    /** The strategy-fitted default for a workspace: recommendation only until the operator opts in. */
    static SmartPicksSchedule defaults(SmartPicksWorkspaceKind kind, String workspaceId, StrategyMode mode) {
        return new SmartPicksSchedule(null, true, workspaceId, kind.code(), kind.defaultScanTimeEt(), kind.defaultDays(),
                false, 1, RecommendationType.SHORT_TERM, mode);
    }

    private void buildUi(Set<DayOfWeek> days) {
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        JLabel intro = new JLabel(CompactFormLayout.wrapped("Runs the " + kind.title() + " Smart Picks scan for this "
                + (mode == StrategyMode.LIVE ? "Live" : "Paper") + " workspace. Suggested: " + kind.defaultScheduleText()
                + ". NeuralArc must be running at the scheduled time.", 420));
        intro.setFont(FontLoader.ui(Font.PLAIN, 11f));
        content.add(intro, BorderLayout.NORTH);

        JPanel dayRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        dayRow.setOpaque(false);
        for (DayOfWeek day : EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY)) {
            JCheckBox box = new JCheckBox(day.name().charAt(0) + day.name().substring(1, 3).toLowerCase(), days.contains(day));
            dayBoxes.put(day, box);
            dayRow.add(box);
        }
        JPanel form = CompactFormLayout.form();
        CompactFormLayout.addRow(form, 0, new JLabel("Scan time"), scanTime, new JLabel("ET"));
        CompactFormLayout.addRow(form, 1, new JLabel("Days"), dayRow, null);
        CompactFormLayout.addRow(form, 2, new JLabel("Quantity per pick"), quantity, new JLabel("shares"));
        CompactFormLayout.addRow(form, 3, new JLabel("Term"), term, null);
        JPanel options = new JPanel(new BorderLayout(0, 4));
        options.add(form, BorderLayout.NORTH);
        options.add(placeOrders, BorderLayout.CENTER);
        JLabel note = new JLabel(CompactFormLayout.wrapped("Unchecked, each scan only records its picks in this "
                + "workspace's scan history; checked, the picks are created in this workspace and their base buys "
                + "are placed with your broker.", 420));
        note.setFont(FontLoader.ui(Font.PLAIN, 10f));
        options.add(note, BorderLayout.SOUTH);
        content.add(options, BorderLayout.CENTER);

        JButton cancel = new JButton("Cancel");
        DialogButtonStyles.apply(cancel, "icons/close.svg");
        cancel.addActionListener(event -> dispose());
        JButton save = new JButton("Save Schedule");
        DialogButtonStyles.apply(save, "icons/save.svg");
        save.addActionListener(event -> onSave());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(cancel);
        buttons.add(Box.createHorizontalStrut(2));
        buttons.add(save);
        content.add(buttons, BorderLayout.SOUTH);
        setContentPane(content);
        getRootPane().setDefaultButton(save);
    }

    private void onSave() {
        Set<DayOfWeek> chosen = EnumSet.noneOf(DayOfWeek.class);
        dayBoxes.forEach((day, box) -> {
            if (box.isSelected()) {
                chosen.add(day);
            }
        });
        if (chosen.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Choose at least one day for the scan.",
                    "Schedule Scan", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (placeOrders.isSelected() && mode == StrategyMode.LIVE) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "This schedule will place LIVE orders automatically after each scan. Continue?",
                    "Live Orders", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        result = new SmartPicksSchedule(existingId, true, workspaceId, kind.code(),
                (LocalTime) scanTime.getSelectedItem(), chosen, placeOrders.isSelected(),
                (Integer) quantity.getValue(), (RecommendationType) term.getSelectedItem(), mode);
        dispose();
    }

    private static List<LocalTime> scanTimes() {
        List<LocalTime> times = new ArrayList<>();
        for (LocalTime time = LocalTime.of(9, 30); time.isBefore(LocalTime.of(16, 0)); time = time.plusMinutes(15)) {
            times.add(time);
        }
        return List.copyOf(times);
    }

    /** The offered time nearest {@code time}, so a stored off-grid time still preselects sensibly. */
    static LocalTime nearestOffered(LocalTime time) {
        LocalTime best = SCAN_TIMES.get(0);
        for (LocalTime candidate : SCAN_TIMES) {
            if (Math.abs(candidate.toSecondOfDay() - time.toSecondOfDay()) < Math.abs(best.toSecondOfDay() - time.toSecondOfDay())) {
                best = candidate;
            }
        }
        return best;
    }
}
