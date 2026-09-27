package com.neuralarc.ui;

import com.neuralarc.agent.tools.PortfolioActionCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The Portfolio Actions menu as a read-only catalogue for the AI analyst.
 *
 * <p>Every count here comes from {@link PortfolioActionsSupport.BulkAction#matches} — the same
 * predicate the menu runs when the operator clicks — so a preview and the action itself can never
 * describe different sets of rows. The descriptions are the menu's own confirmation text with its
 * markup removed, because the operator's wording is what the analyst should be speaking back to them.
 *
 * <p>Nothing here mutates anything, which is what lets these tools sit in a read-only registry.
 */
final class PortfolioActionPreviews implements PortfolioActionCatalog {
    private final Supplier<List<ManagedStrategy>> currentStrategies;
    private final Supplier<List<ManagedStrategy>> scopedStrategies;

    PortfolioActionPreviews(Supplier<List<ManagedStrategy>> currentStrategies,
                            Supplier<List<ManagedStrategy>> scopedStrategies) {
        this.currentStrategies = currentStrategies;
        this.scopedStrategies = scopedStrategies;
    }

    @Override
    public List<ActionPreview> previewAll() {
        List<ActionPreview> previews = new ArrayList<>();
        for (PortfolioActionsSupport.BulkAction action : PortfolioActionsSupport.BulkAction.values()) {
            previews.add(preview(action));
        }
        return previews;
    }

    @Override
    public Optional<ActionPreview> preview(String actionName) {
        String wanted = normalize(actionName);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        for (PortfolioActionsSupport.BulkAction action : PortfolioActionsSupport.BulkAction.values()) {
            if (normalize(action.menuLabel()).equals(wanted) || normalize(action.name()).equals(wanted)) {
                return Optional.of(preview(action));
            }
        }
        return Optional.empty();
    }

    private ActionPreview preview(PortfolioActionsSupport.BulkAction action) {
        List<String> symbols = new ArrayList<>();
        for (ManagedStrategy entry : strategiesFor(action)) {
            if (action.matches(entry) && entry.strategy != null && entry.strategy.symbol() != null) {
                symbols.add(entry.strategy.symbol());
            }
        }
        return new ActionPreview(action.menuLabel(), plainText(action.confirmDetail()), symbols.size(), symbols,
                symbols.isEmpty() ? plainText(action.emptyMessage()) : "");
    }

    /** Trade History cleanup reads the whole scope; every other action works on the current tab. */
    private List<ManagedStrategy> strategiesFor(PortfolioActionsSupport.BulkAction action) {
        Supplier<List<ManagedStrategy>> source =
                action == PortfolioActionsSupport.BulkAction.CLEAN_TRADE_HISTORY ? scopedStrategies : currentStrategies;
        List<ManagedStrategy> strategies = source == null ? null : source.get();
        return strategies == null ? List.of() : List.copyOf(strategies);
    }

    /** The menu's confirmation text without its HTML; the model reads sentences, not markup. */
    static String plainText(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("(?i)<br\\s*/?>", " ")
                .replaceAll("<[^>]+>", "")
                .replace("&amp;", "&")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Ignores case, spacing, punctuation — the model will not spell the menu label exactly. */
    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
