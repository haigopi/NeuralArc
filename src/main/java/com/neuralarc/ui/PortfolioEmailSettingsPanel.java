package com.neuralarc.ui;

import com.neuralarc.model.PortfolioEmailSettings;
import com.neuralarc.util.FontLoader;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Settings for the portfolio snapshot emails: on or off, where they go, and the US Eastern times they
 * are sent on trading days. The five default times can be retimed or switched off, more times added,
 * and the defaults restored; "Send a Snapshot Now" emails one straight away to check it arrives.
 */
public class PortfolioEmailSettingsPanel extends JPanel {
    private static final int FIELD_GAP = 10;
    private static final int FORM_LABEL_COLUMN_WIDTH = 210;
    private static final String MUTED_PROPERTY = "neuralarc.mutedDescription";
    private static final Color TEXT_MUTED = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground")
            : new Color(130, 130, 130);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter LOOSE_TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final String[] TIME_CHOICES = timeChoices();
    private static final Map<String, String> DEFAULT_HINTS = Map.of(
            "Before the open", "Default 09:25 ET: 5 minutes before the 9:30 open.",
            "After the open", "Default 09:40 ET: 10 minutes after the open.",
            "Lunch", "Default 12:00 ET.",
            "Before the close", "Default 15:55 ET: 5 minutes before the 4:00 PM close.",
            "After the close", "Default 16:10 ET: 10 minutes after the close.");
    private static final String DESCRIPTION = "Each email covers every workspace: the bottom bar's totals, each "
            + "workspace's figures, and the Risk Dashboard's analysis (open P&L by symbol, exposure, concentration and "
            + "advisories). Defaults: 09:25 (5 minutes before the 9:30 open), 09:40 (10 minutes after it), 12:00, 15:55 "
            + "(5 minutes before the 4:00 close) and 16:10 (10 minutes after it). Emails go out only on US trading days "
            + "while NeuralArc is running; a time missed by more than 3 minutes is skipped rather than sent late.";

    private final JCheckBox enabledBox = new JCheckBox("Email a portfolio snapshot on US trading days");
    private final JTextField recipientField = new JTextField(25);
    private final JPanel slotsPanel = new JPanel();
    private final List<SlotRow> rows = new ArrayList<>();
    private final JButton addTimeButton = new JButton("Add a Time");
    private final JButton restoreDefaultsButton = new JButton("Restore Default Times");
    private final JButton sendNowButton = new JButton("Send a Snapshot Now");
    private final JLabel statusLabel = new JLabel(" ");
    private Consumer<PortfolioEmailSettings> sendNowHandler;

    public PortfolioEmailSettingsPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        buildUi();
        populate(PortfolioEmailSettings.defaults());
    }

    public PortfolioEmailSettings settings() {
        return new PortfolioEmailSettings(enabledBox.isSelected(), recipientField.getText(),
                rows.stream().map(SlotRow::slot).toList());
    }

    public void populate(PortfolioEmailSettings settings) {
        PortfolioEmailSettings safe = settings == null ? PortfolioEmailSettings.defaults() : settings;
        enabledBox.setSelected(safe.enabled());
        recipientField.setText(safe.recipient());
        rows.clear();
        safe.slots().forEach(slot -> rows.add(new SlotRow(slot)));
        rebuildSlots();
        statusLabel.setText(" ");
    }

    /** Null when the settings can be saved; otherwise what to fix. */
    public String validationError() {
        String recipient = recipientField.getText().trim();
        if (!recipient.isEmpty() && !recipient.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            return "Enter a valid email address for portfolio snapshot emails, or leave it blank to use your User Email.";
        }
        for (SlotRow row : rows) {
            if (row.parsedTime().isEmpty()) {
                return "\"" + row.timeText() + "\" is not a time. Enter times as HH:mm on a 24-hour US Eastern clock,"
                        + " for example 09:25 or 15:55.";
            }
        }
        return null;
    }

    /** Shows "Send a Snapshot Now", which hands the settings on screen (saved or not) to the handler. */
    public void setSendNowHandler(Consumer<PortfolioEmailSettings> handler) {
        this.sendNowHandler = handler;
        sendNowButton.setVisible(handler != null);
    }

    JCheckBox enabledCheckBox() {
        return enabledBox;
    }

    JTextField recipientInput() {
        return recipientField;
    }

    JButton sendNowButton() {
        return sendNowButton;
    }

    JLabel statusLabel() {
        return statusLabel;
    }

    int slotCount() {
        return rows.size();
    }

    void setSlotTimeText(int index, String text) {
        rows.get(index).time.setSelectedItem(text);
    }

    boolean slotControlsEnabled() {
        return rows.stream().allMatch(row -> row.time.isEnabled() && row.enabled.isEnabled());
    }

    void addTime(LocalTime time) {
        rows.add(new SlotRow(new PortfolioEmailSettings.Slot(PortfolioEmailSettings.CUSTOM_LABEL, time, true)));
        rebuildSlots();
    }

    void restoreDefaultTimes() {
        rows.clear();
        PortfolioEmailSettings.defaultSlots().forEach(slot -> rows.add(new SlotRow(slot)));
        rebuildSlots();
    }

    private void buildUi() {
        enabledBox.setOpaque(false);
        enabledBox.addActionListener(e -> updateControlState());
        slotsPanel.setOpaque(false);
        slotsPanel.setLayout(new BoxLayout(slotsPanel, BoxLayout.Y_AXIS));
        slotsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        addTimeButton.addActionListener(e -> addTime(nextFreeTime()));
        restoreDefaultsButton.addActionListener(e -> restoreDefaultTimes());
        sendNowButton.addActionListener(e -> sendNow());
        sendNowButton.setVisible(false);
        DialogButtonStyles.apply(sendNowButton, "icons/verify.svg");
        sendNowButton.setToolTipText(TooltipStyler.text(
                "Email a snapshot right away to the address above, to check the emails arrive and how they look."));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(addTimeButton);
        actions.add(restoreDefaultsButton);
        actions.add(sendNowButton);
        statusLabel.setFont(FontLoader.ui(Font.PLAIN, 11f));

        addFormRow(0, "Snapshot emails:", enabledBox);
        addFormRow(1, "Send to:", column(recipientField, muted("Leave blank to send to the User Email above.")));
        addFormRow(2, "Times (US Eastern):", column(slotsPanel, actions, statusLabel, muted(DESCRIPTION)));
    }

    private void rebuildSlots() {
        slotsPanel.removeAll();
        rows.forEach(row -> slotsPanel.add(row.component));
        updateControlState();
        slotsPanel.revalidate();
        slotsPanel.repaint();
    }

    private void updateControlState() {
        boolean on = enabledBox.isSelected();
        recipientField.setEnabled(on);
        rows.forEach(row -> row.setControlsEnabled(on));
        addTimeButton.setEnabled(on);
        restoreDefaultsButton.setEnabled(on);
    }

    private void sendNow() {
        String problem = validationError();
        if (problem != null) {
            markStatus(false, problem);
            return;
        }
        if (sendNowHandler != null) {
            sendNowHandler.accept(settings());
            markStatus(true, "Snapshot requested. It should arrive within a minute or two; the Event Log shows whether it was sent.");
        }
    }

    private void markStatus(boolean ok, String message) {
        statusLabel.setText("<html><div style='width:360px;'>" + message + "</div></html>");
        statusLabel.setForeground(ok ? new Color(34, 139, 34) : new Color(180, 30, 30));
    }

    private LocalTime nextFreeTime() {
        LocalTime candidate = LocalTime.of(13, 0);
        while (candidate.isBefore(LocalTime.of(20, 0)) && takenTime(candidate)) {
            candidate = candidate.plusMinutes(30);
        }
        return candidate;
    }

    private boolean takenTime(LocalTime time) {
        return rows.stream().anyMatch(row -> row.parsedTime().map(time::equals).orElse(false));
    }

    private static Optional<LocalTime> parseTime(String text) {
        String value = text == null ? "" : text.trim();
        for (DateTimeFormatter format : List.of(TIME, LOOSE_TIME)) {
            try {
                return Optional.of(LocalTime.parse(value, format));
            } catch (DateTimeParseException ignored) {
                // Try the next accepted format.
            }
        }
        return Optional.empty();
    }

    private static String[] timeChoices() {
        List<String> choices = new ArrayList<>();
        for (LocalTime time = LocalTime.of(4, 0); !time.isAfter(LocalTime.of(20, 0)); time = time.plusMinutes(5)) {
            choices.add(time.format(TIME));
            if (time.equals(LocalTime.of(20, 0))) {
                break;
            }
        }
        return choices.toArray(String[]::new);
    }

    private void addFormRow(int row, String labelText, Component valueComponent) {
        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(FORM_LABEL_COLUMN_WIDTH, label.getPreferredSize().height));
        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 0;
        labelGbc.gridy = row;
        labelGbc.anchor = GridBagConstraints.NORTHWEST;
        labelGbc.insets = new Insets(4, 0, FIELD_GAP, FIELD_GAP);
        GridBagConstraints valueGbc = new GridBagConstraints();
        valueGbc.gridx = 1;
        valueGbc.gridy = row;
        valueGbc.weightx = 1;
        valueGbc.fill = GridBagConstraints.HORIZONTAL;
        valueGbc.anchor = GridBagConstraints.NORTHWEST;
        valueGbc.insets = new Insets(0, 0, FIELD_GAP, 0);
        add(label, labelGbc);
        add(valueComponent, valueGbc);
    }

    private static JPanel column(JComponent... children) {
        JPanel column = new JPanel();
        column.setOpaque(false);
        column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
        for (int i = 0; i < children.length; i++) {
            if (i > 0) {
                column.add(Box.createVerticalStrut(6));
            }
            children[i].setAlignmentX(Component.LEFT_ALIGNMENT);
            column.add(children[i]);
        }
        return column;
    }

    private static JLabel muted(String text) {
        JLabel label = new JLabel("<html><div style='width:360px;'>" + text + "</div></html>");
        label.putClientProperty(MUTED_PROPERTY, Boolean.TRUE);
        label.setForeground(TEXT_MUTED);
        label.setFont(FontLoader.ui(Font.PLAIN, 11f));
        return label;
    }

    /** One send time: its switch (named after the time), its editable Eastern time, and Remove for added times. */
    private final class SlotRow {
        private final String label;
        private final LocalTime fallback;
        private final JCheckBox enabled;
        private final JComboBox<String> time = new JComboBox<>(TIME_CHOICES);
        private final JButton remove = new JButton("Remove");
        private final JPanel component = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));

        private SlotRow(PortfolioEmailSettings.Slot slot) {
            label = slot.label();
            fallback = slot.timeEt();
            enabled = new JCheckBox(slot.label(), slot.enabled());
            enabled.setOpaque(false);
            enabled.setFont(FontLoader.ui(Font.PLAIN, 12f));
            enabled.setPreferredSize(new Dimension(150, enabled.getPreferredSize().height));
            enabled.setToolTipText(TooltipStyler.text(DEFAULT_HINTS.getOrDefault(slot.label(), "A time you added.")));
            time.setEditable(true);
            time.setSelectedItem(slot.timeEt().format(TIME));
            time.setFont(FontLoader.ui(Font.PLAIN, 12f));
            time.setPreferredSize(new Dimension(92, time.getPreferredSize().height));
            time.setToolTipText(TooltipStyler.text("US Eastern time on a 24-hour clock. Pick one or type it, for example 09:27."));
            remove.setVisible(!PortfolioEmailSettings.isDefaultLabel(slot.label()));
            remove.setFont(FontLoader.ui(Font.PLAIN, 12f));
            remove.addActionListener(e -> {
                rows.remove(this);
                rebuildSlots();
            });
            JLabel zone = new JLabel("ET");
            zone.putClientProperty(MUTED_PROPERTY, Boolean.TRUE);
            zone.setForeground(TEXT_MUTED);
            zone.setFont(FontLoader.ui(Font.PLAIN, 11f));
            component.setOpaque(false);
            component.setAlignmentX(Component.LEFT_ALIGNMENT);
            component.add(enabled);
            component.add(time);
            component.add(zone);
            component.add(remove);
        }

        private String timeText() {
            Object item = time.getEditor().getItem();
            return item == null ? "" : item.toString().trim();
        }

        private Optional<LocalTime> parsedTime() {
            return parseTime(timeText());
        }

        private PortfolioEmailSettings.Slot slot() {
            return new PortfolioEmailSettings.Slot(label, parsedTime().orElse(fallback), enabled.isSelected());
        }

        private void setControlsEnabled(boolean on) {
            enabled.setEnabled(on);
            time.setEnabled(on);
            remove.setEnabled(on);
        }
    }
}
