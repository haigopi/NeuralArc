package com.neuralarc.ui;

public final class StatusBarPresenter {
    public StatusBarViewModel present(StatusBarState state) {
        String pollingText = "Monitoring: Ready";
        Tone pollingTone = Tone.DEFAULT;
        if (state.pollCycleEvaluated()) {
            if (state.pollMarketClosedSuppressed()) {
                pollingText = "Monitoring: Paused for market close";
                pollingTone = Tone.MUTED;
            } else {
                pollingText = "Monitoring: Active";
                pollingTone = Tone.OK;
            }
        }

        String brokerText;
        Tone brokerTone;
        if (state.connectionRetryPending()) {
            brokerText = "<html>Broker: <b>FAILED</b> Retrying...</html>";
            brokerTone = Tone.ERR;
        } else if (!state.connectionOk()) {
            brokerText = "Broker: Not connected";
            brokerTone = Tone.ERR;
        } else if (state.runningStrategies() > 0) {
            brokerText = "Broker: Connected";
            brokerTone = Tone.OK;
        } else if (state.inactiveStrategies() > 0) {
            brokerText = "Broker: Connected (No active strategies)";
            brokerTone = Tone.WARN;
        } else {
            brokerText = "Broker: Connected (No strategies)";
            brokerTone = Tone.WARN;
        }

        return new StatusBarViewModel(
                "Records: Strategies " + (state.runningStrategies() + state.inactiveStrategies())
                        + " (Active " + state.runningStrategies()
                        + ", Inactive " + state.inactiveStrategies()
                        + ") | Trade History " + state.historyRows(),
                pollingText,
                pollingTone,
                state.marketLabel(),
                state.marketTooltip(),
                state.marketOpenForUi() ? Tone.OK : Tone.WARN,
                state.marketValueText(),
                state.cpuText(),
                state.memoryText(),
                brokerText,
                brokerTone
        );
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
            String cpuText,
            String memoryText
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
            String cpuText,
            String memoryText,
            String brokerText,
            Tone brokerTone
    ) {
    }
}
