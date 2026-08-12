package com.neuralarc.rangerider;

public enum RangeRiderStatus {
    RECOMMENDED("Recommended"), WATCHING_RANGE("Watching Range"), WAITING_FOR_LOW("Waiting for Low"),
    READY_TO_BUY("Ready to Buy"), BOUGHT("Bought"), REJECTED("Rejected"), CLOSED("Closed");
    private final String label;
    RangeRiderStatus(String label) { this.label = label; }
    public String label() { return label; }
}
