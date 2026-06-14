package com.neuralarc.model;

import java.util.List;

/**
 * A pre-defined strategy-workspace template offered in Smart Picks. Clicking "Create" turns a
 * template into a live {@link StrategyWorkspace} (and its tab) instantly.
 *
 * <p>For v1 the catalog is static in code; persistence/editing of templates is a later concern.
 * {@link #CUSTOM_CODE} marks the "Custom Strategy" entry whose name is prompted from the user.
 */
public record StrategyWorkspaceTemplate(String name, String code, String description) {
    public static final String CUSTOM_CODE = "CUSTOM";

    public boolean isCustom() {
        return CUSTOM_CODE.equals(code);
    }

    /** The Smart Picks strategy-template catalog, in display order. */
    public static List<StrategyWorkspaceTemplate> catalog() {
        return List.of(
                new StrategyWorkspaceTemplate("Gap Rocket", "GAPROCKET",
                        "Scan premarket gap-up stocks, rank the strongest movers, and track opening-range, breakout-retest, or VWAP-pullback setups in a dedicated morning strategy grid."),
                new StrategyWorkspaceTemplate("ORB Engine", "ORB",
                        "Opening-range breakout entries on the first range of the session."),
                new StrategyWorkspaceTemplate("VWAP Desk", "VWAP",
                        "Mean-reversion trades around the volume-weighted average price."),
                // "Momentum Lab" (high relative volume) was folded into Gap Rocket above, which is the
                // dedicated high-relative-volume momentum scanner — avoid offering two overlapping ones.
                new StrategyWorkspaceTemplate("Swing Vault", "SWING",
                        "Multi-day swing positions held across sessions."),
                new StrategyWorkspaceTemplate("Dip Hunter", "DIP",
                        "Buys pullbacks in strong names looking for a bounce."),
                new StrategyWorkspaceTemplate("Profit Shield", "SHIELD",
                        "Defensive book focused on protecting realized gains."),
                new StrategyWorkspaceTemplate("Earnings Hunter", "EARNINGS",
                        "Event-driven trades around earnings announcements."),
                new StrategyWorkspaceTemplate("Manual Trades", "MANUAL",
                        "A home for discretionary, manually managed trades."),
                new StrategyWorkspaceTemplate("Custom Strategy", CUSTOM_CODE,
                        "Create a workspace with your own name.")
        );
    }
}
