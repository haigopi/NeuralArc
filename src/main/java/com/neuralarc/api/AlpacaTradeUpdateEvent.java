package com.neuralarc.api;

public record AlpacaTradeUpdateEvent(
        String eventType,
        AlpacaOrderData orderData
) {
}

