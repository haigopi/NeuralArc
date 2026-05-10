package com.neuralarc.ui;

import com.neuralarc.model.AiRecommendationRequest;
import com.neuralarc.model.AiRecommendationResponse;
import com.neuralarc.model.AiRecommendationSettings;
import com.neuralarc.model.AiSourceAnalyzed;
import com.neuralarc.model.AiProviderHealthStatus;
import com.neuralarc.service.AiRecommendationException;
import com.neuralarc.service.AiRecommendationProvider;
import com.neuralarc.service.AiRecommendationProviderFactory;
import com.neuralarc.service.AiRecommendationService;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.util.FontLoader;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Dimension;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AiRecommendationPanel extends JPanel {
    private static final Logger LOGGER = Logger.getLogger(AiRecommendationPanel.class.getName());
    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final Color TEXT_MUTED = new Color(130, 130, 130);
    private static final Color INPUT_BORDER = new Color(190, 190, 200);
    private static final int PANEL_WIDTH = 760;

    private final Supplier<AiRecommendationRequest> requestSupplier;
    private final AppSettingsService settingsService;
    private final AiRecommendationService recommendationService;
    private final JButton requestButton = new JButton("Get AI Recommendation");
    private final JLabel statusLabel = new JLabel("Run Auto Analyze, then request an AI recommendation.");
    private final JLabel recommendationLabel = new JLabel("-");
    private final JLabel confidenceLabel = new JLabel("-");
    private final JLabel providerLabel = new JLabel("-");
    private final JLabel generatedAtLabel = new JLabel("-");
    private final JTextArea summaryArea = readOnlyArea(3);
    private final JTextArea reasonsArea = readOnlyArea(4);
    private final JTextArea risksArea = readOnlyArea(4);
    private final JTextArea sourcesArea = readOnlyArea(4);

    public AiRecommendationPanel(Component parent, Supplier<AiRecommendationRequest> requestSupplier) {
        this(parent, requestSupplier, new AppSettingsService(), new AiRecommendationService());
    }

    AiRecommendationPanel(
            Component parent,
            Supplier<AiRecommendationRequest> requestSupplier,
            AppSettingsService settingsService,
            AiRecommendationService recommendationService
    ) {
        super(new BorderLayout(0, 10));
        this.requestSupplier = Objects.requireNonNull(requestSupplier);
        this.settingsService = Objects.requireNonNull(settingsService);
        this.recommendationService = Objects.requireNonNull(recommendationService);
        setOpaque(false);
        setAlignmentX(Component.LEFT_ALIGNMENT);
        setMaximumSize(new Dimension(PANEL_WIDTH, Short.MAX_VALUE));
        setBorder(createBorder());
        buildUi();
    }

    private void buildUi() {
        JLabel description = new JLabel("<html><div style='width:640px;'>"
                + "Sends the current stock analysis to the selected AI provider. The provider analyzes recent web articles, "
                + "news, earnings commentary, analyst notes, and sentiment, then returns decision support only."
                + "</div></html>");
        description.setForeground(TEXT_MUTED);
        description.setFont(FontLoader.ui(Font.PLAIN, 11f));
        description.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        header.setOpaque(false);
        header.setAlignmentX(Component.LEFT_ALIGNMENT);
        DialogButtonStyles.apply(requestButton, "icons/actions.svg");
        requestButton.addActionListener(e -> requestRecommendation());
        requestButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel buttonRow = new JPanel(new BorderLayout());
        buttonRow.setOpaque(false);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        buttonRow.add(requestButton, BorderLayout.WEST);
        header.add(description);
        header.add(Box.createVerticalStrut(8));
        header.add(buttonRow);
        add(header, BorderLayout.NORTH);

        JPanel details = new JPanel(new GridBagLayout());
        details.setOpaque(false);
        details.setAlignmentX(Component.LEFT_ALIGNMENT);
        int row = 0;
        addRow(details, row++, "Recommendation:", recommendationLabel);
        addRow(details, row++, "Confidence:", confidenceLabel);
        addRow(details, row++, "Provider:", providerLabel);
        addRow(details, row++, "Generated:", generatedAtLabel);
        addTextRow(details, row++, "Summary:", summaryArea);
        addTextRow(details, row++, "Key reasons:", reasonsArea);
        addTextRow(details, row++, "Risks:", risksArea);
        addTextRow(details, row, "Sources:", sourcesArea);
        add(details, BorderLayout.CENTER);

        statusLabel.setForeground(TEXT_MUTED);
        statusLabel.setFont(FontLoader.ui(Font.PLAIN, 10f));
        add(statusLabel, BorderLayout.SOUTH);
    }

    private void requestRecommendation() {
        AiRecommendationRequest request = requestSupplier.get();
        if (request == null || request.symbol().isBlank()) {
            setStatus("Run Auto Analyze first so current stock analysis is available.", false);
            return;
        }
        AiRecommendationSettings settings = settingsService.loadAiRecommendationSettings();
        AiRecommendationProvider provider = AiRecommendationProviderFactory.create(settings);
        requestButton.setEnabled(false);
        setStatus("AI recommendation request started: " + provider.getProviderType() + " / " + request.symbol(), true);
        SwingWorker<AiRecommendationResponse, Void> worker = new SwingWorker<>() {
            @Override
            protected AiRecommendationResponse doInBackground() throws Exception {
                LOGGER.info(() -> "AI recommendation UI request started: provider=" + provider.getProviderType()
                        + " symbol=" + request.symbol());
                AiProviderHealthStatus health = provider.healthCheck();
                LOGGER.info(() -> "AI provider health check: provider=" + provider.getProviderType()
                        + " healthy=" + health.healthy() + " status=" + health.statusText());
                if (!health.healthy()) {
                    throw new AiRecommendationException(health.statusText());
                }
                return recommendationService.requestRecommendation(provider, request);
            }

            @Override
            protected void done() {
                try {
                    AiRecommendationResponse response = get();
                    display(response);
                    setStatus("AI recommendation received for " + response.symbol() + ".", true);
                } catch (Exception ex) {
                    LOGGER.log(Level.WARNING, "AI recommendation request failed", ex);
                    setStatus("AI recommendation failed: " + userMessage(ex), false);
                } finally {
                    requestButton.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    private void display(AiRecommendationResponse response) {
        recommendationLabel.setText(response.recommendation().name());
        confidenceLabel.setText(Math.round(response.confidence() * 100.0d) + "%");
        providerLabel.setText(response.provider().name());
        generatedAtLabel.setText(response.generatedAt() == null ? "-" : DISPLAY_FMT.format(response.generatedAt()));
        summaryArea.setText(response.summary());
        reasonsArea.setText(joinBullets(response.keyReasons()));
        risksArea.setText(joinBullets(response.risks()));
        sourcesArea.setText(formatSources(response.sourcesAnalyzed()));
        recommendationLabel.setForeground(colorFor(response.recommendation().name()));
    }

    private Color colorFor(String recommendation) {
        return switch (recommendation) {
            case "BUY", "HOLD" -> new Color(22, 110, 62);
            case "SELL", "AVOID" -> new Color(166, 45, 45);
            default -> new Color(154, 92, 13);
        };
    }

    private String userMessage(Exception ex) {
        Throwable cause = ex.getCause() == null ? ex : ex.getCause();
        return cause.getMessage() == null || cause.getMessage().isBlank()
                ? "Provider is unreachable or returned an invalid response."
                : cause.getMessage();
    }

    private void setStatus(String message, boolean neutral) {
        Runnable update = () -> {
            statusLabel.setText(message);
            statusLabel.setForeground(neutral ? TEXT_MUTED : new Color(180, 30, 30));
        };
        if (SwingUtilities.isEventDispatchThread()) {
            update.run();
        } else {
            SwingUtilities.invokeLater(update);
        }
    }

    private void addRow(JPanel panel, int row, String label, JLabel value) {
        JLabel rowLabel = new JLabel(label);
        rowLabel.setFont(FontLoader.ui(Font.PLAIN, 11f));
        value.setFont(FontLoader.ui(Font.BOLD, 11f));
        GridBagConstraints labelGbc = baseConstraints(row, 0);
        labelGbc.weightx = 0;
        panel.add(rowLabel, labelGbc);
        GridBagConstraints valueGbc = baseConstraints(row, 1);
        valueGbc.weightx = 1;
        valueGbc.fill = GridBagConstraints.HORIZONTAL;
        panel.add(value, valueGbc);
    }

    private void addTextRow(JPanel panel, int row, String label, JTextArea area) {
        JLabel rowLabel = new JLabel(label);
        rowLabel.setFont(FontLoader.ui(Font.PLAIN, 11f));
        GridBagConstraints labelGbc = baseConstraints(row, 0);
        panel.add(rowLabel, labelGbc);
        JScrollPane scrollPane = new JScrollPane(area);
        scrollPane.setBorder(BorderFactory.createLineBorder(INPUT_BORDER, 1, true));
        GridBagConstraints valueGbc = baseConstraints(row, 1);
        valueGbc.weightx = 1;
        valueGbc.fill = GridBagConstraints.BOTH;
        panel.add(scrollPane, valueGbc);
    }

    private GridBagConstraints baseConstraints(int row, int column) {
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = column;
        gbc.gridy = row;
        gbc.anchor = GridBagConstraints.NORTHWEST;
        gbc.insets = new Insets(0, 0, 8, column == 0 ? 10 : 0);
        return gbc;
    }

    private String joinBullets(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                builder.append("- ").append(value.trim()).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private String formatSources(List<AiSourceAnalyzed> sources) {
        if (sources == null || sources.isEmpty()) {
            return "-";
        }
        StringBuilder builder = new StringBuilder();
        for (AiSourceAnalyzed source : sources) {
            String published = source.publishedAt() == null ? "" : " (" + DISPLAY_FMT.format(source.publishedAt()) + ")";
            builder.append("- ").append(source.title()).append(published).append('\n');
            if (source.url() != null && !source.url().isBlank()) {
                builder.append("  ").append(source.url()).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private static JTextArea readOnlyArea(int rows) {
        JTextArea area = new JTextArea(rows, 40);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setText("-");
        area.setFont(FontLoader.ui(Font.PLAIN, 11f));
        area.setBorder(new EmptyBorder(4, 6, 4, 6));
        return area;
    }

    private TitledBorder createBorder() {
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(INPUT_BORDER, 1, true),
                        new EmptyBorder(10, 10, 10, 10)
                ),
                "AI Recommendation"
        );
        border.setTitleFont(FontLoader.ui(Font.BOLD, 12f));
        return border;
    }
}
