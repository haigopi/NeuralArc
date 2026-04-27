package com.neuralarc.api;

import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.util.AppMetadata;

public final class TradingApiFactory {
    private TradingApiFactory() {}

    public static TradingApi create(BrokerType type) {
        return create(type, ApplicationMode.PAPER);
    }

    public static TradingApi create(BrokerType type, ApplicationMode mode) {
        return new AlpacaTradingApi(AppMetadata.alpacaTradingBaseUrl(mode) + "/v2");
    }
}
