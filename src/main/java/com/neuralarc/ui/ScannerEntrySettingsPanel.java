package com.neuralarc.ui;

import com.neuralarc.model.ScannerEntrySettings;
import com.neuralarc.util.FontLoader;
import com.neuralarc.util.ThemeColors;

import javax.swing.BorderFactory;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;

/**
 * Where every scanner places its entry: the lower of the 2-week low and the current low, a percentage
 * above a chosen period's low, or a price predicted from the 2-week to 1-year trends. Laid out like the
 * other settings sections.
 */
public class ScannerEntrySettingsPanel extends JPanel {
    private static final int FIELD_GAP = 10;
    private static final int SECTION_INNER_PADDING = 10;
    private static final int FORM_LABEL_COLUMN_WIDTH = 280;
    private static final Color TEXT_PRIMARY = UIManager.getColor("Label.foreground") != null
            ? UIManager.getColor("Label.foreground")
            : new Color(45, 45, 50);
    private static final Color TEXT_MUTED = UIManager.getColor("Label.disabledForeground") != null
            ? UIManager.getColor("Label.disabledForeground")
            : new Color(130, 130, 130);
    private static final Color INPUT_BG = UIManager.getColor("TextField.background") != null
            ? UIManager.getColor("TextField.background")
            : Color.WHITE;
    private static final Color INPUT_BORDER = ThemeColors.color("NeuralArc.Input.border", new Color(190, 190, 200));

    private final JComboBox<ScannerEntrySettings.Mode> modeBox = new JComboBox<>(ScannerEntrySettings.Mode.values());
    private final JComboBox<ScannerEntrySettings.Anchor> anchorBox = new JComboBox<>(ScannerEntrySettings.Anchor.values());
    private final JTextField percentField = new JTextField(6);

    public ScannerEntrySettingsPanel() {
        super(new GridBagLayout());
        setOpaque(false);
        setBorder(createSectionBorder("Scanner Entry Level"));
        buildUi();
        applyThemeRecursively(this);
        populate(ScannerEntrySettings.defaults());
    }

    public ScannerEntrySettings settings() {
        return new ScannerEntrySettings(
                (ScannerEntrySettings.Mode) modeBox.getSelectedItem(),
                (ScannerEntrySettings.Anchor) anchorBox.getSelectedItem(),
                parsePercent(percentField.getText())
        );
    }

    public void populate(ScannerEntrySettings settings) {
        ScannerEntrySettings safe = settings == null ? ScannerEntrySettings.defaults() : settings;
        modeBox.setSelectedItem(safe.mode());
        anchorBox.setSelectedItem(safe.anchor());
        percentField.setText(safe.percentAboveLow().stripTrailingZeros().toPlainString());
        updateControlState();
    }

    private void buildUi() {
        addFormRow(0, "Enter scanner buys at:", modeBox);
        addFormRow(1, "", mutedDescription(
                "Applies to every scanner: Gap Rocket, VWAP, Swing Vault, Dip Hunter, ORB, Earnings Hunter, "
                        + "Profit Shield and Range Rider. The entry is never above the market, and a scanner's own "
                        + "deeper entry is never raised."));
        addFormRow(2, "Period low:", anchorBox);
        addFormRow(3, "Percent above the low:", percentField);
        addFormRow(4, "", mutedDescription(
                "Custom: the entry is the chosen period's low plus this percentage (0 to "
                        + ScannerEntrySettings.MAX_PERCENT_ABOVE_LOW.toPlainString() + "). 0 places it exactly at "
                        + "the low; 5 places it 5% above. A higher figure fills more often, a lower one waits for a "
                        + "deeper dip."));
        addFormRow(5, "", mutedDescription(
                "Last 2 weeks low or current low (default): the lower of the last 10 sessions' low and the lowest "
                        + "of today's open, low and current price."));
        addFormRow(6, "", mutedDescription(
                "Automatic: reads the trend over 2 weeks, 2 months, 6 months and 1 year, then places the entry at "
                        + "the price it expects a dip to reach. A rising stock is bought on shallow dips (never "
                        + "under its 2-week low); a falling one is bought lower, anticipating more slide (never "
                        + "under its lowest low in the period read)."));
        modeBox.addActionListener(event -> updateControlState());
    }

    private void updateControlState() {
        boolean custom = modeBox.getSelectedItem() == ScannerEntrySettings.Mode.CUSTOM_PERCENT;
        anchorBox.setEnabled(custom);
        percentField.setEnabled(custom);
        percentField.setForeground(custom ? TEXT_PRIMARY : TEXT_MUTED);
    }

    private BigDecimal parsePercent(String value) {
        try {
            return new BigDecimal(value == null ? "" : value.trim());
        } catch (NumberFormatException ignored) {
            return BigDecimal.ZERO;
        }
    }

    private void addFormRow(int row, String labelText, Component valueComponent) {
        JLabel label = new JLabel(labelText);
        label.setPreferredSize(new Dimension(FORM_LABEL_COLUMN_WIDTH, label.getPreferredSize().height));
        GridBagConstraints labelGbc = new GridBagConstraints();
        labelGbc.gridx = 0;
        labelGbc.gridy = row;
        labelGbc.anchor = GridBagConstraints.NORTHWEST;
        labelGbc.insets = new Insets(0, 0, FIELD_GAP, FIELD_GAP);

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

    private JLabel mutedDescription(String text) {
        JLabel label = new JLabel("<html><div style='width:360px;'>" + text + "</div></html>");
        label.setForeground(TEXT_MUTED);
        label.setFont(FontLoader.ui(Font.PLAIN, 10f));
        return label;
    }

    private void applyThemeRecursively(Component component) {
        component.setFont(FontLoader.ui(component.getFont().getStyle(), component.getFont().getSize2D()));
        if (component instanceof JTextField input) {
            styleInput(input);
        }
        if (component instanceof JComboBox<?> combo) {
            styleCombo(combo);
        }
        if (component instanceof JCheckBox checkBox) {
            checkBox.setOpaque(false);
            checkBox.setForeground(TEXT_PRIMARY);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                applyThemeRecursively(child);
            }
        }
    }

    private void styleCombo(JComboBox<?> combo) {
        combo.setBackground(INPUT_BG);
        combo.setForeground(TEXT_PRIMARY);
    }

    private void styleInput(JTextField input) {
        input.setBackground(INPUT_BG);
        input.setForeground(TEXT_PRIMARY);
        input.setCaretColor(TEXT_PRIMARY);
        input.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                new EmptyBorder(4, 8, 4, 8)
        ));
    }

    private void setEnabledWithStyle(JTextField input, boolean enabled) {
        input.setEnabled(enabled);
        input.setForeground(enabled ? TEXT_PRIMARY : TEXT_MUTED);
    }

    private Border createSectionBorder(String title) {
        TitledBorder border = new TitledBorder(title);
        border.setTitleFont(FontLoader.ui(Font.BOLD, 12f));
        border.setTitleColor(ThemeColors.color("NeuralArc.Section.titleForeground", TEXT_PRIMARY));
        return BorderFactory.createCompoundBorder(border, new EmptyBorder(
                SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING, SECTION_INNER_PADDING
        ));
    }
}
