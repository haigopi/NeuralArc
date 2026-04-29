package com.neuralarc.api;

/**
 * Thrown when the Alpaca Market Data API returns an error or the request cannot be completed.
 */
public class AlpacaMarketDataException extends Exception {

    public AlpacaMarketDataException(String message) {
        super(message);
    }

    public AlpacaMarketDataException(String message, Throwable cause) {
        super(message, cause);
    }
}

