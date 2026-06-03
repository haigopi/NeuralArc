package com.neuralarc.ui;

enum PortfolioCaptureLuckyStrategy {
    VOLATILE("Volatile Strategy"),
    DIVERSIFIED_TOP_20("Top 20 Diversified Stocks");

    private final String label;

    PortfolioCaptureLuckyStrategy(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }
}
