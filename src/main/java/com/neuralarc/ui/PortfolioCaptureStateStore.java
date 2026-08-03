package com.neuralarc.ui;

import com.neuralarc.util.Monetary;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.StrategyMode;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

final class PortfolioCaptureStateStore {
    private final Path stateFile;

    PortfolioCaptureStateStore(Path stateFile) {
        this.stateFile = stateFile;
    }

    Optional<State> load() {
        if (!Files.exists(stateFile)) {
            return Optional.empty();
        }
        try {
            JSONObject json = new JSONObject(Files.readString(stateFile, StandardCharsets.UTF_8));
            boolean enabled = json.optBoolean("enabled", false);
            PortfolioCaptureTargetType targetType = PortfolioCaptureTargetType.valueOf(
                    json.optString("targetType", PortfolioCaptureTargetType.PROFIT_AMOUNT.name()));
            PortfolioCaptureConfig config = new PortfolioCaptureConfig(
                    PortfolioCaptureMode.valueOf(json.optString(
                            "captureMode", PortfolioCaptureMode.TARGET_MONITORING.name())),
                    targetType,
                    new BigDecimal(json.optString("targetValue", "0")),
                    json.optBoolean("includeLosses", true),
                    Math.max(1, json.optInt("monitoringIntervalSeconds", 45)),
                    json.optBoolean("autoStopAfterExecution", true),
                    json.optBoolean("includeOnlyActiveStrategies", true),
                    PortfolioCaptureExecutionFlow.valueOf(json.optString(
                            "executionFlow",
                            PortfolioCaptureExecutionFlow.EXECUTE_ONCE_AND_STOP.name())),
                    StrategyMode.valueOf(json.optString("reentryMode", StrategyMode.PAPER.name())),
                    Math.max(1, json.optInt("reentryQuantity", 1)),
                    RecommendationType.valueOf(json.optString("reentryRecommendationType", RecommendationType.SHORT_TERM.name())),
                    PortfolioCaptureSmartPicksStrategy.valueOf(json.optString(
                            "reentrySmartPicksStrategy",
                            // Legacy key from before the Smart Picks rename.
                            json.optString(
                                    "reentryLuckyStrategy",
                                    PortfolioCaptureSmartPicksStrategy.VOLATILE.name()))),
                    json.optBoolean("autoCleanPendingBeforeCycle", false),
                    PortfolioCapturePullbackType.valueOf(json.optString(
                            "pullbackType", PortfolioCapturePullbackType.PERCENT_FROM_PEAK.name())),
                    new BigDecimal(json.optString("pullbackValue", "0"))
            );
            Instant lastTimestamp = parseInstant(json.optString("lastMonitoringTimestamp", ""));
            BigDecimal lastValue = new BigDecimal(json.optString("lastCalculatedPortfolioValue", "0"));
            StrategyMode mode = StrategyMode.valueOf(json.optString("mode", StrategyMode.PAPER.name()));
            String workspaceId = json.optString("workspaceId", "");
            return Optional.of(new State(enabled, config, lastTimestamp, Monetary.round(lastValue),
                    mode, workspaceId.isBlank() ? null : workspaceId,
                    json.optBoolean("pullbackArmed", false),
                    new BigDecimal(json.optString("peakProfit", "0"))));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    void save(State state) {
        try {
            Files.createDirectories(stateFile.getParent());
            JSONObject json = new JSONObject();
            json.put("enabled", state.enabled());
            json.put("captureMode", state.config().mode().name());
            json.put("targetType", state.config().targetType().name());
            json.put("targetValue", state.config().targetValue());
            json.put("includeLosses", state.config().includeLosses());
            json.put("monitoringIntervalSeconds", state.config().monitoringIntervalSeconds());
            json.put("autoStopAfterExecution", state.config().autoStopAfterExecution());
            json.put("includeOnlyActiveStrategies", state.config().includeOnlyActiveStrategies());
            json.put("executionFlow", state.config().executionFlow().name());
            json.put("reentryMode", state.config().reentryMode().name());
            json.put("reentryQuantity", state.config().reentryQuantity());
            json.put("reentryRecommendationType", state.config().reentryRecommendationType().name());
            json.put("reentrySmartPicksStrategy", state.config().reentrySmartPicksStrategy().name());
            json.put("autoCleanPendingBeforeCycle", state.config().autoCleanPendingBeforeCycle());
            json.put("pullbackType", state.config().pullbackType().name());
            json.put("pullbackValue", state.config().pullbackValue());
            json.put("pullbackArmed", state.pullbackArmed());
            json.put("peakProfit", state.peakProfit());
            json.put("lastMonitoringTimestamp", state.lastMonitoringTimestamp() == null ? "" : state.lastMonitoringTimestamp().toString());
            json.put("lastCalculatedPortfolioValue", state.lastCalculatedPortfolioValue());
            json.put("mode", state.mode() == null ? StrategyMode.PAPER.name() : state.mode().name());
            json.put("workspaceId", state.workspaceId() == null ? "" : state.workspaceId());
            Files.writeString(stateFile, json.toString(2), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // Monitoring state is recoverability metadata. Logging is handled by the caller.
        }
    }

    void clear() {
        save(new State(false, PortfolioCaptureConfig.captureNow(), null, Monetary.zero(), StrategyMode.PAPER, null,
                false, Monetary.zero()));
    }

    private Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Instant.parse(value);
    }

    record State(
            boolean enabled,
            PortfolioCaptureConfig config,
            Instant lastMonitoringTimestamp,
            BigDecimal lastCalculatedPortfolioValue,
            StrategyMode mode,
            String workspaceId,
            boolean pullbackArmed,
            BigDecimal peakProfit
    ) {
        State(
                boolean enabled,
                PortfolioCaptureConfig config,
                Instant lastMonitoringTimestamp,
                BigDecimal lastCalculatedPortfolioValue,
                StrategyMode mode,
                String workspaceId
        ) {
            this(enabled, config, lastMonitoringTimestamp, lastCalculatedPortfolioValue, mode, workspaceId,
                    false, Monetary.zero());
        }
    }
}
