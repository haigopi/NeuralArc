package com.neuralarc.ui;

/** The names a sell run reports under, so a sell that is not a fixed {@code Scope} can still log and report. */
record SellReport(String menuLabel, String dialogTitle, String logPrefix) {
    static SellReport of(PortfolioActionsSupport.Scope scope) {
        return new SellReport(scope.menuLabel(), scope.dialogTitle(), scope.logPrefix());
    }

    static SellReport named(String label) {
        return new SellReport(label, label, "[" + label + "]");
    }
}
