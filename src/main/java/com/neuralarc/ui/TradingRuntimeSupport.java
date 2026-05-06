package com.neuralarc.ui;

import com.neuralarc.api.HttpAlpacaClient;
import com.neuralarc.api.TradingApi;
import com.neuralarc.api.TradingApiFactory;
import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.service.AppSettingsService;
import com.neuralarc.service.MarketHoursService;
import com.neuralarc.service.StrategyExecutionEventRepository;
import com.neuralarc.service.StrategyOrderRepository;
import com.neuralarc.service.StrategyPollingService;
import com.neuralarc.service.StrategyRepository;
import com.neuralarc.service.StrategyService;
import com.neuralarc.service.StrategyValidator;
import com.neuralarc.util.AppMetadata;

public final class TradingRuntimeSupport {
    private final StrategyRepository strategyRepository;
    private final StrategyOrderRepository strategyOrderRepository;
    private final StrategyExecutionEventRepository strategyEventRepository;
    private final AppSettingsService appSettingsService;
    private final MarketHoursService marketHoursService;
    private final TradingApiCreator tradingApiCreator;
    private final AlpacaClientCreator alpacaClientCreator;

    public TradingRuntimeSupport(
            StrategyRepository strategyRepository,
            StrategyOrderRepository strategyOrderRepository,
            StrategyExecutionEventRepository strategyEventRepository,
            AppSettingsService appSettingsService,
            MarketHoursService marketHoursService
    ) {
        this(
                strategyRepository,
                strategyOrderRepository,
                strategyEventRepository,
                appSettingsService,
                marketHoursService,
                TradingApiFactory::create,
                HttpAlpacaClient::new
        );
    }

    TradingRuntimeSupport(
            StrategyRepository strategyRepository,
            StrategyOrderRepository strategyOrderRepository,
            StrategyExecutionEventRepository strategyEventRepository,
            AppSettingsService appSettingsService,
            MarketHoursService marketHoursService,
            TradingApiCreator tradingApiCreator,
            AlpacaClientCreator alpacaClientCreator
    ) {
        this.strategyRepository = strategyRepository;
        this.strategyOrderRepository = strategyOrderRepository;
        this.strategyEventRepository = strategyEventRepository;
        this.appSettingsService = appSettingsService;
        this.marketHoursService = marketHoursService;
        this.tradingApiCreator = tradingApiCreator;
        this.alpacaClientCreator = alpacaClientCreator;
    }

    public RuntimeClients createClients(SettingsDialog settingsDialog, String runtimeApiKey, String runtimeApiSecret) {
        return new RuntimeClients(
                createModeClient(settingsDialog, runtimeApiKey, runtimeApiSecret, ApplicationMode.PAPER),
                createModeClient(settingsDialog, runtimeApiKey, runtimeApiSecret, ApplicationMode.LIVE)
        );
    }

    public RuntimeServices createRuntimeServices(
            HttpAlpacaClient runtimeClient,
            ApplicationMode mode,
            StrategyPollingService.PollListener pollListener
    ) {
        if (runtimeClient == null) {
            return new RuntimeServices(null, null);
        }
        StrategyService strategyService = createStrategyService(
                runtimeClient,
                mode == ApplicationMode.LIVE ? StrategyMode.LIVE : StrategyMode.PAPER
        );
        StrategyPollingService pollingService = new StrategyPollingService(
                strategyRepository,
                strategyOrderRepository,
                strategyEventRepository,
                runtimeClient,
                appSettingsService,
                marketHoursService
        );
        pollingService.setPollListener(pollListener);
        return new RuntimeServices(strategyService, pollingService);
    }

    public StrategyService createStrategyService(HttpAlpacaClient runtimeClient, StrategyMode strategyMode) {
        if (runtimeClient == null) {
            return null;
        }
        return new StrategyService(
                strategyRepository,
                strategyOrderRepository,
                strategyEventRepository,
                runtimeClient,
                new StrategyValidator(),
                AppMetadata.liveTradingEnabled(),
                strategyMode == null ? StrategyMode.PAPER : strategyMode,
                appSettingsService,
                marketHoursService
        );
    }

    public ConnectionAttemptResult attemptConnection(BrokerType brokerType, ApplicationMode mode, String apiKey, String apiSecret) {
        if (brokerType == null) {
            return ConnectionAttemptResult.forMissingBroker();
        }
        if (mode == ApplicationMode.LIVE && !AppMetadata.liveTradingEnabled()) {
            return ConnectionAttemptResult.forLiveDisabled();
        }
        TradingApi candidateApi = tradingApiCreator.create(brokerType, mode);
        candidateApi.authenticate(apiKey, apiSecret);
        boolean connected = candidateApi.testConnection();
        return new ConnectionAttemptResult(
                connected,
                candidateApi,
                connected ? "Connected to " + brokerType.name() + " (" + mode.name() + ")" : "Connection failed",
                false,
                false
        );
    }

    private HttpAlpacaClient createModeClient(
            SettingsDialog settingsDialog,
            String runtimeApiKey,
            String runtimeApiSecret,
            ApplicationMode mode
    ) {
        if (mode == ApplicationMode.LIVE && !AppMetadata.liveTradingEnabled()) {
            return null;
        }
        String apiKey = settingsDialog.savedApiKey(mode);
        String apiSecret = settingsDialog.savedApiSecret(mode);
        if ((apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank())
                && mode == settingsDialog.appliedApplicationMode()
                && runtimeApiKey != null
                && !runtimeApiKey.isBlank()
                && runtimeApiSecret != null
                && !runtimeApiSecret.isBlank()) {
            apiKey = runtimeApiKey;
            apiSecret = runtimeApiSecret;
        }
        if (apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
            return null;
        }
        return alpacaClientCreator.create(
                apiKey,
                apiSecret,
                AppMetadata.alpacaTradingBaseUrl(mode),
                AppMetadata.alpacaDataUrl(),
                settingsDialog.appliedExtendedHoursTradingEnabled()
        );
    }

    @FunctionalInterface
    interface TradingApiCreator {
        TradingApi create(BrokerType brokerType, ApplicationMode mode);
    }

    @FunctionalInterface
    interface AlpacaClientCreator {
        HttpAlpacaClient create(String apiKey, String apiSecret, String tradingBaseUrl, String dataBaseUrl, boolean extendedHoursEnabled);
    }

    public record RuntimeClients(HttpAlpacaClient paperModeClient, HttpAlpacaClient liveModeClient) {
    }

    public record RuntimeServices(StrategyService strategyService, StrategyPollingService strategyPollingService) {
    }

    public record ConnectionAttemptResult(
            boolean connected,
            TradingApi tradingApi,
            String message,
            boolean brokerMissing,
            boolean liveDisabled
    ) {
        public static ConnectionAttemptResult forMissingBroker() {
            return new ConnectionAttemptResult(false, null, "Broker not configured", true, false);
        }

        public static ConnectionAttemptResult forLiveDisabled() {
            return new ConnectionAttemptResult(false, null,
                    "LIVE mode is disabled. Set trading.live.enabled=true in app.properties.", false, true);
        }
    }
}
