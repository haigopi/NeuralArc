package com.neuralarc.model;

/**
 * Represents the active automated profit-control strategy.
 * Only one mode can be active at a time. Manual selling is always available.
 */
public enum ProfitControlMode {
    /**
     * No automated profit control is active. Manual selling is available.
     */
    NONE,

    /**
     * Sell Trigger mode: A local application-side trigger that places an Alpaca sell order
     * only when the current price reaches or exceeds the configured trigger price.
     */
    SELL_TRIGGER,

    /**
     * Automatic Stop Sell mode: Broker-side protection using Alpaca stop/trailing stop orders.
     * The stop order is placed after the configured profit threshold is crossed.
     */
    AUTOMATIC_STOP_SELL,

    /**
     * Profit Hold mode: Trails the highest price and protects gains by exiting when
     * the price pulls back below the configured trailing threshold.
     */
    PROFIT_HOLD
}

