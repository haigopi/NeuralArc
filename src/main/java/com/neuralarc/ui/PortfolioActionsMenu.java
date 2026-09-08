package com.neuralarc.ui;

import java.util.List;

/**
 * Structure of the Portfolio Actions menu: a handful of named groups, each holding a few actions
 * that carry a one-line description of what they do.
 *
 * <p>The menu had grown to roughly thirty entries in one flat list, where several labels differ by a
 * single word ("losing pending" vs "gaining pending") and nothing on screen said what each one
 * touched. Grouping keeps the top level short, and the description under every label answers "which
 * one is this?" without opening the confirm dialog.
 */
final class PortfolioActionsMenu {
    private static final String DESCRIPTION_COLOR = "#8e97a8";

    private PortfolioActionsMenu() {
    }

    /** One action: what it is called, what it does, its icon, and what to run. */
    record Entry(String label, String description, String iconPath, Runnable action, boolean enabled, String disabledTooltip) {
        Entry(String label, String description, String iconPath, Runnable action) {
            this(label, description, iconPath, action, true, null);
        }

        Entry disabledWith(String tooltip) {
            return new Entry(label, description, iconPath, action, false, tooltip);
        }
    }

    /** A submenu of related actions. */
    record Group(String title, String iconPath, List<Entry> entries) {
    }

    /**
     * Two-line menu text: the action name, then its description in the muted secondary style used
     * for descriptions elsewhere in the app.
     */
    static String itemHtml(String label, String description) {
        String safeLabel = escape(label);
        if (description == null || description.isBlank()) {
            return "<html><div>" + safeLabel + "</div></html>";
        }
        return "<html><div>" + safeLabel + "</div>"
                + "<div style='color:" + DESCRIPTION_COLOR + ";font-size:9px;padding-top:2px'>"
                + escape(description) + "</div></html>";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
