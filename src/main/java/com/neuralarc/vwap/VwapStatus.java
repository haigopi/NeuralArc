package com.neuralarc.vwap;

public enum VwapStatus {
    RECOMMENDED("Recommended"), WATCHING_VWAP("Watching VWAP"), WAITING_FOR_REVERSION("Waiting for Reversion"),
    READY_TO_BUY("Ready to Buy"), BOUGHT("Bought"), REJECTED("Rejected"), CLOSED("Closed");
    private final String label;
    VwapStatus(String label) { this.label = label; }
    public String label() { return label; }
}
