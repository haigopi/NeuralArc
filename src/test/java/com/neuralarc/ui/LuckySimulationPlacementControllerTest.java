package com.neuralarc.ui;

import com.neuralarc.model.AutoAnalyzeBundle;
import com.neuralarc.model.AutoAnalyzeResult;
import com.neuralarc.model.LuckySimulationSelection;
import com.neuralarc.model.MarketMode;
import com.neuralarc.model.RecommendationAction;
import com.neuralarc.model.RecommendationType;
import com.neuralarc.model.ShortTermMarketMode;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyConfig;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyRecommendation;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.model.TrendingStock;
import com.neuralarc.service.StrategyRepository;
import com.neuralarc.service.StrategyService;
import org.junit.jupiter.api.Test;

import javax.swing.JOptionPane;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LuckySimulationPlacementControllerTest {
    @Test
    void startsPaperMonitoringThroughPaperCreationPath() {
        InMemoryRepository repository = new InMemoryRepository();
        LuckySimulationPlacementController controller = controller(repository, true, false);

        LuckySimulationPlacementController.PlacementResult result = controller.place(List.of(selection("NVDA")));

        assertEquals(1, result.created());
        Strategy saved = repository.findAll().getFirst();
        assertEquals("NVDA", saved.symbol());
        assertEquals(StrategyMode.PAPER, saved.mode());
        assertEquals(StrategyStatus.ACTIVE, saved.status());
        assertEquals(10, saved.baseBuyQuantity());
        assertEquals("PAPER_PENDING", saved.latestOrderStatus());
        assertTrue(saved.name().startsWith("I_AM_FEELING_LUCKY_REVIEWED:"));
        assertTrue(saved.lastEvent().contains("Alpaca Paper mode"));
    }

    @Test
    void usesPerSelectionQuantityWhenCreatingStrategy() {
        InMemoryRepository repository = new InMemoryRepository();
        LuckySimulationPlacementController controller = controller(repository, true, false);

        LuckySimulationPlacementController.PlacementResult result = controller.place(List.of(selection("NVDA", 25)));

        assertEquals(1, result.created());
        Strategy saved = repository.findAll().getFirst();
        assertEquals(25, saved.baseBuyQuantity());
        assertEquals(25, saved.buyLimit1Quantity());
        assertEquals(25, saved.buyLimit2Quantity());
    }

    @Test
    void storesLuckyMoverSourceOnCreatedStrategy() {
        InMemoryRepository repository = new InMemoryRepository();
        LuckySimulationPlacementController controller = controller(repository, true, false);

        LuckySimulationPlacementController.PlacementResult result =
                controller.place(List.of(selection("NVDA", 10, "top mover loser")));

        assertEquals(1, result.created());
        Strategy saved = repository.findAll().getFirst();
        assertTrue(saved.name().startsWith("I_AM_FEELING_LUCKY_LOSERS:"));
        assertTrue(saved.lastEvent().contains("Source top mover loser"));
    }

    @Test
    void duplicateWaitingForFillNoStopsPlacementWithoutChanges() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(waitingPaperStrategy("NVDA", "existing-waiting"));

        LuckySimulationPlacementController controller = controller(repository, false, false);
        LuckySimulationPlacementController.PlacementResult result = controller.place(List.of(selection("NVDA")));

        assertTrue(result.canceled());
        assertEquals(0, result.created());
        assertEquals(0, result.replaced());
        assertEquals(1, repository.findAll().size());
        assertEquals("existing-waiting", repository.findAll().getFirst().id());
    }

    @Test
    void duplicateWaitingForFillYesReplacesExistingStrategy() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.save(waitingPaperStrategy("NVDA", "existing-waiting"));

        LuckySimulationPlacementController controller = controller(repository, true, false);
        LuckySimulationPlacementController.PlacementResult result = controller.place(List.of(selection("NVDA")));

        assertEquals(0, result.created());
        assertEquals(1, result.replaced());
        assertEquals(1, repository.findAll().size());
        assertTrue(repository.findAll().stream().noneMatch(strategy -> "existing-waiting".equals(strategy.id())));
    }

    @Test
    void nonWaitingDuplicateFollowsDuplicatePolicyWhenNotAllowed() {
        InMemoryRepository repository = new InMemoryRepository();
        LuckySimulationPlacementController controller = controller(repository, true, false);
        controller.place(List.of(selection("NVDA")));

        LuckySimulationPlacementController.PlacementResult result = controller.place(List.of(selection("NVDA")));

        assertEquals(0, result.created());
        assertEquals(1, result.skipped());
        assertEquals(1, repository.findAll().size());
    }

    @Test
    void nonWaitingDuplicateAllowsSecondStrategyWhenPolicyAllows() {
        InMemoryRepository repository = new InMemoryRepository();
        LuckySimulationPlacementController controller = controller(repository, true, true);
        controller.place(List.of(selection("NVDA")));

        LuckySimulationPlacementController.PlacementResult result = controller.place(List.of(selection("NVDA")));

        assertEquals(1, result.created());
        assertEquals(0, result.skipped());
        assertEquals(2, repository.findAll().size());
    }

    @Test
    void autoAdjustsBaseLimitWhenRecommendationBasePriceIsAboveCurrentPrice() {
        InMemoryRepository repository = new InMemoryRepository();
        LuckySimulationPlacementController controller = controller(repository, true, false);

        LuckySimulationPlacementController.PlacementResult result = controller.place(List.of(selectionWithBaseAboveCurrent("NVDA", 10)));

        assertEquals(1, result.created());
        assertEquals(0, result.skipped());
        Strategy saved = repository.findAll().getFirst();
        assertEquals(new BigDecimal("118.80"), saved.baseBuyLimitPrice());
    }

    @Test
    void autoAdjustsBaseLimitWhenRecommendationBasePriceEqualsCurrentPrice() {
        InMemoryRepository repository = new InMemoryRepository();
        LuckySimulationPlacementController controller = controller(repository, true, false);

        LuckySimulationSelection selection = selectionWithBaseAndCurrent("NVDA", 10,
                new BigDecimal("120.00"), new BigDecimal("120.00"));
        LuckySimulationPlacementController.PlacementResult result = controller.place(List.of(selection));

        assertEquals(1, result.created());
        assertEquals(0, result.skipped());
        Strategy saved = repository.findAll().getFirst();
        assertEquals(new BigDecimal("118.80"), saved.baseBuyLimitPrice());
    }

    @Test
    void usesStockLatestPriceAsCurrentSourcePriorityForAdjustment() {
        InMemoryRepository repository = new InMemoryRepository();
        LuckySimulationPlacementController controller = controller(repository, true, false);

        LuckySimulationSelection selection = selectionWithBaseCurrentAndLatest(
                "NVDA",
                10,
                new BigDecimal("125.00"),
                new BigDecimal("130.00"),
                new BigDecimal("120.00")
        );
        LuckySimulationPlacementController.PlacementResult result = controller.place(List.of(selection));

        assertEquals(1, result.created());
        assertEquals(0, result.skipped());
        Strategy saved = repository.findAll().getFirst();
        assertEquals(new BigDecimal("118.80"), saved.baseBuyLimitPrice());
    }

    @Test
    void fallsBackToRecommendationCurrentWhenStockLatestUnavailable() {
        InMemoryRepository repository = new InMemoryRepository();
        LuckySimulationPlacementController controller = controller(repository, true, false);

        LuckySimulationSelection selection = selectionWithBaseCurrentAndLatest(
                "NVDA",
                10,
                new BigDecimal("125.00"),
                new BigDecimal("121.00"),
                BigDecimal.ZERO
        );
        LuckySimulationPlacementController.PlacementResult result = controller.place(List.of(selection));

        assertEquals(1, result.created());
        assertEquals(0, result.skipped());
        Strategy saved = repository.findAll().getFirst();
        assertEquals(new BigDecimal("119.79"), saved.baseBuyLimitPrice());
    }

    @Test
    void appliesGatewayDefaultsForPollingAndCycleBehavior() {
        InMemoryRepository repository = new InMemoryRepository();
        LuckySimulationPlacementController controller = controller(repository, true, false, 90, true, true);

        LuckySimulationPlacementController.PlacementResult result = controller.place(List.of(selection("NVDA")));

        assertEquals(1, result.created());
        Strategy saved = repository.findAll().getFirst();
        assertEquals(90, saved.pollingIntervalSeconds());
        assertTrue(saved.restartAfterExitEnabled());
        assertTrue(saved.resubmitOnExpiryEnabled());
    }

    private LuckySimulationPlacementController controller(InMemoryRepository repository, boolean replaceChoice, boolean allowDuplicates) {
        return controller(repository, replaceChoice, allowDuplicates, 60, true, true);
    }

    private LuckySimulationPlacementController controller(
            InMemoryRepository repository,
            boolean replaceChoice,
            boolean allowDuplicates,
            int defaultPollingSeconds,
            boolean defaultRepeatCycleAfterProfitExit,
            boolean defaultResubmitOnExpiry
    ) {
        return new LuckySimulationPlacementController(new LuckySimulationPlacementController.Gateway() {
            @Override public StrategyRepository repository() { return repository; }
            @Override public StrategyService.StrategyCreationResult createPaperStrategy(Strategy strategy) {
                strategy.setStatus(StrategyStatus.ACTIVE);
                repository.save(strategy);
                return StrategyService.StrategyCreationResult.success(strategy.id(), "order-row", "alpaca-paper-order", "client-order");
            }
            @Override public boolean confirmReplaceWaitingPaperStrategy(String symbol) { return replaceChoice; }
            @Override public boolean allowDuplicateSymbols() { return allowDuplicates; }
            @Override public int defaultStrategyPollingSeconds() { return defaultPollingSeconds; }
            @Override public boolean defaultRepeatCycleAfterProfitExitEnabled() { return defaultRepeatCycleAfterProfitExit; }
            @Override public boolean defaultResubmitOnExpiryEnabled() { return defaultResubmitOnExpiry; }
            @Override public void cancelAndDeletePaperStrategy(String strategyId) { repository.deleteById(strategyId); }
            @Override public void afterPlacement() {}
            @Override public void log(String message) {}
        });
    }

    private Strategy waitingPaperStrategy(String symbol, String id) {
        StrategyConfig config = new StrategyConfig(
                symbol,
                new BigDecimal("125.00"),
                10,
                new BigDecimal("120.00"),
                new BigDecimal("130.00"),
                new BigDecimal("119.00"),
                10,
                new BigDecimal("118.00"),
                10,
                60,
                true,
                false
        );
        Strategy strategy = Strategy.fromConfig(id, "existing paper", config, StrategyMode.PAPER);
        strategy.setStatus(StrategyStatus.ACTIVE);
        strategy.setCurrentState(StrategyLifecycleState.BASE_BUY_PLACED);
        strategy.setLatestOrderStatus("accepted");
        return strategy;
    }

    private LuckySimulationSelection selection(String symbol) {
        return selection(symbol, 10);
    }

    private LuckySimulationSelection selection(String symbol, int quantity) {
        return selection(symbol, quantity, "");
    }

    private LuckySimulationSelection selection(String symbol, int quantity, String reason) {
        StrategyRecommendation recommendation = recommendation(symbol, RecommendationType.SHORT_TERM, new BigDecimal("125.00"));
        AutoAnalyzeBundle bundle = new AutoAnalyzeBundle(
                result(symbol),
                recommendation,
                recommendation(symbol, RecommendationType.HIGH_RISK_SHORT_TERM, new BigDecimal("126.00")),
                recommendation(symbol, RecommendationType.LONG_TERM, new BigDecimal("120.00"))
        );
        return new LuckySimulationSelection(
                new TrendingStock(symbol, "", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, reason, BigDecimal.TEN),
                bundle,
                RecommendationType.SHORT_TERM,
                quantity
        );
    }

    private LuckySimulationSelection selectionWithBaseAboveCurrent(String symbol, int quantity) {
        return selectionWithBaseAndCurrent(symbol, quantity, new BigDecimal("125.00"), new BigDecimal("120.00"));
    }

    private LuckySimulationSelection selectionWithBaseAndCurrent(
            String symbol,
            int quantity,
            BigDecimal basePrice,
            BigDecimal currentPrice
    ) {
        return selectionWithBaseCurrentAndLatest(symbol, quantity, basePrice, currentPrice, BigDecimal.ZERO);
    }

    private LuckySimulationSelection selectionWithBaseCurrentAndLatest(
            String symbol,
            int quantity,
            BigDecimal basePrice,
            BigDecimal currentPrice,
            BigDecimal latestPrice
    ) {
        StrategyRecommendation recommendation = recommendation(symbol, RecommendationType.SHORT_TERM,
                basePrice, currentPrice);
        AutoAnalyzeBundle bundle = new AutoAnalyzeBundle(
                result(symbol),
                recommendation,
                recommendation(symbol, RecommendationType.HIGH_RISK_SHORT_TERM, basePrice.add(BigDecimal.ONE), currentPrice),
                recommendation(symbol, RecommendationType.LONG_TERM, basePrice.subtract(BigDecimal.ONE), currentPrice)
        );
        return new LuckySimulationSelection(
                new TrendingStock(symbol, "", latestPrice, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "", BigDecimal.TEN),
                bundle,
                RecommendationType.SHORT_TERM,
                quantity
        );
    }

    private AutoAnalyzeResult result(String symbol) {
        return new AutoAnalyzeResult(
                symbol,
                LocalDate.now().minusMonths(12),
                LocalDate.now(),
                15,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                BigDecimal.TEN,
                true,
                BigDecimal.TEN,
                BigDecimal.TEN,
                20,
                200,
                Instant.now()
        );
    }

    private StrategyRecommendation recommendation(String symbol, RecommendationType type, BigDecimal basePrice) {
        return recommendation(symbol, type, basePrice, basePrice);
    }

    private StrategyRecommendation recommendation(String symbol, RecommendationType type, BigDecimal basePrice, BigDecimal currentPrice) {
        return new StrategyRecommendation(
                symbol,
                type,
                basePrice,
                basePrice,
                basePrice,
                basePrice,
                currentPrice,
                basePrice.subtract(BigDecimal.ONE),
                basePrice.add(BigDecimal.ONE),
                BigDecimal.ONE,
                basePrice,
                basePrice,
                MarketMode.ACCUMULATION,
                ShortTermMarketMode.RANGE_ENTRY,
                "Test recommendation",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                basePrice.subtract(BigDecimal.ONE),
                basePrice.subtract(new BigDecimal("2")),
                basePrice.subtract(new BigDecimal("3")),
                basePrice.add(new BigDecimal("5")),
                basePrice.add(new BigDecimal("2")),
                basePrice.add(new BigDecimal("5")),
                "UP",
                "HIGH",
                BigDecimal.ONE,
                80,
                RecommendationAction.BUY,
                "",
                false
        );
    }

    private static final class InMemoryRepository implements StrategyRepository {
        private final List<Strategy> strategies = new ArrayList<>();

        @Override
        public void save(Strategy strategy) {
            deleteById(strategy.id());
            strategies.add(strategy);
        }

        @Override
        public Optional<Strategy> findById(String id) {
            return strategies.stream().filter(strategy -> strategy.id().equals(id)).findFirst();
        }

        @Override
        public List<Strategy> findAll() {
            return new ArrayList<>(strategies);
        }

        @Override
        public List<Strategy> findActive() {
            return strategies.stream().filter(strategy -> strategy.status() == StrategyStatus.ACTIVE).toList();
        }

        @Override
        public void deleteById(String id) {
            strategies.removeIf(strategy -> strategy.id().equals(id));
        }
    }
}
