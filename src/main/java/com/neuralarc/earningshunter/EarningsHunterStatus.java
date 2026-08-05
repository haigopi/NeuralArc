package com.neuralarc.earningshunter;

public enum EarningsHunterStatus {
    RECOMMENDED("Recommended"), WATCHING_SETUP("Watching Setup"), REJECTED("Rejected"), CLOSED("Closed");
    private final String label;
    EarningsHunterStatus(String label) { this.label = label; }
    public String label() { return label; }
}
