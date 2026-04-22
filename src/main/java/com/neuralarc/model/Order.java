package com.neuralarc.model;

import java.math.BigDecimal;
import java.time.Instant;

public record Order(String symbol, String side, int quantity, BigDecimal price, Instant timestamp) {
}
