package com.neuralarc.ui;

public final class StatusBarPresenter {
    public static final String NETWORK_ICON_PATH = "icons/wifi.svg";

    public StatusBarViewModel present(StatusBarState state) {
        String pollingText = "Ready";
        Tone pollingTone = Tone.DEFAULT;
        if (state.pollCycleEvaluated()) {
            if (state.pollMarketClosedSuppressed()) {
                pollingText = "Paused for market close";
                pollingTone = Tone.MUTED;
            } else {
                pollingText = "Active";
                pollingTone = Tone.OK;
            }
        }

        String brokerText;
        Tone brokerTone;
        if (state.connectionRetryPending()) {
            brokerText = "<html><b>FAILED</b> Retrying...</html>";
            brokerTone = Tone.ERR;
        } else if (!state.connectionOk()) {
            brokerText = "Not connected";
            brokerTone = Tone.ERR;
        } else if (state.runningStrategies() > 0) {
            brokerText = "Connected";
            brokerTone = Tone.OK;
        } else if (state.inactiveStrategies() > 0) {
            brokerText = "Connected (No active)";
            brokerTone = Tone.WARN;
        } else {
            brokerText = "Connected (No strategies)";
            brokerTone = Tone.WARN;
        }

        return new StatusBarViewModel(
                "Strategies " + (state.runningStrategies() + state.inactiveStrategies())
                        + "  Active " + state.runningStrategies()
                        + "  Inactive " + state.inactiveStrategies()
                        + "  History " + state.historyRows(),
                pollingText,
                pollingTone,
                removePrefix(state.marketLabel(), "Market:"),
                state.marketTooltip(),
                state.marketOpenForUi() ? Tone.OK : Tone.WARN,
                removePrefix(state.marketValueText(), "Market Value:"),
                removePrefix(state.investedValueText(), "Invested Value:"),
                removePrefix(state.availableFundsText(), "Funds Available:"),
                removePrefix(state.baseBuyPendingText(), "Base Buy Pending Total:"),
                removePrefix(state.cpuText(), "CPU:"),
                removePrefix(state.memoryText(), "Memory:"),
                brokerText,
                brokerTone,
                state.gainingPositionsText(),
                state.losingPositionsText(),
                state.pendingToFillText()
        );
    }

    public NetworkStatusViewModel presentNetworkStatus(boolean online) {
        return new NetworkStatusViewModel(
                NETWORK_ICON_PATH,
                online ? Tone.OK : Tone.ERR,
                online ? "Internet connection available" : "Internet connection unavailable",
                !online
        );
    }

    private String removePrefix(String value, String prefix) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String trimmed = value.trim();
        if (trimmed.regionMatches(true, 0, prefix, 0, prefix.length())) {
            return trimmed.substring(prefix.length()).trim();
        }
        return trimmed;
    }

    public enum Tone {
        DEFAULT,
        MUTED,
        OK,
        WARN,
        ERR
    }

    public record StatusBarState(
            long runningStrategies,
            long inactiveStrategies,
            boolean pollCycleEvaluated,
            boolean pollMarketClosedSuppressed,
            int pollDue,
            int pollSkippedNotDue,
            long historyRows,
            boolean connectionRetryPending,
            boolean connectionOk,
            String marketLabel,
            String marketTooltip,
            boolean marketOpenForUi,
            String marketValueText,
            String investedValueText,
            String availableFundsText,
            String baseBuyPendingText,
            String cpuText,
            String memoryText,
            String gainingPositionsText,
            String losingPositionsText,
            String pendingToFillText
    ) {
    }

    public record StatusBarViewModel(
            String strategyCountText,
            String pollingText,
            Tone pollingTone,
            String marketText,
            String marketTooltip,
            Tone marketTone,
            String marketValueText,
            String investedValueText,
            String availableFundsText,
            String baseBuyPendingText,
            String cpuText,
            String memoryText,
            String brokerText,
            Tone brokerTone,
            String gainingPositionsText,
            String losingPositionsText,
            String pendingToFillText
    ) {
    }

    public record NetworkStatusViewModel(
            String iconPath,
            Tone tone,
            String tooltip,
            boolean blink
    ) {
    }
}
