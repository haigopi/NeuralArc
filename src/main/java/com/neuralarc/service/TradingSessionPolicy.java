package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.model.Strategy;

import java.time.Instant;

/**
 * Resolves whether a strategy's symbol is tradable in the current market session.
 * Extended-hours overnight windows are enabled only for symbols that broker metadata
 * marks as overnight-eligible (e.g. Alpaca 24x5 assets).
 */
public class TradingSessionPolicy {
    private final MarketHoursService marketHoursService;
    private final AlpacaClient alpacaClient;

    public TradingSessionPolicy(MarketHoursService marketHoursService, AlpacaClient alpacaClient) {
        this.marketHoursService = marketHoursService;
        this.alpacaClient = alpacaClient;
    }

    public boolean isTradingSessionOpen(Strategy strategy, AppSettingsService.AppSettings settings, Instant now) {
        if (strategy == null) {
            return false;
        }
        boolean extendedEnabled = settings != null && settings.extendedHoursTradingEnabled();
        if (!extendedEnabled) {
            return marketHoursService.isTradingSessionOpen(now, false);
        }
        boolean overnightEligible = alpacaClient != null && alpacaClient.supportsOvernightSession(strategy.symbol());
        return marketHoursService.isTradingSessionOpen(now, true, overnightEligible);
    }
}

