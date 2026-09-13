package com.neuralarc.ui;

import com.neuralarc.model.PortfolioEmailSettings;
import com.neuralarc.service.AppSettingsService;
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
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
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
 * Every email NeuralArc sends, in one place. All of them go to the User Email: this section shows
 * that address rather than asking for another. Below it are the trade alerts and the portfolio
 * snapshots with their US Eastern send times, and "Send a Snapshot Now" to check one arrives.
 */
public class CommunicationSettingsPanel extends JPanel {
    private static final int FIELD_GAP = 10;
    private static final int FORM_LABEL_COLUMN_WIDTH = 210;
    private static final String MUTED_PROPERTY = "neuralarc.mutedDescription";
    private static final Color TEXT_PRIMARY = UIManager.getColor("Label.foreground") != null
            ? UIManager.getColor("Label.foreground")
            : new Color(45, 45, 50);
    private static final Color TEXT_MUTED = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground")
            : new Color(130, 130, 130);
    private static final Color WARN = new Color(214, 150, 60);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter LOOSE_TIME = DateTimeFormatter.ofPattern("H:mm");
    private static final String[] TIME_CHOICES = timeChoices();
    private static final Map<String, String> DEFAULT_HINTS = Map.of(
            "Before the open", "Default 09:25 ET: 5 minutes before the 9:30 open.",
            "After the open", "Default 09:40 ET: 10 minutes after the open.",
            "Lunch", "Default 12:00 ET.",
            "Before the close", "Default 15:55 ET: 5 minutes before the 4:00 PM close.",
            "After the close", "Default 16:10 ET: 10 minutes after the close.");
    private static final String TRADE_ALERTS_DESCRIPTION = "For Live strategies. A buy alert is sent when a buy order is "
            + "placed and waiting to fill; a sell alert when a sell order fills.";
    private static final String SNAPSHOTS_DESCRIPTION = "Each snapshot covers every workspace: the bottom bar's totals, each "
            + "workspace's figures, the Risk Dashboard's analysis and the broker reconciliation with Alpaca. Defaults: 09:25 "
            + "(5 minutes before the 9:30 open), 09:40 (10 minutes after it), 12:00, 15:55 (5 minutes before the 4:00 close) "
            + "and 16:10 (10 minutes after it). Snapshots go out only on US trading days while NeuralArc is running; a time "
            + "missed by more than 3 minutes is skipped rather than sent late.";

    private final JLabel recipientLabel = new JLabel(" ");
    private final JLabel recipientHint = muted(" ");
    private final JCheckBox buyExpectedBox = new JCheckBox("Buy order placed / waiting for fill", AppSettingsService.DEFAULT_EMAIL_ON_BUY_EXPECTED);
    private final JCheckBox sellExecutedBox = new JCheckBox("Sell order executed", AppSettingsService.DEFAULT_EMAIL_ON_SELL_EXECUTED);
    private final JCheckBox snapshotsBox = new JCheckBox("Email a portfolio snapshot on US trading days");
    private final JPanel slotsPanel = new JPanel();
    private final List<SlotRow> rows = new ArrayList<>();
    private final JButton addTimeButton = new JButton("Add a Time");
    private final JButton restoreDefaultsButton = new JButton("Restore Default Times");
    private final JButton sendNowButton = new JButton("Send a Snapshot Now");
    private final JLabel statusLabel = new JLabel(" ");
    private Consumer<String> sendNowHandler;
    private JTextField userEmailField;

