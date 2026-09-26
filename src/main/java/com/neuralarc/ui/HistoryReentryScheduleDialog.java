package com.neuralarc.ui;

import com.neuralarc.model.HistoryReentrySchedule;
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
import java.util.EnumSet;
import java.util.Optional;

/**
 * Sets up the standing Trade History re-entry run: when it fires, how often, which group of stocks it
 * may place, and how many.
 *
 * <p>The manual picker exists so that placing orders is a deliberate act; a schedule gives that up, so
 * this dialog is built to keep the blast radius small — one group, a hard cap on how many stocks, and
 * a spelled-out confirmation before a live schedule is saved. Turning it off leaves the settings in
 * place, so it can be switched back on without being rebuilt.
 */
final class HistoryReentryScheduleDialog extends JDialog {
    private final JCheckBox enabled = new JCheckBox("Re-enter inactive stocks automatically");
    private final JComboBox<HistoryReentrySchedule.Cadence> cadence =
            new JComboBox<>(HistoryReentrySchedule.Cadence.values());
    private final JComboBox<DayOfWeek> day = new JComboBox<>(
            EnumSet.range(DayOfWeek.MONDAY, DayOfWeek.FRIDAY).toArray(new DayOfWeek[0]));
    private final JComboBox<LocalTime> runTime =
            new JComboBox<>(SmartPicksScheduleDialog.SCAN_TIMES.toArray(new LocalTime[0]));
    private final JComboBox<HistoryReentrySchedule.Group> group =
            new JComboBox<>(HistoryReentrySchedule.Group.values());
    private final JSpinner maxStocks = new JSpinner(new SpinnerNumberModel(
            HistoryReentrySchedule.DEFAULT_MAX_STOCKS, 1, HistoryReentrySchedule.MAX_STOCKS_CEILING, 1));
    private final StrategyMode mode;
    private final String existingId;
    private final java.time.LocalDate lastRunDate;
    private HistoryReentrySchedule result;

    HistoryReentryScheduleDialog(Component parent, StrategyMode mode, HistoryReentrySchedule existing) {
        super(parent == null ? null : javax.swing.SwingUtilities.getWindowAncestor(parent),
                "Schedule Re-entry Scan", ModalityType.APPLICATION_MODAL);
        DialogCloseActions.bindEscapeToClose(this);
        this.mode = mode;
        this.existingId = existing == null ? null : existing.id();
        this.lastRunDate = existing == null ? null : existing.lastRunDate();
        HistoryReentrySchedule initial = existing != null ? existing : HistoryReentrySchedule.defaults(mode);
        enabled.setSelected(initial.enabled());
        cadence.setSelectedItem(initial.cadence());
        day.setSelectedItem(initial.day());
        runTime.setSelectedItem(SmartPicksScheduleDialog.nearestOffered(initial.scanTimeEt()));
        group.setSelectedItem(initial.group());
        maxStocks.setValue(initial.maxStocks());
        buildUi(initial);
        pack();
        setMinimumSize(getSize());
        setLocationRelativeTo(parent);
    }

    /** The saved schedule, or empty when cancelled. */
    Optional<HistoryReentrySchedule> showDialog() {
        setVisible(true);
        return Optional.ofNullable(result);
    }

    JCheckBox enabledBox() {
        return enabled;
    }

    /** The schedule the current form describes, without saving it. */
    HistoryReentrySchedule current() {
        return new HistoryReentrySchedule(existingId, enabled.isSelected(), mode,
                (DayOfWeek) day.getSelectedItem(), (LocalTime) runTime.getSelectedItem(),
                (HistoryReentrySchedule.Cadence) cadence.getSelectedItem(),
                (HistoryReentrySchedule.Group) group.getSelectedItem(),
                (Integer) maxStocks.getValue(), lastRunDate);
    }

    private void buildUi(HistoryReentrySchedule initial) {
        JPanel content = new JPanel(new BorderLayout(0, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 10, 14));
        JLabel intro = new JLabel(CompactFormLayout.wrapped("Places " + (mode == StrategyMode.LIVE ? "LIVE" : "paper")
                + " limit buys for stocks in Trade History that no workspace is trading any more — the same run as the"
                + " Re-enter Inactive Stocks button, without the picker. Each goes in at its re-entry price: the lowest"
                + " price it has traded over its last "
                + com.neuralarc.analytics.RecentLow.WEEK_SESSIONS + " sessions. Stocks still held, or still waiting to"
                + " fill, are skipped. NeuralArc must be running at that time.", 460));
        intro.setFont(FontLoader.ui(Font.PLAIN, 11f));
        content.add(intro, BorderLayout.NORTH);

        JPanel form = CompactFormLayout.form();
        CompactFormLayout.addRow(form, 0, new JLabel("How often"), cadence, null);
        CompactFormLayout.addRow(form, 1, new JLabel("Day"), day, null);
        CompactFormLayout.addRow(form, 2, new JLabel("Time"), runTime, new JLabel("ET"));
        CompactFormLayout.addRow(form, 3, new JLabel("Which stocks"), group, null);
        CompactFormLayout.addRow(form, 4, new JLabel("Place at most"), maxStocks, new JLabel("stocks per run"));

        JPanel options = new JPanel(new BorderLayout(0, 6));
        options.add(enabled, BorderLayout.NORTH);
        options.add(form, BorderLayout.CENTER);
        JLabel note = new JLabel(CompactFormLayout.wrapped("A run places the stocks that qualify, newest history first,"
                + " up to the cap — a cap is the only thing standing between a quiet week and dozens of orders. Each"
                + " run goes into its own dated workspace, and every placement is written to the event log."
                + (initial.lastRunDate() == null ? "" : " Last run: " + initial.lastRunDate() + "."), 460));
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
        HistoryReentrySchedule schedule = current();
        if (schedule.enabled() && mode == StrategyMode.LIVE) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "<html>This schedule will place <b>LIVE</b> limit buy orders on its own, with no further"
                            + " confirmation.<br><br>" + schedule.summary() + "<br><br>Continue?</html>",
                    "Live Orders", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        result = schedule;
        dispose();
    }
}
