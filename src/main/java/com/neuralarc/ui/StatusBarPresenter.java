package com.neuralarc.ui;

public final class StatusBarPresenter {
    public StatusBarViewModel present(StatusBarState state) {
        String pollingText = "Poll: -";
        Tone pollingTone = Tone.DEFAULT;
        if (state.pollCycleEvaluated()) {
            if (state.pollMarketClosedSuppressed()) {
                pollingText = "Poll: Market Closed";
                pollingTone = Tone.MUTED;
            } else {
                pollingText = "Poll: due " + state.pollDue() + " | skipped " + state.pollSkippedNotDue();
                if (state.pollDue() > 0) {
                    pollingTone = Tone.OK;
                } else if (state.pollSkippedNotDue() > 0) {
                    pollingTone = Tone.WARN;
                }
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
                "Strategies: Active " + state.runningStrategies() + " | Inactive " + state.inactiveStrategies(),
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

