package com.neuralarc.api;

import com.neuralarc.model.ApplicationMode;

import java.io.InputStream;
import java.util.Properties;

public final class AlpacaEndpointConfig {
    private static final String CONFIG_RESOURCE = "alpaca-endpoints.properties";
    private static final String DEFAULT_PAPER_ENDPOINT = "https://paper-api.alpaca.markets/v2";
    private static final String DEFAULT_LIVE_ENDPOINT = "https://api.alpaca.markets/v2";

    private final String paperEndpoint;
    private final String liveEndpoint;

    private AlpacaEndpointConfig(String paperEndpoint, String liveEndpoint) {
        this.paperEndpoint = paperEndpoint;
        this.liveEndpoint = liveEndpoint;
    }

    public static AlpacaEndpointConfig load() {
        Properties properties = new Properties();
        try (InputStream input = AlpacaEndpointConfig.class.getClassLoader().getResourceAsStream(CONFIG_RESOURCE)) {
            if (input != null) {
                properties.load(input);
            }
        } catch (Exception ignored) {
            // Use safe defaults if config cannot be loaded.
        }

        String paper = properties.getProperty("paper", DEFAULT_PAPER_ENDPOINT).trim();
        String live = properties.getProperty("live", DEFAULT_LIVE_ENDPOINT).trim();
        return new AlpacaEndpointConfig(paper.isEmpty() ? DEFAULT_PAPER_ENDPOINT : paper,
                live.isEmpty() ? DEFAULT_LIVE_ENDPOINT : live);
    }

    public String endpointFor(ApplicationMode mode) {
        return mode == ApplicationMode.LIVE ? liveEndpoint : paperEndpoint;
    }
}

