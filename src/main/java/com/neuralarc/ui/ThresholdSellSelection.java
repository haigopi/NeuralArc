package com.neuralarc.ui;

import com.neuralarc.model.SellSubmissionType;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** What the operator confirmed in the threshold-sell dialog: how to sell and exactly which rows. */
record ThresholdSellSelection(ThresholdSellRule rule, SellSubmissionType submissionType, Set<String> strategyIds) {
    ThresholdSellSelection {
        strategyIds = strategyIds == null ? Set.of() : Set.copyOf(strategyIds);
    }

    /**
     * The ticked rows that are still sellable. The grid can sit open while the market and the
     * broker move on, so eligibility is checked again against live state at submit time.
     */
    List<ManagedStrategy> selectedFrom(List<ManagedStrategy> scope, Predicate<ManagedStrategy> stillSellable) {
        return scope.stream()
                .filter(entry -> strategyIds.contains(entry.strategy.id()))
                .filter(stillSellable)
                .toList();
    }
}
