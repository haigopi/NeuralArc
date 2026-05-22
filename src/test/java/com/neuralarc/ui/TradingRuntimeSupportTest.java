package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.api.TradingApi;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.service.FileStrategyExecutionEventRepository;
import com.neuralarc.service.FileStrategyOrderRepository;
import com.neuralarc.service.FileStrategyRepository;
import com.neuralarc.service.MarketHoursService;
import com.neuralarc.service.StrategyPollingService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradingRuntimeSupportTest {
    @Test
    void attemptConnectionReturnsConnectedApiFromFactory() throws Exception {
        TradingRuntimeSupport support = support((ignoredBrokerType, ignoredMode) -> new FakeTradingApi(true), HttpAlpacaClient::new);

        TradingRuntimeSupport.ConnectionAttemptResult result = support.attemptConnection(
                BrokerType.ALPACA,
                ApplicationMode.PAPER,
                "key",
                "secret"
        );

        assertTrue(result.connected());
        assertNotNull(result.tradingApi());
        assertEquals("Connected to ALPACA (PAPER)", result.message());
    }

    @Test
    void attemptConnectionFlagsMissingBroker() throws Exception {
        TradingRuntimeSupport support = support((ignoredBrokerType, ignoredMode) -> new FakeTradingApi(true), HttpAlpacaClient::new);

        TradingRuntimeSupport.ConnectionAttemptResult result = support.attemptConnection(
                null,
                ApplicationMode.PAPER,
                "key",
                "secret"
        );

        assertFalse(result.connected());
        assertTrue(result.brokerMissing());
    }

    @Test
    void createRuntimeServicesBuildsStrategyAndPollingServices() throws Exception {
        TradingRuntimeSupport support = support((ignoredBrokerType, ignoredMode) -> new FakeTradingApi(true), HttpAlpacaClient::new);
        HttpAlpacaClient client = new HttpAlpacaClient("key", "secret", "https://paper-api.alpaca.markets", "https://data.alpaca.markets", false);

        TradingRuntimeSupport.RuntimeServices services = support.createRuntimeServices(
                client,
                ApplicationMode.PAPER,
                new com.neuralarc.service.StrategyPollingService.PollListener() {}
        );

        assertNotNull(services.strategyService());
        assertNotNull(services.strategyPollingService());
        services.strategyPollingService().shutdown();
    }

    @Test
    void createRuntimeServicesScopesPollingToApplicationMode() throws Exception {
        TradingRuntimeSupport support = support((ignoredBrokerType, ignoredMode) -> new FakeTradingApi(true), HttpAlpacaClient::new);
        HttpAlpacaClient client = new HttpAlpacaClient("key", "secret", "https://paper-api.alpaca.markets", "https://data.alpaca.markets", false);

        TradingRuntimeSupport.RuntimeServices paperServices = support.createRuntimeServices(
                client,
                ApplicationMode.PAPER,
                new StrategyPollingService.PollListener() {}
        );
        TradingRuntimeSupport.RuntimeServices liveServices = support.createRuntimeServices(
                client,
                ApplicationMode.LIVE,
                new StrategyPollingService.PollListener() {}
        );

        assertEquals(StrategyMode.PAPER, pollingMode(paperServices.strategyPollingService()));
        assertEquals(StrategyMode.LIVE, pollingMode(liveServices.strategyPollingService()));
        paperServices.strategyPollingService().shutdown();
        liveServices.strategyPollingService().shutdown();
    }

    private StrategyMode pollingMode(StrategyPollingService service) throws Exception {
        Field field = StrategyPollingService.class.getDeclaredField("strategyMode");
        field.setAccessible(true);
        return (StrategyMode) field.get(service);
    }

    private TradingRuntimeSupport support(
            TradingRuntimeSupport.TradingApiCreator tradingApiCreator,
            TradingRuntimeSupport.AlpacaClientCreator alpacaClientCreator
    ) throws Exception {
        Path tempDir = Files.createTempDirectory("neuralarc-runtime-support-test");
        return new TradingRuntimeSupport(
                new FileStrategyRepository(tempDir.resolve("strategies.json")),
                new FileStrategyOrderRepository(tempDir.resolve("orders.json")),
                new FileStrategyExecutionEventRepository(tempDir.resolve("events.json")),
                new AppSettingsService(),
                new MarketHoursService(),
                tradingApiCreator,
                alpacaClientCreator
        );
    }

    private static final class FakeTradingApi implements TradingApi {
        private final boolean connected;

        private FakeTradingApi(boolean connected) {
            this.connected = connected;
        }

        @Override
        public void authenticate(String apiKey, String apiSecret) {
        }

        @Override
        public boolean testConnection() {
            return connected;
        }

        @Override
        public BigDecimal getLatestPrice(String symbol) {
            return BigDecimal.ZERO;
        }

        @Override
        public com.neuralarc.model.Position getPosition(String symbol) {
            return new com.neuralarc.model.Position(symbol == null ? "" : symbol);
        }

        @Override
        public com.neuralarc.model.OrderResult placeBuyOrder(String symbol, int quantity, BigDecimal limitPrice) {
            return com.neuralarc.model.OrderResult.fail(symbol, quantity, "not implemented");
        }

        @Override
        public com.neuralarc.model.OrderResult placeSellOrder(String symbol, int quantity, BigDecimal limitPrice) {
            return com.neuralarc.model.OrderResult.fail(symbol, quantity, "not implemented");
        }

        @Override
        public boolean cancelOpenOrdersForSymbol(String symbol) {
            return false;
        }
    }
}
