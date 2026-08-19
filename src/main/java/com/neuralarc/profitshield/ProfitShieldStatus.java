package com.neuralarc.profitshield;

public enum ProfitShieldStatus {
    RECOMMENDED("Recommended"), WATCHING_SETUP("Watching Setup"), REJECTED("Rejected"), CLOSED("Closed");
    private final String label;
    ProfitShieldStatus(String label) { this.label = label; }
    public String label() { return label; }
}
