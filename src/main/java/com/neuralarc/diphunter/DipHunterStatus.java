package com.neuralarc.diphunter;

public enum DipHunterStatus {
    RECOMMENDED("Recommended"), WATCHING_DIP("Watching Dip"), WAITING_FOR_BOUNCE("Waiting for Bounce"),
    READY_TO_BUY("Ready to Buy"), BOUGHT("Bought"), REJECTED("Rejected"), CLOSED("Closed");
    private final String label;
    DipHunterStatus(String label) { this.label = label; }
    public String label() { return label; }
}
