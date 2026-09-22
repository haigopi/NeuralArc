package com.neuralarc.ui;

import com.neuralarc.model.SmartPicksSchedule;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;
import java.util.Optional;

/**
 * The Smart Picks workspaces' part of the main window: the empty-state card (what the strategy does,
 * with Analyze and Schedule buttons) and the action-bar controls shown once the grid has rows — Analyze,
 * Schedule, the schedule badge and Cancel Schedule. One view serves all three kinds; {@link #refresh}
 * points it at the selected one.
 */
final class SmartPicksWorkspaceView {
    private final JLabel emptyText = new JLabel();
    private final JButton emptyAnalyze = new JButton();
    private final JButton emptySchedule = new JButton("Schedule Scan…");
    private final JPanel emptyState = new JPanel(new GridBagLayout());
    private final JButton analyzeButton = new JButton();
    private final JButton scheduleButton = new JButton("Schedule Scan…");
    private final JLabel scheduleStatus = new JLabel();
    private final JButton cancelScheduleButton = new JButton("Cancel Schedule");

    SmartPicksWorkspaceView(Font font, Runnable onAnalyze, Runnable onSchedule, Runnable onCancelSchedule) {
        emptyState.setOpaque(false);
        JPanel card = new JPanel();
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        emptyText.setAlignmentX(Component.CENTER_ALIGNMENT);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttons.setOpaque(false);
        buttons.add(emptyAnalyze);
        buttons.add(emptySchedule);
        buttons.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(emptyText);
        card.add(Box.createVerticalStrut(12));
        card.add(buttons);
        emptyState.add(card, new GridBagConstraints());

        emptyAnalyze.addActionListener(event -> onAnalyze.run());
        analyzeButton.addActionListener(event -> onAnalyze.run());
        emptySchedule.addActionListener(event -> onSchedule.run());
        scheduleButton.addActionListener(event -> onSchedule.run());
        cancelScheduleButton.addActionListener(event -> onCancelSchedule.run());
        for (JComponent control : List.of(analyzeButton, scheduleButton, scheduleStatus, cancelScheduleButton)) {
            control.setFont(font);
            control.setVisible(false);
            if (control instanceof JButton button) {
                button.setFocusPainted(false);
            }
        }
        cancelScheduleButton.setToolTipText(TooltipStyler.text("Cancel this workspace's autonomous Smart Picks scan.", 320));
    }

    JComponent emptyState() {
        return emptyState;
    }

    /** Action-bar controls, in display order. */
    List<JComponent> actionControls() {
        return List.of(scheduleStatus, cancelScheduleButton, scheduleButton, analyzeButton);
    }

    /**
     * Shows the controls for {@code kind} (empty when the selected workspace is not a Smart Picks one):
     * the empty-state card while its grid has no rows, the action-bar buttons once it has.
     */
    void refresh(Optional<SmartPicksWorkspaceKind> kind, boolean gridEmpty, Optional<SmartPicksSchedule> schedule) {
        boolean selected = kind.isPresent();
        boolean scheduled = schedule.filter(SmartPicksSchedule::enabled).isPresent();
        kind.ifPresent(value -> {
            String analyzeText = "Analyze " + value.title();
            emptyAnalyze.setText(analyzeText);
            analyzeButton.setText(analyzeText);
            emptyText.setText(emptyStateHtml(value, schedule.orElse(null)));
            String tip = TooltipStyler.text(description(value), 420);
            analyzeButton.setToolTipText(tip);
            emptyAnalyze.setToolTipText(tip);
        });
        String scheduleText = scheduled ? "Edit Schedule…" : "Schedule Scan…";
        scheduleButton.setText(scheduleText);
        emptySchedule.setText(scheduleText);
        analyzeButton.setVisible(selected && !gridEmpty);
        scheduleButton.setVisible(selected && !gridEmpty);
        scheduleStatus.setVisible(selected && scheduled);
        cancelScheduleButton.setVisible(selected && scheduled);
        scheduleStatus.setText(schedule.map(value -> "Scheduled: " + value.summary()
                + (value.executeAfterScan() ? " · auto-orders" : " · recommend only")).orElse(""));
    }

    JButton analyzeButton() { return analyzeButton; }
    JButton scheduleButton() { return scheduleButton; }
    JLabel scheduleStatus() { return scheduleStatus; }
    JButton cancelScheduleButton() { return cancelScheduleButton; }
    JLabel emptyText() { return emptyText; }

    static String description(SmartPicksWorkspaceKind kind) {
        return switch (kind) {
            case MOVERS -> "Fetch the day's biggest gainers and losers on live data, analyze each one with Auto Analyze, "
                    + "and add the setups you choose to this grid.";
            case LEADERS -> "Analyze a diversified list of 20 sector-leading large caps on live data and add the "
                    + "strongest entries to this grid.";
            case REBOUND -> "Find Friday's controlled selloffs in liquid stocks, score each by how often it bounced on "
                    + "Mondays after a Friday decline, and add the best rebound setups to this grid.";
        };
    }

    private static String example(SmartPicksWorkspaceKind kind) {
        return switch (kind) {
            case MOVERS -> "A stock up 18% on news with three times its usual volume is analyzed at 10:00 ET, once the "
                    + "opening swings settle, and planned with an entry near support and a protective stop.";
            case LEADERS -> "A sector leader dips 3% with its long-term trend intact; Monday's scan plans an entry "
                    + "near support and holds it through the week.";
            case REBOUND -> "A liquid stock falls 5% on Friday with no broken trend; the 3:30 PM ET scan buys it before "
                    + "the close, aiming for the Monday bounce its history shows.";
        };
    }

    private static String emptyStateHtml(SmartPicksWorkspaceKind kind, SmartPicksSchedule schedule) {
        String scheduleLine = schedule != null && schedule.enabled()
                ? "Scheduled: " + escape(schedule.summary())
                        + (schedule.executeAfterScan() ? ", placing orders automatically." : ", recommendation only.")
                : "Not scheduled yet. Suggested: " + escape(kind.defaultScheduleText()) + ".";
        return "<html><div style='text-align:left; width:420px;'>"
                + "<div style='font-size:9px; color:#ffffff;'><b>Description:</b></div>"
                + "<div style='margin-top:4px; font-size:9px; color:#ffccff;'>" + escape(description(kind)) + "</div>"
                + "<br><div style='font-size:9px; color:#ffffff;'><b>Example:</b></div>"
                + "<div style='margin-top:4px; font-size:9px; color:#ffccff;'>" + escape(example(kind)) + "</div>"
                + "<br><div style='font-size:9px; color:#ffffff;'><b>Know before you make a next move:</b></div>"
                + "<ul style='margin-top:4px; padding-left:14px; font-size:9px; color:#ffccff;'>"
                + "<li><b>Analyze</b> opens the Smart Picks review; the picks you place land in this workspace.</li>"
                + "<li><b>Schedule</b> runs the scan on its own at a set time, either recording the picks in the scan "
                + "history below or placing them automatically.</li>"
                + "<li>" + scheduleLine + "</li>"
                + "</ul></div></html>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
