package com.neuralarc.ui;

import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerFillReconcilerTest {
    private static final Instant T0 = Instant.parse("2026-10-01T14:30:00Z");

    private static AlpacaOrderData broker(String id, String clientId, String side, String status, String filled) {
        return new AlpacaOrderData(id, clientId, "RDW", side, "market", BigDecimal.ZERO, new BigDecimal("10.50"),
                new BigDecimal(filled), status, "{}", T0);
    }

    private static StrategyOrder local(String strategyId, String brokerId, StrategyOrderStatus status, String filled, String requested) {
        return new StrategyOrder("o-" + brokerId, strategyId, StrategyStage.BASE_BUY, brokerId, "cid-" + brokerId, "RDW",
                StrategyOrderSide.BUY, StrategyOrderType.LIMIT, new BigDecimal("10.50"), BigDecimal.ZERO,
                new BigDecimal(requested), new BigDecimal(filled), new BigDecimal("10.50"), status, T0, T0, null, "{}");
    }

    private static BrokerFillReconciler.Target target(String id, int untracked, StrategyOrder... orders) {
        return new BrokerFillReconciler.Target(id, "RDW", StrategyMode.LIVE, untracked, T0.minusSeconds(3600), List.of(orders));
    }

    private static List<BrokerFillReconciler.Proposal> propose(
            List<BrokerFillReconciler.Target> targets, List<AlpacaOrderData> history, Map<String, StrategyOrder> everyLocal) {
        return BrokerFillReconciler.propose(targets, t -> history, id -> Optional.ofNullable(everyLocal.get(id)));
    }

    @Test
    void aBrokerFillWithNoLocalRecordIsProposedAsAMissingOrder() {
        // The RDW case: the submit's response was lost, the strategy was marked failed, and no order was saved.
        List<BrokerFillReconciler.Proposal> proposals = propose(
                List.of(target("s1", 4)), List.of(broker("b1", "", "buy", "filled", "4")), Map.of());

        assertEquals(1, proposals.size());
        BrokerFillReconciler.Proposal proposal = proposals.get(0);
        assertEquals(BrokerFillReconciler.Kind.LINK, proposal.kind());
        assertEquals("s1", proposal.strategyId());
        assertEquals(0, new BigDecimal("4").compareTo(proposal.shareDelta()));
        assertEquals(StrategyStage.MANUAL_BUY, proposal.stage(), "no client id to say which stage it was");
    }

    @Test
    void theStageComesFromTheClientIdWhenItCarriesOne() {
        String clientId = "NA_LIVE_ALL_RDW_BASE_BUY_20261001143000_AB12";

        BrokerFillReconciler.Proposal proposal = propose(
                List.of(target("s1", 4)), List.of(broker("b1", clientId, "buy", "filled", "4")), Map.of()).get(0);

        assertEquals(StrategyStage.BASE_BUY, proposal.stage());
    }

    @Test
    void aStageThatDisagreesWithTheOrderSideFallsBackToManual() {
        String clientId = "NA_LIVE_ALL_RDW_BASE_BUY_20261001143000_AB12";

        BrokerFillReconciler.Proposal proposal = propose(
                List.of(target("s1", 1)), List.of(broker("b1", clientId, "sell", "filled", "1")), Map.of()).get(0);

        assertEquals(StrategyStage.MANUAL_EXIT, proposal.stage());
        assertEquals(0, new BigDecimal("-1").compareTo(proposal.shareDelta()), "a sell takes shares away");
    }

    @Test
    void aCancelledOrderThatPartlyFilledIsProposedAsACorrection() {
        // Recorded as cancelled with nothing filled, but the broker filled 1 before the cancel.
        StrategyOrder stale = local("s1", "b1", StrategyOrderStatus.CANCELED, "0", "2");

        List<BrokerFillReconciler.Proposal> proposals = propose(
                List.of(target("s1", 1, stale)), List.of(broker("b1", "cid-b1", "buy", "canceled", "1")), Map.of("b1", stale));

        assertEquals(1, proposals.size());
        assertEquals(BrokerFillReconciler.Kind.SYNC, proposals.get(0).kind());
        assertEquals(0, BigDecimal.ONE.compareTo(proposals.get(0).shareDelta()));
        assertTrue(proposals.get(0).note().contains("canceled"), proposals.get(0).note());
    }

    @Test
    void aRecordThatAlreadyMatchesTheBrokerIsLeftAlone() {
        StrategyOrder fine = local("s1", "b1", StrategyOrderStatus.FILLED, "2", "2");

        List<BrokerFillReconciler.Proposal> proposals = propose(
                List.of(target("s1", 1, fine)), List.of(broker("b1", "cid-b1", "buy", "filled", "2")), Map.of("b1", fine));

        assertEquals(1, proposals.size());
        assertEquals(BrokerFillReconciler.Kind.REVIEW, proposals.get(0).kind(),
                "nothing to fix, so the operator is told the shares came from elsewhere");
        assertFalse(proposals.get(0).applicable());
    }

    @Test
    void aClientIdMatchFindsARecordSavedWithoutTheBrokerOrderId() {
        StrategyOrder noBrokerId = local("s1", "b1", StrategyOrderStatus.PENDING, "0", "2");
        AlpacaOrderData fill = broker("b9", "cid-b1", "buy", "filled", "2");

        List<BrokerFillReconciler.Proposal> proposals = propose(List.of(target("s1", 2, noBrokerId)), List.of(fill), Map.of());

        assertEquals(BrokerFillReconciler.Kind.SYNC, proposals.get(0).kind(), "it is the same order, not a new one");
    }

    @Test
    void unfilledBrokerOrdersAreIgnored() {
        List<BrokerFillReconciler.Proposal> proposals = propose(
                List.of(target("s1", 2)), List.of(broker("b1", "", "buy", "canceled", "0")), Map.of());

        assertEquals(BrokerFillReconciler.Kind.REVIEW, proposals.get(0).kind());
    }

    @Test
    void anOrderBelongingToAnotherStrategyIsNotTakenOver() {
        StrategyOrder theirs = local("other", "b1", StrategyOrderStatus.FILLED, "3", "3");

        List<BrokerFillReconciler.Proposal> proposals = propose(
                List.of(target("s1", 3)), List.of(broker("b1", "cid-b1", "buy", "filled", "3")), Map.of("b1", theirs));

        assertTrue(proposals.stream().noneMatch(BrokerFillReconciler.Proposal::applicable));
    }

    @Test
    void whenSeveralStrategiesHoldTheSymbolAnUnownedFillIsLeftForTheOperator() {
        List<BrokerFillReconciler.Proposal> proposals = propose(
                List.of(target("s1", 2), target("s2", 2)),
                List.of(broker("b1", "", "buy", "filled", "2")), Map.of());

        assertEquals(1, proposals.size());
        assertEquals(BrokerFillReconciler.Kind.REVIEW, proposals.get(0).kind());
        assertTrue(proposals.get(0).note().contains("Several strategies"));
    }

    @Test
    void appliedFixesMakeTheOrdersExplainTheBrokerShares() {
        StrategyOrder partial = local("s1", "b1", StrategyOrderStatus.CANCELED, "0", "5");
        List<BrokerFillReconciler.Proposal> proposals = propose(
                List.of(target("s1", 6, partial)),
                List.of(broker("b1", "cid-b1", "buy", "canceled", "2"), broker("b2", "", "buy", "filled", "4")),
                Map.of("b1", partial));

        for (BrokerFillReconciler.Proposal proposal : proposals) {
            if (proposal.kind() == BrokerFillReconciler.Kind.SYNC) {
                BrokerFillReconciler.applyTo(proposal.existing(), proposal);
            }
        }
        StrategyOrder added = BrokerFillReconciler.newOrderFor(
                proposals.stream().filter(p -> p.kind() == BrokerFillReconciler.Kind.LINK).findFirst().orElseThrow());

        assertEquals(6, StrategyOrderFillSupport.netFilledShares(List.of(partial, added)),
                "the untracked shares are now explained by the records");
        assertEquals(StrategyOrderStatus.FILLED, partial.status());
        assertEquals("b2", added.alpacaOrderId());
    }
}
