package com.neuralarc.model;

import com.neuralarc.util.Monetary;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class StrategyOrder {
    private final String id;
    private final String strategyId;
    private final StrategyStage stage;
    private String alpacaOrderId;
    private final String clientOrderId;
    private final String symbol;
    private final StrategyOrderSide side;
    private final StrategyOrderType orderType;
    private final BigDecimal limitPrice;
    private final int quantity;
    private BigDecimal filledQuantity;
    private StrategyOrderStatus status;
    private final Instant submittedAt;
    private Instant filledAt;
    private String rawResponseJson;

    public StrategyOrder(
            String id,
            String strategyId,
            StrategyStage stage,
            String alpacaOrderId,
            String clientOrderId,
            String symbol,
            StrategyOrderSide side,
            StrategyOrderType orderType,
            BigDecimal limitPrice,
            int quantity,
            BigDecimal filledQuantity,
            StrategyOrderStatus status,
            Instant submittedAt,
            Instant filledAt,
            String rawResponseJson
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.strategyId = Objects.requireNonNull(strategyId, "strategyId");
        this.stage = Objects.requireNonNull(stage, "stage");
        this.alpacaOrderId = alpacaOrderId;
        this.clientOrderId = Objects.requireNonNull(clientOrderId, "clientOrderId");
        this.symbol = symbol == null ? "" : symbol.trim().toUpperCase();
        this.side = Objects.requireNonNull(side, "side");
        this.orderType = Objects.requireNonNull(orderType, "orderType");
        this.limitPrice = Monetary.round(limitPrice);
        this.quantity = quantity;
        this.filledQuantity = Monetary.round(filledQuantity);
        this.status = Objects.requireNonNull(status, "status");
        this.submittedAt = submittedAt == null ? Instant.now() : submittedAt;
        this.filledAt = filledAt;
        this.rawResponseJson = rawResponseJson;
    }

    public String id() { return id; }
    public String strategyId() { return strategyId; }
    public StrategyStage stage() { return stage; }
    public String alpacaOrderId() { return alpacaOrderId; }
    public String clientOrderId() { return clientOrderId; }
    public String symbol() { return symbol; }
    public StrategyOrderSide side() { return side; }
    public StrategyOrderType orderType() { return orderType; }
    public BigDecimal limitPrice() { return limitPrice; }
    public int quantity() { return quantity; }
    public BigDecimal filledQuantity() { return filledQuantity; }
    public StrategyOrderStatus status() { return status; }
    public Instant submittedAt() { return submittedAt; }
    public Instant filledAt() { return filledAt; }
    public String rawResponseJson() { return rawResponseJson; }

    public void setStatus(StrategyOrderStatus status) { this.status = status; }

    public void setFilledQuantity(BigDecimal filledQuantity) {
        this.filledQuantity = Monetary.round(filledQuantity);
    }

    public void setAlpacaOrderId(String alpacaOrderId) {
        this.alpacaOrderId = alpacaOrderId;
    }

    public void setFilledAt(Instant filledAt) {
        this.filledAt = filledAt;
    }

    public void setRawResponseJson(String rawResponseJson) {
        this.rawResponseJson = rawResponseJson;
    }

    public boolean isTerminal() {
        return status == StrategyOrderStatus.FILLED
                || status == StrategyOrderStatus.CANCELED
                || status == StrategyOrderStatus.REJECTED
                || status == StrategyOrderStatus.FAILED;
    }

    public boolean isPending() {
        return status == StrategyOrderStatus.SUBMITTED
                || status == StrategyOrderStatus.PENDING
                || status == StrategyOrderStatus.PARTIALLY_FILLED;
    }
}

