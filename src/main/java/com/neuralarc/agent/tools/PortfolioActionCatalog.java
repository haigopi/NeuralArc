package com.neuralarc.agent.tools;

import java.util.List;
import java.util.Optional;

/**
 * What the Portfolio Actions menu could do right now, without doing any of it.
 *
 * <p>The analyst can read the book and the market but has no idea what the operator can do about
 * either, so its advice stops at "NVDA's staged buy is 4% above the safe low" when the console has
 * an action for exactly that. This hands it the menu's own vocabulary — each action's name, what it
 * does, and which rows it would touch if it ran — while remaining strictly a read: a preview counts
 * matches, it never performs one.
 *
 * <p>An interface, implemented by the UI, so the agent package stays clear of Swing and the counts
 * come from the same matchers the menu itself uses. If the two ever disagreed, the analyst would be
 * describing an app that does not exist.
 */
public interface PortfolioActionCatalog {
    /**
     * @param name        the action as the menu names it
     * @param description what running it would do
     * @param matchCount  how many rows it would act on right now
     * @param symbols     those rows' symbols
     * @param emptyReason why nothing matches, when nothing does
     */
    record ActionPreview(String name, String description, int matchCount, List<String> symbols, String emptyReason) {
        public ActionPreview {
            symbols = symbols == null ? List.of() : List.copyOf(symbols);
        }
    }

    /** Every action, each with the rows it would act on. */
    List<ActionPreview> previewAll();

    /** One action by name, accepting the menu label or its code; empty when no action matches. */
    Optional<ActionPreview> preview(String actionName);
}
