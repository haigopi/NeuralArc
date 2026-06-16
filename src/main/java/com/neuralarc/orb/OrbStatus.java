package com.neuralarc.orb;

public enum OrbStatus {
    WAITING_FOR_RANGE("Waiting for range"),
    RANGE_CAPTURED("Range captured"),
    ARMED("Armed"),
    TRIGGERED("Triggered"),
    REJECTED("Rejected"),
    EXPIRED("Expired");

    private final String label;
    OrbStatus(String label) { this.label = label; }
    public String label() { return label; }
}
