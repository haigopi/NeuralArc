package com.neuralarc.ui;

import com.neuralarc.api.AlpacaPositionData;
import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.Position;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyMode;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

final class BrokerSnapshotLoader {
    private BrokerSnapshotLoader() {
    }

    static Map<String, Position> loadPositionSnapshots(
            List<Strategy> stored,
            Function<ApplicationMode, HttpAlpacaClient> clientResolver,
            Predicate<Strategy> includeStrategy
    ) {
        return loadPositionSnapshots(stored, clientResolver, includeStrategy, null, null);
    }

    static Map<String, Position> loadPositionSnapshots(
            List<Strategy> stored,
            Function<ApplicationMode, HttpAlpacaClient> clientResolver,
            Predicate<Strategy> includeStrategy,
            BiFunction<ApplicationMode, HttpAlpacaClient, List<AlpacaPositionData>> positionResolver
    ) {
        return loadPositionSnapshots(stored, clientResolver, includeStrategy, positionResolver, null);
    }

    /**
     * @param localShareClaim shares each strategy's own filled orders say it holds. Used to split one
     *                        broker position across several strategies on the same symbol; when null
     *                        the whole position lands on the oldest of them instead of on all.
     */
    static Map<String, Position> loadPositionSnapshots(
            List<Strategy> stored,
            Function<ApplicationMode, HttpAlpacaClient> clientResolver,
            Predicate<Strategy> includeStrategy,
            BiFunction<ApplicationMode, HttpAlpacaClient, List<AlpacaPositionData>> positionResolver,
            ToIntFunction<Strategy> localShareClaim
    ) {
        return loadPositionSnapshots(stored, stored, clientResolver, includeStrategy, positionResolver, localShareClaim);
    }

    /**
     * @param stored             the strategies to produce snapshots for — typically only those due a
     *                           refresh this tick.
     * @param allocationPopulation every strategy that could own part of a broker position, whether or
     *                           not it is being refreshed now. Splitting a position must consider all
     *                           of them: judged against the refresh batch alone, a newly added row can
     *                           be the only claimant on its symbol and take the whole position — which
     *                           is how a freshly imported row came to display an existing holding's
     *                           entry price and P&amp;L.
     */
    static Map<String, Position> loadPositionSnapshots(
            List<Strategy> stored,
            List<Strategy> allocationPopulation,
            Function<ApplicationMode, HttpAlpacaClient> clientResolver,
            Predicate<Strategy> includeStrategy,
            BiFunction<ApplicationMode, HttpAlpacaClient, List<AlpacaPositionData>> positionResolver,
            ToIntFunction<Strategy> localShareClaim
    ) {
        if (stored == null || stored.isEmpty() || clientResolver == null) {
            return Map.of();
        }
        List<Strategy> population = allocationPopulation == null || allocationPopulation.isEmpty()
                ? stored
                : allocationPopulation;
        Map<String, Position> snapshots = new LinkedHashMap<>();
        loadPositionSnapshotsForMode(stored, population, StrategyMode.PAPER, ApplicationMode.PAPER, clientResolver, includeStrategy, positionResolver, localShareClaim, snapshots);
        loadPositionSnapshotsForMode(stored, population, StrategyMode.LIVE, ApplicationMode.LIVE, clientResolver, includeStrategy, positionResolver, localShareClaim, snapshots);
        return snapshots;
    }

    private static void loadPositionSnapshotsForMode(
            List<Strategy> stored,
            List<Strategy> allocationPopulation,
            StrategyMode mode,
            ApplicationMode applicationMode,
            Function<ApplicationMode, HttpAlpacaClient> clientResolver,
            Predicate<Strategy> includeStrategy,
            BiFunction<ApplicationMode, HttpAlpacaClient, List<AlpacaPositionData>> positionResolver,
            ToIntFunction<Strategy> localShareClaim,
            Map<String, Position> target
    ) {
        List<Strategy> strategiesForMode = eligible(stored, mode, includeStrategy);
        if (strategiesForMode.isEmpty()) {
            return;
        }
        List<Strategy> populationForMode = eligible(allocationPopulation, mode, includeStrategy);
        HttpAlpacaClient client = clientResolver.apply(applicationMode);
        if (client == null) {
            return;
        }
        List<String> symbols = uniqueSymbols(strategiesForMode);
        if (symbols.isEmpty()) {
            return;
        }
        Map<String, BigDecimal> latestPrices = client.getLatestPrices(symbols);
        List<AlpacaPositionData> positions = positionResolver == null
                ? client.getPositions()
                : positionResolver.apply(applicationMode, client);
        if (positions == null) {
            positions = List.of();
        }
        Map<String, AlpacaPositionData> positionsBySymbol = positions.stream()
                .filter(position -> position != null && position.symbol() != null && !position.symbol().isBlank())
                .collect(java.util.stream.Collectors.toMap(
                        position -> position.symbol().toUpperCase(Locale.ROOT),
                        position -> position,
                        (left, ignored) -> left,
                        LinkedHashMap::new
                ));
        Map<String, Integer> allocationByStrategyId =
                allocateSharesAcrossSameSymbolStrategies(populationForMode, positionsBySymbol, localShareClaim);
        for (Strategy strategy : strategiesForMode) {
            String symbol = strategy.symbol().toUpperCase(Locale.ROOT);
            target.put(strategy.id(), buildPositionSnapshot(
                    strategy.symbol(),
                    positionsBySymbol.get(symbol),
                    latestPrices.get(symbol),
                    allocationByStrategyId.get(strategy.id())
            ));
        }
    }

