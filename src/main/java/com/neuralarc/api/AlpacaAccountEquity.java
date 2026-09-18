package com.neuralarc.api;

import java.math.BigDecimal;

/**
 * The account's total value from Alpaca: {@code equity} is cash plus the market value of every
 * position right now, and {@code lastEquity} is the same figure at the previous session's close, so
 * their difference is the day's change exactly as Alpaca reports it.
 */
public record AlpacaAccountEquity(BigDecimal equity, BigDecimal lastEquity) {
}
