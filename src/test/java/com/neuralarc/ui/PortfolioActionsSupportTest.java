package com.neuralarc.ui;

import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.Position;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioActionsSupportTest {

    private final PortfolioActionsSupport support = new PortfolioActionsSupport();

    @Test
    void filtersProfitableTargetsFromCachedPositions() {
        ManagedStrategy profitable = managed("AAPL", StrategyStatus.ACTIVE, 10, new BigDecimal("100"), new BigDecimal("110"));
        ManagedStrategy losing = managed("MSFT", StrategyStatus.ACTIVE, 10, new BigDecimal("100"), new BigDecimal("90"));
        ManagedStrategy flat = managed("TSLA", StrategyStatus.ACTIVE, 0, BigDecimal.ZERO, BigDecimal.ZERO);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(profitable, losing, flat),
                PortfolioActionsSupport.Scope.PROFITABLE
        );

        assertEquals(1, targets.size());
        assertEquals("AAPL", targets.getFirst().strategy.symbol());
    }

    @Test
    void confirmationMessageTruncatesSymbolsAfterSixEntries() {
        List<ManagedStrategy> targets = List.of(
                managed("AAPL", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101")),
                managed("MSFT", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101")),
                managed("TSLA", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101")),
                managed("NVDA", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101")),
                managed("AMD", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101")),
                managed("META", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101")),
                managed("ORCL", StrategyStatus.ACTIVE, 1, new BigDecimal("100"), new BigDecimal("101"))
        );

        String message = support.buildConfirmationMessage(PortfolioActionsSupport.Scope.ALL_OPEN, targets);

        assertTrue(message.contains("AAPL, MSFT, TSLA, NVDA, AMD, META"));
        assertTrue(message.contains(", ..."));
        assertTrue(!message.contains("ORCL"));
    }

    @Test
    void resultMessageIncludesFailuresSectionWhenAnyFail() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.Scope.LOSS_ONLY,
                new PortfolioActionsSupport.BatchResult(
                        List.of("AAPL", "MSFT"),
                        List.of("TSLA: no open quantity")
                )
        );

        assertTrue(message.contains("Submitted: 2"));
        assertTrue(message.contains("<b>Failed:</b>"));
        assertTrue(message.contains("TSLA: no open quantity"));
    }

    private static ManagedStrategy managed(String symbol, StrategyStatus status, int shares, BigDecimal avgCost, BigDecimal lastPrice) {
        ManagedStrategy managed = new ManagedStrategy(baseStrategy(symbol, status));
        Position position = new Position(symbol);
        if (shares > 0) {
            position.applyBuy(shares, avgCost);
            position.setLastPrice(lastPrice);
        }
        managed.setCachedPosition(position);
        return managed;
    }

    private static Strategy baseStrategy(String symbol, StrategyStatus status) {
        return new Strategy(
                UUID.randomUUID().toString(),
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                status,
                StrategyLifecycleState.CREATED,
                new BigDecimal("10"),
                1,
                new BigDecimal("9"),
                1,
                new BigDecimal("8"),
                1,
                true,
                StopLossType.FIXED_PRICE,
                new BigDecimal("7"),
                new BigDecimal("1"),
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("11"),
                new BigDecimal("100"),
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                new BigDecimal("1"),
                new BigDecimal("1"),
                BigDecimal.ZERO,
                false,
                10,
                new BigDecimal("1000"),
                10,
                Instant.now(),
                Instant.now()
        );
    }
}

