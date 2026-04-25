package com.neuralarc.api;

import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;

public final class TradingApiFactory {
    private TradingApiFactory() {}

    private static final AlpacaEndpointConfig ALPACA_ENDPOINT_CONFIG = AlpacaEndpointConfig.load();

    public static TradingApi create(BrokerType type) {
        return create(type, ApplicationMode.PAPER);
    }

    public static TradingApi create(BrokerType type, ApplicationMode mode) {
        return new AlpacaTradingApi(ALPACA_ENDPOINT_CONFIG.endpointFor(mode));
    }
}
