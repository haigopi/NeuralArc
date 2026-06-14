package com.neuralarc.gaprocket;

public enum GapRocketStatus {
    RECOMMENDED("Recommended"), WATCHING_OPENING_RANGE("Watching Opening Range"), WAITING_FOR_BREAKOUT("Waiting for Breakout"),
    WAITING_FOR_PULLBACK("Waiting for Pullback"), READY_TO_BUY("Ready to Buy"), BOUGHT("Bought"), REJECTED("Rejected"), CLOSED("Closed");
    private final String label;
    GapRocketStatus(String label) { this.label = label; }
    public String label() { return label; }
}