    private static List<Strategy> eligible(List<Strategy> strategies, StrategyMode mode, Predicate<Strategy> includeStrategy) {
        return strategies.stream()
                .filter(strategy -> strategy != null && strategy.mode() == mode)
                .filter(strategy -> includeStrategy == null || includeStrategy.test(strategy))
                .filter(strategy -> strategy.symbol() != null && !strategy.symbol().isBlank())
                .toList();
    }

    /**
     * One broker position per symbol, several local strategies possible on that symbol: decide how
     * many of the broker's shares each row may show so a single position is never counted twice.
     */
    private static Map<String, Integer> allocateSharesAcrossSameSymbolStrategies(
            List<Strategy> strategiesForMode,
            Map<String, AlpacaPositionData> positionsBySymbol,
            ToIntFunction<Strategy> localShareClaim
    ) {
        Map<String, List<Strategy>> bySymbol = new LinkedHashMap<>();
        for (Strategy strategy : strategiesForMode) {
            bySymbol.computeIfAbsent(strategy.symbol().toUpperCase(Locale.ROOT), ignored -> new ArrayList<>()).add(strategy);
        }
        Map<String, Integer> allocation = new LinkedHashMap<>();
        for (Map.Entry<String, List<Strategy>> entry : bySymbol.entrySet()) {
            AlpacaPositionData position = positionsBySymbol.get(entry.getKey());
            int brokerShares = brokerShares(position);
            List<BrokerPositionAllocator.Claim> claims = entry.getValue().stream()
                    .map(strategy -> new BrokerPositionAllocator.Claim(
                            strategy.id(),
                            localShareClaim == null ? 0 : localShareClaim.applyAsInt(strategy),
                            strategy.createdAt(),
                            StrategyRowVisibility.hiddenFromCurrentTab(strategy)))
                    .toList();
            allocation.putAll(BrokerPositionAllocator.allocate(brokerShares, claims));
        }
        return allocation;
    }

    private static int brokerShares(AlpacaPositionData position) {
        if (position == null || !position.hasExposure() || position.quantity() == null) {
            return 0;
        }
        // DOWN rounds toward zero, so a short keeps its sign without ever deepening.
        return position.quantity().setScale(0, RoundingMode.DOWN).intValue();
    }

    private static List<String> uniqueSymbols(List<Strategy> strategies) {
        List<String> symbols = new ArrayList<>();
        for (Strategy strategy : strategies) {
            String symbol = strategy.symbol().toUpperCase(Locale.ROOT);
            if (!symbols.contains(symbol)) {
                symbols.add(symbol);
            }
        }
        return symbols;
    }

    static Position buildPositionSnapshot(String symbol, AlpacaPositionData remotePosition, BigDecimal latestPrice) {
        return buildPositionSnapshot(symbol, remotePosition, latestPrice, null);
    }

    /**
     * @param allocatedShares shares of the broker position this strategy may show, or null to show
     *                        the whole position (a symbol held by a single strategy).
     */
    static Position buildPositionSnapshot(
            String symbol,
            AlpacaPositionData remotePosition,
            BigDecimal latestPrice,
            Integer allocatedShares
    ) {
        Position snapshot = new Position(symbol == null ? "" : symbol);
        boolean positionMarketPriceApplied = false;
        if (remotePosition != null && remotePosition.hasExposure()) {
            int quantity = allocatedShares == null
                    ? remotePosition.quantity().setScale(0, RoundingMode.DOWN).intValue()
                    : allocatedShares;
            if (quantity != 0) {
                snapshot.applyBuy(quantity, remotePosition.avgEntryPrice());
            }
            if (remotePosition.marketPrice() != null && remotePosition.marketPrice().compareTo(BigDecimal.ZERO) > 0) {
                snapshot.setLastPrice(remotePosition.marketPrice());
                positionMarketPriceApplied = true;
            }
        }
        if (!positionMarketPriceApplied && latestPrice != null && latestPrice.compareTo(BigDecimal.ZERO) > 0) {
            snapshot.setLastPrice(latestPrice);
        }
        return snapshot;
    }
}
