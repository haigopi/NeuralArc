package com.neuralarc.ui;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatLaf;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.AutoAdjustRiskConfig;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.ProfitControlMode;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.ThresholdType;
import com.neuralarc.model.TimeInForce;
import com.neuralarc.model.TrailingType;
import com.neuralarc.util.FontLoader;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

final class DocsScreenshotGeneratorTest {
    private static final Path OUTPUT_DIR = Path.of(
            System.getProperty("neuralarc.docsOutputDir", "docs/images")
    );

    @Test
    @EnabledIfSystemProperty(named = "generateDocsScreenshots", matches = "true")
    void regenerateDocScreenshots() throws Exception {
        runGenerator();
    }

    public static void main(String[] args) throws Exception {
        runGenerator();
    }

    private static void installTheme() {
        FlatLaf.registerCustomDefaultsSource("themes");
        FlatDarculaLaf.setup();
        FontLoader.installSwingDefaults();
    }

    private static void runGenerator() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless(), "Requires a desktop graphics environment");
        Files.createDirectories(OUTPUT_DIR);

        SwingUtilities.invokeAndWait(() -> {
            installTheme();
            try {
                generateScreenshots();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
        });
    }

    private static void generateScreenshots() throws Exception {
        JFrame owner = new JFrame("Docs Screenshot Owner");
        owner.setUndecorated(true);
        owner.setSize(20, 20);
        owner.setLocation(-10_000, -10_000);
        owner.setVisible(true);

        try {
            SettingsDialog settingsDialog = buildSettingsDialog(owner);
            captureComponent(settingsDialog.getRootPane(), OUTPUT_DIR.resolve("workflow-settings.png"));

            StrategyDialog strategyDialog = buildStrategyDialog(owner);
            captureComponent(strategyDialog.getRootPane(), OUTPUT_DIR.resolve("workflow-strategy-dialog.png"));

            ProfitControlsPanel profitControlsPanel = findDescendant(strategyDialog.getRootPane(), ProfitControlsPanel.class);
            if (profitControlsPanel == null) {
                throw new IllegalStateException("Profit controls panel not found in strategy dialog");
            }
            captureComponent(profitControlsPanel, OUTPUT_DIR.resolve("workflow-profit-controls.png"));

            PortfolioCaptureDialog portfolioDialog = buildPortfolioDialog(owner);
            captureComponent(portfolioDialog.getRootPane(), OUTPUT_DIR.resolve("workflow-portfolio-actions.png"));
        } finally {
            owner.dispose();
        }
    }

    private static SettingsDialog buildSettingsDialog(JFrame owner) throws Exception {
        SettingsDialog dialog = new SettingsDialog(owner);
        dialog.prepareForOpen();
        dialog.selectBrokerAndMode(BrokerType.ALPACA, ApplicationMode.LIVE);
        dialog.markConnectionStatus(true, "Live connection verified");
        setTextField(dialog, "emailField", "operator@neuralarc.local");
        setTextField(dialog, "apiKeyField", "AKIA-NEURALARC-DEMO");
        setPasswordField(dialog, "apiSecretField", "••••••••••••••••");
        setTextField(dialog, "endpointField", "https://analytics.neuralarc.app/v1/events");
        dialog.setSize(dialog.getPreferredSize());
        layoutRecursively(dialog.getRootPane());
        return dialog;
    }

    private static StrategyDialog buildStrategyDialog(JFrame owner) {
        StrategyConfig config = new StrategyConfig(
                "NIO",
                new BigDecimal("4.69"),
                10,
                true,
                new BigDecimal("4.25"),
                false,
                BigDecimal.ZERO,
                new BigDecimal("4.38"),
                10,
                new BigDecimal("4.05"),
                20,
                true,
                false,
                BigDecimal.ZERO,
                60,
                false,
                false,
                true,
                ProfitHoldType.PERCENT_TRAILING,
                new BigDecimal("2.50"),
                BigDecimal.ZERO,
                true,
                ProfitControlMode.PROFIT_HOLD,
                ThresholdType.FIXED_AMOUNT,
                new BigDecimal("0.50"),
                TrailingType.PERCENTAGE,
                new BigDecimal("1.75"),
                true,
                new BigDecimal("2.00"),
                TimeInForce.DAY,
                new AutoAdjustRiskConfig(true, 5, new BigDecimal("1.25"), true, true, true)
        );
        StrategyDialog dialog = new StrategyDialog(owner, config, null, null, 60, true, true);
        dialog.setSize(dialog.getPreferredSize());
        layoutRecursively(dialog.getRootPane());
        return dialog;
    }

    private static PortfolioCaptureDialog buildPortfolioDialog(JFrame owner) throws Exception {
        PortfolioCaptureDialog dialog = new PortfolioCaptureDialog(
                owner,
                config -> new PortfolioCaptureSnapshot(
                        new BigDecimal("6071.12"),
                        new BigDecimal("5890.37"),
                        new BigDecimal("-180.75"),
                        new BigDecimal("-2.98"),
                        BigDecimal.ZERO,
                        13,
                        List.of(
                                new PortfolioCaptureSnapshot.Row("nio-1", "NIO", 10, new BigDecimal("4.69"),
                                        new BigDecimal("4.75"), new BigDecimal("46.90"), new BigDecimal("47.50"), new BigDecimal("0.60")),
                                new PortfolioCaptureSnapshot.Row("tsla-1", "TSLA", 2, new BigDecimal("408.02"),
                                        new BigDecimal("393.58"), new BigDecimal("816.04"), new BigDecimal("787.16"), new BigDecimal("-28.88")),
                                new PortfolioCaptureSnapshot.Row("v-1", "V", 2, new BigDecimal("313.45"),
                                        new BigDecimal("359.53"), new BigDecimal("626.90"), new BigDecimal("719.06"), new BigDecimal("92.16"))
                        ),
                        Instant.now()
                ),
                config -> { },
                config -> { },
                () -> { },
                true
        );

        selectRadio(dialog, "captureTarget");
        selectRadio(dialog, "percentTarget");
        setTextField(dialog, "percentField", "4.5");
        setTextField(dialog, "intervalField", "45");
        selectRadio(dialog, "continuousLoop");
        selectRadio(dialog, "liveMode");
        JCheckBox acknowledgement = getField(dialog, "acknowledgement", JCheckBox.class);
        acknowledgement.setSelected(true);
        invokePrivate(dialog, "updateEnabledState");
        dialog.setSize(dialog.getPreferredSize());
        layoutRecursively(dialog.getRootPane());
        return dialog;
    }

    private static void captureComponent(JComponent component, Path output) throws IOException {
        Dimension size = component.getSize();
        if (size.width <= 0 || size.height <= 0) {
            size = component.getPreferredSize();
            component.setSize(size);
        }
        layoutRecursively(component);

        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setColor(component.getBackground() == null ? Color.BLACK : component.getBackground());
        graphics.fillRect(0, 0, size.width, size.height);
        component.printAll(graphics);
        graphics.dispose();

        ImageIO.write(image, "png", output.toFile());
    }

    private static void layoutRecursively(Component component) {
        component.doLayout();
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                if (child.getWidth() <= 0 || child.getHeight() <= 0) {
                    Dimension preferred = child.getPreferredSize();
                    if (preferred != null && preferred.width > 0 && preferred.height > 0) {
                        child.setSize(preferred);
                    }
                }
                layoutRecursively(child);
            }
        }
        if (component instanceof JScrollPane scrollPane) {
            Component view = scrollPane.getViewport().getView();
            if (view != null) {
                Dimension preferred = view.getPreferredSize();
                if (preferred != null && preferred.width > 0 && preferred.height > 0) {
                    view.setSize(preferred);
                    layoutRecursively(view);
                }
            }
        }
    }

    private static <T> T findDescendant(Component root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof Container container) {
            for (Component child : container.getComponents()) {
                T match = findDescendant(child, type);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static void setTextField(Object target, String fieldName, String value) throws Exception {
        JTextField field = getField(target, fieldName, JTextField.class);
        field.setText(value);
    }

    private static void setPasswordField(Object target, String fieldName, String value) throws Exception {
        JPasswordField field = getField(target, fieldName, JPasswordField.class);
        field.setText(value);
    }

    private static void selectRadio(Object target, String fieldName) throws Exception {
        JRadioButton field = getField(target, fieldName, JRadioButton.class);
        field.setSelected(true);
    }

    private static void invokePrivate(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(target);
    }

    @SuppressWarnings("unchecked")
    private static <T> T getField(Object target, String fieldName, Class<T> type) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        Object value = field.get(target);
        if (!type.isInstance(value)) {
            throw new IllegalStateException(fieldName + " is not a " + type.getSimpleName());
        }
        return (T) value;
    }
}
