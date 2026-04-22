package com.neuralarc.api;

import com.neuralarc.model.BrokerType;

public final class TradingApiFactory {
    private TradingApiFactory() {}

    public static TradingApi create(BrokerType type) {
        return switch (type) {
            case ALPACA -> new AlpacaTradingApi();
            case MOCK -> new MockTradingApi();
        };
    }
}
