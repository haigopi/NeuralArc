package com.neuralarc.service;

import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Keeps an entry's sells to the shares it bought itself.
 *
 * <p>The broker nets everything held in a symbol into one position, and the app lets several entries
 * share a symbol. An exit that sells "the position" therefore sells every entry's shares at once, and
 * the other entries' own exits then have nothing left, or find a short. When another live entry in the
 * same symbol and mode holds shares of its own, this entry is capped to its own tracked shares.
 *
 * <p>The cap applies only then. An entry that is alone in its symbol keeps selling what the broker
 * holds, including shares bought outside the app, and an entry with no tracked fills at all (adopted
 * from an existing position) is left uncapped because its own orders say nothing about what it holds.
 */
final class SharedPositionCap {
    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository orderRepository;

    SharedPositionCap(StrategyRepository strategyRepository, StrategyOrderRepository orderRepository) {
        this.strategyRepository = strategyRepository;
        this.orderRepository = orderRepository;
    }

    /** @return {@code brokerQuantity} or, when it must be capped, the entry's own share count. */
    BigDecimal cap(Strategy strategy, BigDecimal brokerQuantity, List<StrategyOrder> ownOrders) {
        if (strategy == null || brokerQuantity == null || brokerQuantity.signum() <= 0) {
            return brokerQuantity;
        }
        int own = OwnedShares.net(ownOrders);
        if (own <= 0 || BigDecimal.valueOf(own).compareTo(brokerQuantity) >= 0) {
            return brokerQuantity;
        }
        return anotherEntryHoldsShares(strategy) ? BigDecimal.valueOf(own) : brokerQuantity;
    }

    Optional<AlpacaPositionData> cap(Strategy strategy, Optional<AlpacaPositionData> position, List<StrategyOrder> ownOrders) {
        if (position.isEmpty() || !position.get().exists()) {
            return position;
        }
        AlpacaPositionData held = position.get();
        BigDecimal capped = cap(strategy, held.quantity(), ownOrders);
        if (capped.compareTo(held.quantity()) == 0) {
            return position;
        }
        return Optional.of(new AlpacaPositionData(held.symbol(), capped, held.avgEntryPrice(), held.marketPrice(), held.rawJson()));
    }

    private boolean anotherEntryHoldsShares(Strategy strategy) {
        return strategyRepository.findAll().stream()
                .filter(other -> other != null && !other.id().equals(strategy.id()))
                .filter(other -> other.mode() == strategy.mode())
                .filter(other -> other.symbol() != null && other.symbol().equalsIgnoreCase(strategy.symbol()))
                .filter(other -> other.status() != StrategyStatus.ARCHIVED)
                .anyMatch(other -> OwnedShares.net(orderRepository.findByStrategyId(other.id())) > 0);
    }
}
