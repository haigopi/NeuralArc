package com.neuralarc.swing;

public enum SwingStatus {
    RECOMMENDED("Recommended"), WATCHING_SETUP("Watching Setup"), WAITING_FOR_ENTRY("Waiting for Entry"),
    READY_TO_BUY("Ready to Buy"), HOLDING("Holding"), REJECTED("Rejected"), CLOSED("Closed");
    private final String label;
    SwingStatus(String label) { this.label = label; }
    public String label() { return label; }
}
