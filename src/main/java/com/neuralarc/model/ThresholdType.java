package com.neuralarc.model;

/**
 * Represents the type of threshold for profit-based strategies.
 */
public enum ThresholdType {
    /**
     * Threshold is a percentage gain from the base buy price.
     */
    PERCENTAGE,

    /**
     * Threshold is a fixed dollar amount above the base buy price.
     */
    FIXED_AMOUNT
}

