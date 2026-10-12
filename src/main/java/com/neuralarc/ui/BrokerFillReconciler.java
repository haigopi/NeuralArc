package com.neuralarc.ui;

import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.util.BrokerOrderStatusUtil;
import com.neuralarc.util.ClientOrderId;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Works out what a strategy's order records are missing, by comparing them with the broker's order
 * history for the same symbol. Pure: it reads, proposes, and changes nothing; the operator reviews
 * the proposals and {@link #applyTo} / {@link #newOrderFor} produce the records to save.
 *
 * <p>Two things leave shares at the broker that a strategy's orders do not explain. A broker order
 * can have no local record at all, as when a submit succeeded but the response never came back (the
 * strategy was marked failed with an {@code api_error}). Or the record exists but is stale: it says
 * cancelled, or short of what actually filled, because the order partly filled before it was cancelled.
 * The first becomes a {@link Kind#LINK} proposal and the second a {@link Kind#SYNC}.
 *
 * <p>Orders are only ever attached to a strategy when that is unambiguous. If several strategies
 * hold the same symbol the broker order is left for the operator ({@link Kind#REVIEW}); the
 * broker's own history is never altered, and only fills (quantity above zero) are considered.
 */
final class BrokerFillReconciler {
    private BrokerFillReconciler() {
    }

    enum Kind {
        /** A broker fill with no local record: add one to the strategy. */
        LINK,
        /** A local record that understates what the broker filled: correct it. */
        SYNC,
        /** Nothing safe to apply; says why, so the operator knows where to look next. */
        REVIEW
    }

    /** A strategy that holds shares its own orders do not explain. */
    record Target(
            String strategyId,
            String symbol,
            StrategyMode mode,
            int untrackedShares,
            Instant strategyCreatedAt,
            List<StrategyOrder> orders
    ) {
        Target {
            symbol = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
            orders = orders == null ? List.of() : List.copyOf(orders);
        }
    }

    record Proposal(
            Kind kind,
            String strategyId,
            String symbol,
            StrategyMode mode,
            StrategyOrderSide side,
            BigDecimal filledQuantity,
            BigDecimal averagePrice,
            Instant filledAt,
            String brokerOrderId,
            String clientOrderId,
            String brokerStatus,
            StrategyStage stage,
            StrategyOrder existing,
            BigDecimal shareDelta,
            String note
    ) {
        boolean applicable() {
            return kind != Kind.REVIEW;
        }
    }

    /**
     * @param targets          strategies with untracked shares.
     * @param brokerHistory    the broker's orders for a symbol, as the caller fetched them.
     * @param localByBrokerId  the local record, from any strategy, for a broker order id.
     */
    static List<Proposal> propose(
            List<Target> targets,
            Function<Target, List<AlpacaOrderData>> brokerHistory,
            Function<String, Optional<StrategyOrder>> localByBrokerId
    ) {
        Map<String, List<Target>> bySymbol = new LinkedHashMap<>();
        for (Target target : targets) {
            bySymbol.computeIfAbsent(target.mode() + "|" + target.symbol(), key -> new ArrayList<>()).add(target);
        }
        List<Proposal> proposals = new ArrayList<>();
        for (List<Target> group : bySymbol.values()) {
            // One fetch per symbol, reaching back to the oldest strategy on it.
            Target first = group.get(0);
            Instant since = group.stream().map(Target::strategyCreatedAt).filter(java.util.Objects::nonNull)
                    .min(Instant::compareTo).orElse(null);
            Target forFetch = new Target(first.strategyId(), first.symbol(), first.mode(), 0, since, List.of());
            proposals.addAll(proposeForSymbol(group, brokerHistory.apply(forFetch), localByBrokerId));
        }
        return proposals;
    }

    private static List<Proposal> proposeForSymbol(
            List<Target> group,
            List<AlpacaOrderData> history,
            Function<String, Optional<StrategyOrder>> localByBrokerId
    ) {
        Target first = group.get(0);
        List<Proposal> found = new ArrayList<>();
        for (AlpacaOrderData broker : history == null ? List.<AlpacaOrderData>of() : history) {
            if (broker.filledQuantity().signum() <= 0 || sideOf(broker) == null) {
                continue;
            }
            Optional<StrategyOrder> local = findLocal(broker, group, localByBrokerId);
            if (local.isPresent()) {
                Target owner = group.stream()
                        .filter(target -> target.strategyId().equals(local.get().strategyId()))
                        .findFirst().orElse(null);
                if (owner != null) {
                    syncProposal(owner, local.get(), broker).ifPresent(found::add);
                }
                // A record owned by a strategy outside this group is accounted for elsewhere.
                continue;
            }
            found.add(group.size() == 1 ? linkProposal(first, broker) : ambiguous(first, broker));
        }
        for (Target target : group) {
            if (found.stream().noneMatch(p -> p.applicable() && p.strategyId().equals(target.strategyId()))
                    && found.stream().noneMatch(p -> !p.applicable() && p.brokerOrderId() != null)) {
                found.add(review(target, "No broker fill is missing from this strategy's records since it was created."
                        + " The " + target.untrackedShares() + " untracked share(s) were bought outside NeuralArc,"
                        + " or before this strategy existed."));
            }
        }
        return found;
    }

    private static Optional<StrategyOrder> findLocal(
            AlpacaOrderData broker, List<Target> group, Function<String, Optional<StrategyOrder>> localByBrokerId) {
        Optional<StrategyOrder> byId = localByBrokerId.apply(broker.orderId());
        if (byId.isPresent()) {
            return byId;
        }
        // A submit that lost its response may have saved the order with only the client id.
        return group.stream()
                .flatMap(target -> target.orders().stream())
                .filter(order -> !broker.clientOrderId().isBlank() && broker.clientOrderId().equals(order.clientOrderId()))
                .findFirst();
    }

    private static Optional<Proposal> syncProposal(Target owner, StrategyOrder local, AlpacaOrderData broker) {
        BigDecimal recorded = StrategyOrderFillSupport.countsAsFilled(local)
                ? StrategyOrderFillSupport.resolvedFilledQuantity(local)
                : BigDecimal.ZERO;
        if (broker.filledQuantity().compareTo(recorded) <= 0) {
            return Optional.empty();
        }
        BigDecimal missing = broker.filledQuantity().subtract(recorded);
        String why = StrategyOrderFillSupport.countsAsFilled(local)
                ? "Recorded fill is " + recorded.stripTrailingZeros().toPlainString() + "; the broker filled "
                        + broker.filledQuantity().stripTrailingZeros().toPlainString() + "."
                : "Recorded as " + local.status().name().toLowerCase(Locale.ROOT)
                        + " with no fill; the broker shows it " + shownStatus(broker) + ".";
        return Optional.of(new Proposal(Kind.SYNC, owner.strategyId(), owner.symbol(), owner.mode(), local.side(),
                broker.filledQuantity(), broker.filledAveragePrice(), broker.submittedAt(), broker.orderId(),
                local.clientOrderId(), broker.status(), local.stage(), local, signed(local.side(), missing), why));
    }

    private static Proposal linkProposal(Target target, AlpacaOrderData broker) {
        StrategyOrderSide side = sideOf(broker);
        StrategyStage stage = stageFor(broker, side);
        return new Proposal(Kind.LINK, target.strategyId(), target.symbol(), target.mode(), side,
                broker.filledQuantity(), broker.filledAveragePrice(), broker.submittedAt(), broker.orderId(),
                broker.clientOrderId(), broker.status(), stage, null,
                signed(side, broker.filledQuantity()),
                "No local record of this " + shownStatus(broker) + " broker order.");
    }

    private static Proposal ambiguous(Target target, AlpacaOrderData broker) {
        StrategyOrderSide side = sideOf(broker);
        return new Proposal(Kind.REVIEW, target.strategyId(), target.symbol(), target.mode(), side,
                broker.filledQuantity(), broker.filledAveragePrice(), broker.submittedAt(), broker.orderId(),
                broker.clientOrderId(), broker.status(), stageFor(broker, side), null, BigDecimal.ZERO,
                "Several strategies hold " + target.symbol() + ", so which one owns this order cannot be told.");
    }

    private static Proposal review(Target target, String note) {
        return new Proposal(Kind.REVIEW, target.strategyId(), target.symbol(), target.mode(), null,
                BigDecimal.ZERO, BigDecimal.ZERO, null, null, null, "", null, null, BigDecimal.ZERO, note);
    }

    /** The stage the order was placed for when its client id says so, else a manual order of that side. */
    private static StrategyStage stageFor(AlpacaOrderData broker, StrategyOrderSide side) {
        Optional<ClientOrderId.Parsed> parsed = ClientOrderId.parse(broker.clientOrderId());
        if (parsed.isPresent()) {
            try {
                StrategyStage stage = StrategyStage.valueOf(parsed.get().stage());
                if (isBuyStage(stage) == (side == StrategyOrderSide.BUY)) {
                    return stage;
                }
            } catch (IllegalArgumentException unknownStage) {
                // fall through to the manual stage
            }
        }
        return side == StrategyOrderSide.BUY ? StrategyStage.MANUAL_BUY : StrategyStage.MANUAL_EXIT;
    }

    private static boolean isBuyStage(StrategyStage stage) {
        return stage == StrategyStage.BASE_BUY || stage == StrategyStage.BUY_LIMIT_1
                || stage == StrategyStage.BUY_LIMIT_2 || stage == StrategyStage.MANUAL_BUY;
    }

    private static StrategyOrderSide sideOf(AlpacaOrderData broker) {
        return switch (broker.side().toLowerCase(Locale.ROOT)) {
            case "buy" -> StrategyOrderSide.BUY;
            case "sell" -> StrategyOrderSide.SELL;
            default -> null;
        };
    }

    private static BigDecimal signed(StrategyOrderSide side, BigDecimal quantity) {
        return side == StrategyOrderSide.BUY ? quantity : quantity.negate();
    }

    private static String shownStatus(AlpacaOrderData broker) {
        return BrokerOrderStatusUtil.normalize(broker.status()).replace('_', ' ');
    }

    /**
     * A new record for a {@link Kind#LINK}. The order is terminal at the broker with these fills, so
     * it is recorded as filled for the quantity that actually executed.
     */
    static StrategyOrder newOrderFor(Proposal proposal) {
        Instant at = proposal.filledAt() == null ? Instant.now() : proposal.filledAt();
        String clientId = proposal.clientOrderId() == null || proposal.clientOrderId().isBlank()
                ? "NA_RECONCILED_" + proposal.brokerOrderId()
                : proposal.clientOrderId();
        return new StrategyOrder(
                UUID.randomUUID().toString(), proposal.strategyId(), proposal.stage(), proposal.brokerOrderId(), clientId,
                proposal.symbol(), proposal.side(), StrategyOrderType.MARKET, BigDecimal.ZERO, BigDecimal.ZERO,
                proposal.filledQuantity(), proposal.filledQuantity(), proposal.averagePrice(),
                StrategyOrderStatus.FILLED, at, Instant.now(), at,
                "{\"reconciledFromBrokerFills\":true,\"brokerStatus\":\"" + proposal.brokerStatus() + "\"}");
    }

    /**
     * Corrects a {@link Kind#SYNC} record in place. A cancelled or expired order that executed part of
     * its quantity is recorded as filled for that part: it is finished, and the shares are real.
     */
    static void applyTo(StrategyOrder order, Proposal proposal) {
        order.setFilledQuantity(proposal.filledQuantity());
        order.setFilledAveragePrice(proposal.averagePrice());
        order.setStatus(StrategyOrderStatus.FILLED);
        if (order.filledAt() == null) {
            order.setFilledAt(proposal.filledAt() == null ? Instant.now() : proposal.filledAt());
        }
    }
}
