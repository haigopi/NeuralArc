package com.neuralarc.model;

import java.util.List;

/**
 * A pre-defined strategy-workspace template offered in Smart Picks. Clicking "Create" turns a
 * template into a live {@link StrategyWorkspace} (and its tab) instantly.
 *
 * <p>For v1 the catalog is static in code; persistence/editing of templates is a later concern.
 * {@link #CUSTOM_CODE} marks the "Custom Strategy" entry whose name is prompted from the user.
 *
 * <p>{@link #implemented()} marks whether the template has a dedicated scanner/analysis engine
 * behind it. Templates that are still placeholders are shown disabled ("coming soon") so the menu
 * advertises the roadmap without letting operators create empty, non-functional workspaces.
 */
public record StrategyWorkspaceTemplate(String name, String code, String description, boolean implemented) {
    public static final String CUSTOM_CODE = "CUSTOM";

    /** Backwards-compatible constructor: templates default to implemented. */
    public StrategyWorkspaceTemplate(String name, String code, String description) {
        this(name, code, description, true);
    }

    public boolean isCustom() {
        return CUSTOM_CODE.equals(code);
    }

    /** The Smart Picks strategy-template catalog, in display order. */
    public static List<StrategyWorkspaceTemplate> catalog() {
        return List.of(
                new StrategyWorkspaceTemplate("Gap Rocket", "GAPROCKET",
                        "Scan premarket gap-up stocks, rank the strongest movers, and track opening-range, breakout-retest, or VWAP-pullback setups in a dedicated morning strategy grid.", true),
                new StrategyWorkspaceTemplate("ORB Engine", "ORB",
                        "Capture the first 5/15/30 minute regular-session range, rank live breakout candidates, and arm planned entries in a dedicated ORB Engine grid.", true),
                new StrategyWorkspaceTemplate("Dip Hunter", "DIP",
                        "Scan strong up-trending names that have pulled back from a recent high, rank the best bounce setups on live data, and track planned entries in a dedicated Dip Hunter grid.", true),
                new StrategyWorkspaceTemplate("VWAP Desk", "VWAP",
                        "Mean-reversion trades around the volume-weighted average price.", false),
                // "Momentum Lab" (high relative volume) was folded into Gap Rocket above, which is the
                // dedicated high-relative-volume momentum scanner — avoid offering two overlapping ones.
                new StrategyWorkspaceTemplate("Swing Vault", "SWING",
                        "Multi-day swing positions held across sessions.", false),
                new StrategyWorkspaceTemplate("Profit Shield", "SHIELD",
                        "Defensive book focused on protecting realized gains.", false),
                new StrategyWorkspaceTemplate("Earnings Hunter", "EARNINGS",
                        "Event-driven trades around earnings announcements.", false),
                new StrategyWorkspaceTemplate("Manual Trades", "MANUAL",
                        "A home for discretionary, manually managed trades.", true),
                new StrategyWorkspaceTemplate("Custom Strategy", CUSTOM_CODE,
                        "Create a workspace with your own name.", true)
        );
    }
}
