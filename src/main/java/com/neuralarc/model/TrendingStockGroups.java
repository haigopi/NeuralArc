package com.neuralarc.model;

import java.util.List;

public record TrendingStockGroups(
        List<TrendingStock> gainers,
        List<TrendingStock> losers
) {
    public TrendingStockGroups {
        gainers = gainers == null ? List.of() : List.copyOf(gainers);
        losers = losers == null ? List.of() : List.copyOf(losers);
    }

    public boolean empty() {
        return gainers.isEmpty() && losers.isEmpty();
    }
}