    public CommunicationSettingsPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        buildUi();
        populatePortfolioEmail(PortfolioEmailSettings.defaults());
        refreshRecipient();
    }

    /** Shows the User Email as the one address, updating as it is typed. */
    public void followRecipient(JTextField userEmail) {
        this.userEmailField = userEmail;
        userEmail.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refreshRecipient();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refreshRecipient();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refreshRecipient();
            }
        });
        refreshRecipient();
    }

    public JCheckBox buyExpectedCheckBox() {
        return buyExpectedBox;
    }

    public JCheckBox sellExecutedCheckBox() {
        return sellExecutedBox;
    }

    public PortfolioEmailSettings portfolioEmailSettings() {
        return new PortfolioEmailSettings(snapshotsBox.isSelected(), rows.stream().map(SlotRow::slot).toList());
    }

    public void populatePortfolioEmail(PortfolioEmailSettings settings) {
        PortfolioEmailSettings safe = settings == null ? PortfolioEmailSettings.defaults() : settings;
        snapshotsBox.setSelected(safe.enabled());
        rows.clear();
        safe.slots().forEach(slot -> rows.add(new SlotRow(slot)));
        rebuildSlots();
        statusLabel.setText(" ");
    }

    /** Null when the settings can be saved; otherwise what to fix. */
    public String validationError() {
        for (SlotRow row : rows) {
            if (row.parsedTime().isEmpty()) {
                return "\"" + row.timeText() + "\" is not a time. Enter snapshot times as HH:mm on a 24-hour US Eastern clock,"
                        + " for example 09:25 or 15:55.";
            }
        }
        return null;
    }

    /** Shows "Send a Snapshot Now", which hands the User Email on screen (saved or not) to the handler. */
    public void setSendNowHandler(Consumer<String> handler) {
        this.sendNowHandler = handler;
        sendNowButton.setVisible(handler != null);
    }

    String recipientText() {
        return recipientLabel.getText();
    }

    JCheckBox snapshotsCheckBox() {
        return snapshotsBox;
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
        for (JCheckBox box : List.of(buyExpectedBox, sellExecutedBox, snapshotsBox)) {
            box.setOpaque(false);
        }
        snapshotsBox.addActionListener(e -> updateControlState());
        slotsPanel.setOpaque(false);
        slotsPanel.setLayout(new BoxLayout(slotsPanel, BoxLayout.Y_AXIS));
        addTimeButton.addActionListener(e -> addTime(nextFreeTime()));
        restoreDefaultsButton.addActionListener(e -> restoreDefaultTimes());
        sendNowButton.addActionListener(e -> sendNow());
        sendNowButton.setVisible(false);
        DialogButtonStyles.apply(sendNowButton, "icons/verify.svg");
        sendNowButton.setToolTipText(TooltipStyler.text(
                "Email a snapshot right away to your User Email, to check the emails arrive and how they look."));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.setOpaque(false);
        actions.add(addTimeButton);
        actions.add(restoreDefaultsButton);
        actions.add(sendNowButton);
        statusLabel.setFont(FontLoader.ui(Font.PLAIN, 11f));

        addFormRow(0, "Emails go to:", column(recipientLabel, recipientHint));
        addFormRow(1, "Trade alerts:", column(buyExpectedBox, sellExecutedBox, muted(TRADE_ALERTS_DESCRIPTION)));
        addFormRow(2, "Portfolio snapshots:",
                column(snapshotsBox, slotsPanel, actions, statusLabel, muted(SNAPSHOTS_DESCRIPTION)));
    }

    private void refreshRecipient() {
        String email = currentRecipient();
        recipientLabel.setFont(FontLoader.ui(Font.BOLD, 12f));
        if (email.isBlank()) {
            recipientLabel.setText("No email address yet");
            recipientLabel.setForeground(WARN);
            setMutedText(recipientHint, "Add your User Email above to receive trade alerts and portfolio snapshots.");
        } else {
            recipientLabel.setText(email);
            recipientLabel.setForeground(TEXT_PRIMARY);
            setMutedText(recipientHint, "Trade alerts and portfolio snapshots all go to your User Email above.");
        }
    }

    private String currentRecipient() {
        return userEmailField == null ? "" : userEmailField.getText().trim();
    }

    private void rebuildSlots() {
        slotsPanel.removeAll();
        rows.forEach(row -> slotsPanel.add(row.component));
        updateControlState();
        slotsPanel.revalidate();
        slotsPanel.repaint();
    }

    private void updateControlState() {
        boolean on = snapshotsBox.isSelected();
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
        String email = currentRecipient();
        if (email.isBlank()) {
            markStatus(false, "Add your User Email above first, so the snapshot has somewhere to go.");
            return;
        }
        if (sendNowHandler != null) {
            sendNowHandler.accept(email);
            markStatus(true, "Snapshot requested for " + email + ". It should arrive within a minute or two; the Event Log"
                    + " shows whether it was sent.");
        }
    }

    private void markStatus(boolean ok, String message) {
        statusLabel.setText("<html><div style='width:360px;'>" + escape(message) + "</div></html>");
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
        labelGbc.insets = new Insets(4, 0, FIELD_GAP + 4, FIELD_GAP);
        GridBagConstraints valueGbc = new GridBagConstraints();
        valueGbc.gridx = 1;
        valueGbc.gridy = row;
        valueGbc.weightx = 1;
        valueGbc.fill = GridBagConstraints.HORIZONTAL;
        valueGbc.anchor = GridBagConstraints.NORTHWEST;
        valueGbc.insets = new Insets(0, 0, FIELD_GAP + 4, 0);
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
        JLabel label = new JLabel();
        label.putClientProperty(MUTED_PROPERTY, Boolean.TRUE);
        label.setForeground(TEXT_MUTED);
        label.setFont(FontLoader.ui(Font.PLAIN, 11f));
        setMutedText(label, text);
        return label;
    }

    private static void setMutedText(JLabel label, String text) {
        label.setText("<html><div style='width:360px;'>" + escape(text) + "</div></html>");
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
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
