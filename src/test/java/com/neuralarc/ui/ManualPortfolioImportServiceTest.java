package com.neuralarc.ui;

import com.neuralarc.api.AlpacaMarketDataApi;
import com.neuralarc.api.AlpacaMarketDataException;
import com.neuralarc.model.MarketBar;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.service.StrategyRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManualPortfolioImportServiceTest {
    @Test
    void parsesExampleAlertBlocks() {
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = PortfolioStockImportDialog.parse("""
                @everyone type: DT/Swing
                Symbol: $MDB
                Entry: Entered @ 451
                Stop: Below 446 (Aggressive) - 15% from the entry.
                Targets: 466

                @everyone type: DT/Swing
                Symbol: $TEAM
                Entry: Entered @ 191.60
                Stop: Below 183 (Very strict stop) (Aggressive) - 15% from the entry.
                Targets: 204 - 218
                """);

        assertEquals(2, drafts.size());
        assertEquals("MDB", drafts.getFirst().symbol());
        assertEquals(new BigDecimal("451"), drafts.getFirst().recommendedEntry());
        assertEquals(new BigDecimal("446"), drafts.getFirst().stopLoss());
        assertEquals(List.of(new BigDecimal("204"), new BigDecimal("218")), drafts.get(1).targets());
    }

    @Test
    void parsesBullishTargetListFormat() {
        List<PortfolioStockImportDialog.ImportedStockDraft> drafts = PortfolioStockImportDialog.parse("""
                WALL STREET’S MOST BULLISH PRICE TARGETS FOR POPULAR STOCKS

                AI Utilities
                • $IREN $105 (+151%)
                • $NBIS $410 (+87%)
                • $HUT $273 (+238%)

                Fintech
                • $SOFI $30 (+59%)
                • $COIN $330 (+77%
                """);

        assertEquals(5, drafts.size());
        assertEquals("IREN", drafts.getFirst().symbol());
        assertEquals(new BigDecimal("41.83"), drafts.getFirst().recommendedEntry());
        assertEquals(new BigDecimal("35.56"), drafts.getFirst().stopLoss());
        assertEquals(List.of(new BigDecimal("105")), drafts.getFirst().targets());
        assertEquals("COIN", drafts.get(4).symbol());
        assertEquals(new BigDecimal("186.44"), drafts.get(4).recommendedEntry());
    }

    @Test
    void importsManualStrategiesUsingTwoWeekLowAsBaseWhenLower() {
        InMemoryRepository repository = new InMemoryRepository();
        ManualPortfolioImportService service = new ManualPortfolioImportService(new FakeGateway(repository));

        ManualPortfolioImportService.ImportResult result = service.importDrafts(List.of(
                new PortfolioStockImportDialog.ImportedStockDraft(
                        "MDB",
                        new BigDecimal("451"),
                        new BigDecimal("446"),
                        List.of(new BigDecimal("466"))
                )
        ));

        assertEquals(List.of("MDB"), result.importedSymbols());
        Strategy saved = repository.findAll().getFirst();
        assertEquals("MANUAL_ADDITION: MDB Paper", saved.name());
        assertEquals(new BigDecimal("430.00"), saved.baseBuyLimitPrice());
        assertEquals(new BigDecimal("417.10"), saved.stopLossPrice());
        assertEquals(new BigDecimal("466.00"), saved.targetSellPrice());
        assertEquals(1, saved.baseBuyQuantity());
        assertEquals("PAPER_PENDING", saved.latestOrderStatus());
        assertTrue(saved.lastEvent().contains("Manual addition imported for pending review"));
    }

    @Test
    void keepsRecommendedEntryWhenTwoWeekLowIsUnavailable() {
        InMemoryRepository repository = new InMemoryRepository();
        FakeGateway gateway = new FakeGateway(repository);
        gateway.marketDataApi = null;
        ManualPortfolioImportService service = new ManualPortfolioImportService(gateway);

        service.importDrafts(List.of(
                new PortfolioStockImportDialog.ImportedStockDraft(
                        "TEAM",
                        new BigDecimal("191.60"),
                        new BigDecimal("183"),
                        List.of(new BigDecimal("204"), new BigDecimal("218"))
                )
        ));

        Strategy saved = repository.findAll().getFirst();
        assertEquals(new BigDecimal("191.60"), saved.baseBuyLimitPrice());
        assertEquals(new BigDecimal("218.00"), saved.targetSellPrice());
    }

    private static final class FakeGateway implements ManualPortfolioImportService.Gateway {
        private final InMemoryRepository repository;
        private AlpacaMarketDataApi marketDataApi = new AlpacaMarketDataApi() {
            @Override
            public List<MarketBar> getDailyBars(String symbol, LocalDate startDate, LocalDate endDate) throws AlpacaMarketDataException {
                return List.of(
                        new MarketBar(symbol, "2026-08-01", BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("440"), BigDecimal.ONE, BigDecimal.ONE),
                        new MarketBar(symbol, "2026-08-02", BigDecimal.ONE, BigDecimal.ONE, new BigDecimal("430"), BigDecimal.ONE, BigDecimal.ONE)
                );
            }

            @Override
            public List<MarketBar> getIntradayBars(String symbol, LocalDate startDate, LocalDate endDate, int intervalMinutes) {
                return List.of();
            }
        };

        private FakeGateway(InMemoryRepository repository) {
            this.repository = repository;
        }

        @Override public StrategyRepository repository() { return repository; }
        @Override public String targetWorkspaceId() { return null; }
        @Override public StrategyMode targetMode() { return StrategyMode.PAPER; }
        @Override public boolean allowDuplicateSymbols() { return false; }
        @Override public int defaultPollingSeconds() { return 60; }
        @Override public boolean defaultRepeatCycleAfterProfitExitEnabled() { return true; }
        @Override public boolean defaultResubmitOnExpiryEnabled() { return true; }
        @Override public AlpacaMarketDataApi marketDataApi() { return marketDataApi; }
        @Override public void assignWorkspace(Strategy strategy, String workspaceId) { }
    }

    private static final class InMemoryRepository implements StrategyRepository {
        private final List<Strategy> strategies = new ArrayList<>();

        @Override public void save(Strategy strategy) {
            deleteById(strategy.id());
            strategies.add(strategy);
        }
        @Override public Optional<Strategy> findById(String id) {
            return strategies.stream().filter(strategy -> strategy.id().equals(id)).findFirst();
        }
        @Override public List<Strategy> findAll() { return strategies; }
        @Override public List<Strategy> findActive() { return strategies; }
        @Override public void deleteById(String id) {
            strategies.removeIf(strategy -> strategy.id().equals(id));
        }
    }
}
