package com.neuralarc.ui;

import com.neuralarc.model.StrategyOrder;

import javax.swing.JOptionPane;
import javax.swing.SwingWorker;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reconcile from Broker Fills, end to end: find the positions whose shares their orders do not
 * explain, ask the broker for those symbols' order history (off the EDT), let the operator review the
 * proposed record fixes, then apply the ticked ones.
 *
 * <p>Only NeuralArc's own order records are written. The broker is read, never changed.
 */
final class BrokerFillReconcileFlow {
    static final String NAME = "Reconcile from Broker Fills";
    /** Where history reaches back to when a strategy has no creation time to say otherwise. */
    private static final Duration DEFAULT_LOOKBACK = Duration.ofDays(365);
    /** Margin before a strategy's creation, covering an order placed just before its row was saved. */
    private static final Duration CREATION_MARGIN = Duration.ofDays(1);

    private final PortfolioActionsController.Gateway gateway;
    private final Runnable refresh;

    BrokerFillReconcileFlow(PortfolioActionsController.Gateway gateway, Runnable refresh) {
        this.gateway = gateway;
        this.refresh = refresh;
    }

    void run() {
        gateway.actionStarted(NAME);
        List<BrokerFillReconciler.Target> targets = targets();
        if (targets.isEmpty()) {
            String message = "Every position's shares are accounted for by its own orders. There is nothing to reconcile.";
            gateway.actionSkipped(NAME, message);
            gateway.showMessage(message, NAME, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        gateway.log("[Reconcile] Checking the broker's order history for " + targets.size()
                + " position(s) with untracked shares.");
        new SwingWorker<List<BrokerFillReconciler.Proposal>, Void>() {
            @Override
            protected List<BrokerFillReconciler.Proposal> doInBackground() {
                return BrokerFillReconciler.propose(targets, BrokerFillReconcileFlow.this::history, gateway::findOrderByBrokerId);
            }

            @Override
            protected void done() {
                try {
                    review(get());
                } catch (Exception ex) {
                    gateway.actionFailed(NAME, ex.getMessage());
                    gateway.showMessage("Could not read the broker's order history: " + ex.getMessage(),
                            NAME, JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private List<BrokerFillReconciler.Target> targets() {
        List<BrokerFillReconciler.Target> targets = new ArrayList<>();
        for (ManagedStrategy entry : gateway.currentStrategies()) {
            if (entry == null || entry.strategy == null || entry.untrackedShares() <= 0) {
                continue;
            }
            targets.add(new BrokerFillReconciler.Target(
                    entry.strategy.id(), entry.strategy.symbol(), entry.strategy.mode(), entry.untrackedShares(),
                    entry.strategy.createdAt(), gateway.ordersForStrategy(entry.strategy.id())));
        }
        return targets;
    }

    private List<com.neuralarc.api.AlpacaOrderData> history(BrokerFillReconciler.Target target) {
        Instant after = target.strategyCreatedAt() == null
                ? Instant.now().minus(DEFAULT_LOOKBACK)
                : target.strategyCreatedAt().minus(CREATION_MARGIN);
        return gateway.brokerOrderHistory(target.mode(), target.symbol(), after);
    }

    private void review(List<BrokerFillReconciler.Proposal> proposals) {
        if (proposals.isEmpty()) {
            gateway.actionSkipped(NAME, "The broker's history shows nothing missing.");
            gateway.showMessage("The broker's order history shows nothing missing from these positions' records.",
                    NAME, JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        Optional<List<BrokerFillReconciler.Proposal>> chosen = gateway.chooseBrokerFillFixes(proposals);
        if (chosen.isEmpty()) {
            gateway.actionCanceled(NAME);
            return;
        }
        apply(chosen.get());
    }

    private void apply(List<BrokerFillReconciler.Proposal> chosen) {
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                int applied = 0;
                for (BrokerFillReconciler.Proposal proposal : chosen) {
                    if (applyOne(proposal)) {
                        applied++;
                    }
                }
                return applied;
            }

            @Override
            protected void done() {
                try {
                    int applied = get();
                    refresh.run();
                    gateway.actionCompleted(NAME, "Fixed " + applied + " of " + chosen.size() + " order record(s).");
                    gateway.showToast(NAME + " — fixed " + applied + " order record" + (applied == 1 ? "" : "s"), applied < chosen.size());
                } catch (Exception ex) {
                    gateway.actionFailed(NAME, ex.getMessage());
                    gateway.showMessage("Could not save the corrected records: " + ex.getMessage(), NAME, JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /** @return false when the fix no longer applies (the record changed since it was proposed). */
    private boolean applyOne(BrokerFillReconciler.Proposal proposal) {
        if (proposal.kind() == BrokerFillReconciler.Kind.LINK) {
            // Someone may have recorded it since the review opened; never add the same order twice.
            if (gateway.findOrderByBrokerId(proposal.brokerOrderId()).isPresent()) {
                gateway.log("[Reconcile] " + proposal.symbol() + ": order " + proposal.brokerOrderId()
                        + " is already recorded; skipped.");
                return false;
            }
            gateway.saveStrategyOrder(BrokerFillReconciler.newOrderFor(proposal));
            gateway.log("[Reconcile] " + proposal.symbol() + ": added the broker's " + proposal.side()
                    + " of " + proposal.filledQuantity().stripTrailingZeros().toPlainString() + " (order " + proposal.brokerOrderId() + ").");
            return true;
        }
        if (proposal.kind() == BrokerFillReconciler.Kind.SYNC) {
            StrategyOrder existing = proposal.existing();
            BrokerFillReconciler.applyTo(existing, proposal);
            gateway.saveStrategyOrder(existing);
            gateway.log("[Reconcile] " + proposal.symbol() + ": corrected order " + proposal.brokerOrderId()
                    + " to " + proposal.filledQuantity().stripTrailingZeros().toPlainString() + " filled.");
            return true;
        }
        return false;
    }
}
