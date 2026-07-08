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

final class BrokerSnapshotLoader {
    private BrokerSnapshotLoader() {
    }

    static Map<String, Position> loadPositionSnapshots(
            List<Strategy> stored,
            Function<ApplicationMode, HttpAlpacaClient> clientResolver,
            Predicate<Strategy> includeStrategy
    ) {
        return loadPositionSnapshots(stored, clientResolver, includeStrategy, null);
    }

    static Map<String, Position> loadPositionSnapshots(
            List<Strategy> stored,
            Function<ApplicationMode, HttpAlpacaClient> clientResolver,
            Predicate<Strategy> includeStrategy,
            BiFunction<ApplicationMode, HttpAlpacaClient, List<AlpacaPositionData>> positionResolver
    ) {
        if (stored == null || stored.isEmpty() || clientResolver == null) {
            return Map.of();
        }
        Map<String, Position> snapshots = new LinkedHashMap<>();
        loadPositionSnapshotsForMode(stored, StrategyMode.PAPER, ApplicationMode.PAPER, clientResolver, includeStrategy, positionResolver, snapshots);
        loadPositionSnapshotsForMode(stored, StrategyMode.LIVE, ApplicationMode.LIVE, clientResolver, includeStrategy, positionResolver, snapshots);
        return snapshots;
    }

    private static void loadPositionSnapshotsForMode(
            List<Strategy> stored,
            StrategyMode mode,
            ApplicationMode applicationMode,
            Function<ApplicationMode, HttpAlpacaClient> clientResolver,
            Predicate<Strategy> includeStrategy,
            BiFunction<ApplicationMode, HttpAlpacaClient, List<AlpacaPositionData>> positionResolver,
            Map<String, Position> target
    ) {
        List<Strategy> strategiesForMode = stored.stream()
                .filter(strategy -> strategy.mode() == mode)
                .filter(strategy -> includeStrategy == null || includeStrategy.test(strategy))
                .filter(strategy -> strategy.symbol() != null && !strategy.symbol().isBlank())
                .toList();
        if (strategiesForMode.isEmpty()) {
            return;
        }
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
        for (Strategy strategy : strategiesForMode) {
            target.put(strategy.id(), buildPositionSnapshot(strategy.symbol(), positionsBySymbol.get(strategy.symbol().toUpperCase(Locale.ROOT)), latestPrices.get(strategy.symbol().toUpperCase(Locale.ROOT))));
        }
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
        Position snapshot = new Position(symbol == null ? "" : symbol);
        boolean positionMarketPriceApplied = false;
        if (remotePosition != null && remotePosition.exists()) {
            int quantity = remotePosition.quantity().setScale(0, RoundingMode.DOWN).intValue();
            if (quantity > 0) {
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
