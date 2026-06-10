package com.neuralarc.ui;

enum PortfolioCaptureSmartPicksStrategy {
    VOLATILE("High Volatility Movers"),
    DIVERSIFIED_TOP_20("Diversified Leaders (Top 20)");

    private final String label;

    PortfolioCaptureSmartPicksStrategy(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
