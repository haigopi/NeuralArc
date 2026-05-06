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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Behavioral integration tests for the portfolio action flow — covering all three
 * {@link PortfolioActionsSupport.Scope} values, filtering semantics, scoped messaging,
 * and the result message surface area.
 */
class PortfolioActionsFlowTest {

    private final PortfolioActionsSupport support = new PortfolioActionsSupport();

    // ---- ALL_OPEN scope filtering ----

    @Test
    void allOpenScopeMatchesAnyStrategyWithOpenShares() {
        ManagedStrategy withShares = managed("AAPL", 5, new BigDecimal("100"), new BigDecimal("95"));
        ManagedStrategy noShares = managed("MSFT", 0, BigDecimal.ZERO, BigDecimal.ZERO);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(withShares, noShares), PortfolioActionsSupport.Scope.ALL_OPEN);

        assertEquals(1, targets.size());
        assertEquals("AAPL", targets.getFirst().strategy.symbol());
    }

    @Test
    void allOpenScopeIncludesPositionsRegardlessOfPnl() {
        ManagedStrategy profitable = managed("AAPL", 3, new BigDecimal("100"), new BigDecimal("120"));
        ManagedStrategy losing   = managed("TSLA", 2, new BigDecimal("200"), new BigDecimal("180"));

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(profitable, losing), PortfolioActionsSupport.Scope.ALL_OPEN);

        assertEquals(2, targets.size());
    }

    @Test
    void allOpenScopeExcludesStrategiesWithZeroShares() {
        ManagedStrategy flat = managed("NVDA", 0, BigDecimal.ZERO, BigDecimal.ZERO);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(flat), PortfolioActionsSupport.Scope.ALL_OPEN);

        assertTrue(targets.isEmpty());
    }

    // ---- LOSS_ONLY scope filtering ----

    @Test
    void lossOnlyScopeMatchesNegativePnlPositions() {
        ManagedStrategy losing = managed("META", 4, new BigDecimal("300"), new BigDecimal("270"));

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(losing), PortfolioActionsSupport.Scope.LOSS_ONLY);

        assertEquals(1, targets.size());
        assertEquals("META", targets.getFirst().strategy.symbol());
    }

    @Test
    void lossOnlyScopeExcludesProfitablePositions() {
        ManagedStrategy profitable = managed("AMZN", 2, new BigDecimal("150"), new BigDecimal("175"));
        ManagedStrategy losing     = managed("NFLX", 1, new BigDecimal("200"), new BigDecimal("185"));

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(profitable, losing), PortfolioActionsSupport.Scope.LOSS_ONLY);

        assertEquals(1, targets.size());
        assertEquals("NFLX", targets.getFirst().strategy.symbol());
    }

    @Test
    void lossOnlyScopeExcludesBreakevenandFlat() {
        // Breakeven: avgCost == lastPrice → unrealizedPnl == 0
        ManagedStrategy breakeven = managed("GOOG", 3, new BigDecimal("100"), new BigDecimal("100"));
        ManagedStrategy flat      = managed("NVDA", 0, BigDecimal.ZERO, BigDecimal.ZERO);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(breakeven, flat), PortfolioActionsSupport.Scope.LOSS_ONLY);

        assertTrue(targets.isEmpty());
    }

    // ---- PROFITABLE scope filtering edge cases ----

    @Test
    void profitableScopeExcludesBreakevenPositions() {
        ManagedStrategy breakeven = managed("AMD", 1, new BigDecimal("50"), new BigDecimal("50"));

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(breakeven), PortfolioActionsSupport.Scope.PROFITABLE);

        assertTrue(targets.isEmpty());
    }

    @Test
    void profitableScopeExcludesFlatPositions() {
        ManagedStrategy noPosition = managed("SPY", 0, BigDecimal.ZERO, BigDecimal.ZERO);

        List<ManagedStrategy> targets = support.filterTargets(
                List.of(noPosition), PortfolioActionsSupport.Scope.PROFITABLE);

        assertTrue(targets.isEmpty());
    }

    // ---- Scope metadata ----

    @Test
    void scopeMenuLabelsAreDistinctAndNonBlank() {
        for (PortfolioActionsSupport.Scope scope : PortfolioActionsSupport.Scope.values()) {
            assertFalse(scope.menuLabel().isBlank(), "menuLabel should not be blank for " + scope);
        }
        long distinct = java.util.Arrays.stream(PortfolioActionsSupport.Scope.values())
                .map(PortfolioActionsSupport.Scope::menuLabel)
                .distinct()
                .count();
        assertEquals(PortfolioActionsSupport.Scope.values().length, distinct, "Each scope must have a unique menuLabel");
    }

    @Test
    void scopeDialogTitleMatchesMenuLabel() {
        for (PortfolioActionsSupport.Scope scope : PortfolioActionsSupport.Scope.values()) {
            assertEquals(scope.menuLabel(), scope.dialogTitle(),
                    "dialogTitle() must equal menuLabel() for " + scope);
        }
    }

    @Test
    void scopeLogPrefixWrapsMenuLabelInSquareBrackets() {
        for (PortfolioActionsSupport.Scope scope : PortfolioActionsSupport.Scope.values()) {
            String prefix = scope.logPrefix();
            assertTrue(prefix.startsWith("["), "logPrefix must start with '[' for " + scope);
            assertTrue(prefix.endsWith("]"), "logPrefix must end with ']' for " + scope);
            assertTrue(prefix.contains(scope.menuLabel()),
                    "logPrefix must contain menuLabel for " + scope);
        }
    }

    @Test
    void scopeEmptyMessageIsNonBlankForAllScopes() {
        for (PortfolioActionsSupport.Scope scope : PortfolioActionsSupport.Scope.values()) {
            assertFalse(scope.emptyMessage().isBlank(),
                    "emptyMessage should not be blank for " + scope);
        }
    }

    @Test
    void profitableScopeEmptyMessageMentionsProfitable() {
        String msg = PortfolioActionsSupport.Scope.PROFITABLE.emptyMessage().toLowerCase();
        assertTrue(msg.contains("profitable"), "PROFITABLE emptyMessage should reference 'profitable'");
    }

    @Test
    void lossOnlyScopeEmptyMessageMentionsLosing() {
        String msg = PortfolioActionsSupport.Scope.LOSS_ONLY.emptyMessage().toLowerCase();
        assertTrue(msg.contains("losing") || msg.contains("loss"),
                "LOSS_ONLY emptyMessage should reference 'losing' or 'loss'");
    }

    @Test
    void allOpenScopeEmptyMessageMentionsOpen() {
        String msg = PortfolioActionsSupport.Scope.ALL_OPEN.emptyMessage().toLowerCase();
        assertTrue(msg.contains("open"), "ALL_OPEN emptyMessage should reference 'open'");
    }

    // ---- Result message surface area ----

    @Test
    void resultMessageWithNoFailuresOmitsFailedSection() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.Scope.ALL_OPEN,
                new PortfolioActionsSupport.BatchResult(
                        List.of("AAPL", "MSFT"),
                        List.of()
                )
        );

        assertTrue(message.contains("Submitted: 2"));
        assertFalse(message.contains("Failed"), "No failure section expected when failures list is empty");
    }

    @Test
    void resultMessageWithOnlyFailuresShowsZeroSubmittedAndFailureBlock() {
        String message = support.buildResultMessage(
                PortfolioActionsSupport.Scope.PROFITABLE,
                new PortfolioActionsSupport.BatchResult(
                        List.of(),
                        List.of("TSLA: order rejected")
                )
        );

        assertTrue(message.contains("Submitted: 0"));
        assertTrue(message.contains("Failed"));
        assertTrue(message.contains("TSLA: order rejected"));
    }

    @Test
    void confirmationMessageContainsHeadingWithCount() {
        List<ManagedStrategy> targets = List.of(
                managed("AAPL", 1, new BigDecimal("100"), new BigDecimal("110")),
                managed("MSFT", 2, new BigDecimal("200"), new BigDecimal("210"))
        );

        String message = support.buildConfirmationMessage(PortfolioActionsSupport.Scope.PROFITABLE, targets);

        // heading from Scope.PROFITABLE.confirmHeading(2)
        assertTrue(message.contains("2"), "Confirmation heading should include the target count");
        assertTrue(message.contains("AAPL"));
        assertTrue(message.contains("MSFT"));
    }

    @Test
    void confirmationMessageShowsEllipsisForExactlySevenTargets() {
        List<ManagedStrategy> targets = List.of(
                managed("A1", 1, new BigDecimal("10"), new BigDecimal("11")),
                managed("A2", 1, new BigDecimal("10"), new BigDecimal("11")),
                managed("A3", 1, new BigDecimal("10"), new BigDecimal("11")),
                managed("A4", 1, new BigDecimal("10"), new BigDecimal("11")),
                managed("A5", 1, new BigDecimal("10"), new BigDecimal("11")),
                managed("A6", 1, new BigDecimal("10"), new BigDecimal("11")),
                managed("A7", 1, new BigDecimal("10"), new BigDecimal("11"))
        );

        String message = support.buildConfirmationMessage(PortfolioActionsSupport.Scope.ALL_OPEN, targets);

        assertTrue(message.contains(", ..."), "Ellipsis expected when more than 6 targets");
        assertFalse(message.contains("A7"), "Seventh symbol must be omitted");
    }

    @Test
    void confirmationMessageDoesNotShowEllipsisForExactlySixTargets() {
        List<ManagedStrategy> targets = List.of(
                managed("B1", 1, new BigDecimal("10"), new BigDecimal("11")),
                managed("B2", 1, new BigDecimal("10"), new BigDecimal("11")),
                managed("B3", 1, new BigDecimal("10"), new BigDecimal("11")),
                managed("B4", 1, new BigDecimal("10"), new BigDecimal("11")),
                managed("B5", 1, new BigDecimal("10"), new BigDecimal("11")),
                managed("B6", 1, new BigDecimal("10"), new BigDecimal("11"))
        );

        String message = support.buildConfirmationMessage(PortfolioActionsSupport.Scope.ALL_OPEN, targets);

        assertFalse(message.contains(", ..."), "No ellipsis expected for exactly 6 targets");
    }

    // ---- helpers ----

    private static ManagedStrategy managed(String symbol, int shares, BigDecimal avgCost, BigDecimal lastPrice) {
        ManagedStrategy managed = new ManagedStrategy(baseStrategy(symbol));
        Position position = new Position(symbol);
        if (shares > 0) {
            position.applyBuy(shares, avgCost);
            position.setLastPrice(lastPrice);
        }
        managed.setCachedPosition(position);
        return managed;
    }

    private static Strategy baseStrategy(String symbol) {
        return new Strategy(
                UUID.randomUUID().toString(),
                symbol + " Strategy",
                symbol,
                StrategyMode.PAPER,
                StrategyStatus.ACTIVE,
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

