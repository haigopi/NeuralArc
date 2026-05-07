package com.neuralarc.model;

/**
 * Represents the type of trailing stop protection.
 */
public enum TrailingType {
    /**
     * Trailing stop is based on a percentage pullback from the highest price.
     */
    PERCENTAGE,

    /**
     * Trailing stop is based on a fixed dollar amount pullback from the highest price.
     */
    FIXED_AMOUNT
}

