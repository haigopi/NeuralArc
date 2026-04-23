package com.neuralarc.service;

import com.neuralarc.analytics.AnalyticsEvent;
import com.neuralarc.analytics.AnalyticsPublisher;
import com.neuralarc.api.TradingApi;
import com.neuralarc.model.*;
import com.neuralarc.rules.RuleEvaluationService;
import com.neuralarc.util.TradeLogger;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class TradingStrategyService {
    private final TradingApi api;
    private final RuleEvaluationService ruleService;
    private final AnalyticsPublisher analyticsPublisher;
    private final TradeLogger logger;
    private final String userId;
    private final String sessionId = UUID.randomUUID().toString();

    private StrategyConfig config;
    private final StrategyState state = new StrategyState();

    public TradingStrategyService(TradingApi api,
                                  RuleEvaluationService ruleService,
                                  AnalyticsPublisher analyticsPublisher,
                                  Consumer<String> logSink,
                                  String userId) {
        this.api = api;
        this.ruleService = ruleService;
        this.analyticsPublisher = analyticsPublisher;
        this.logger = new TradeLogger(logSink);
        this.userId = userId;
    }

    public void configure(StrategyConfig config) {
        this.config = config;
    }

    public synchronized void onPriceTick() {
        if (config == null) return;
        BigDecimal price = api.getLatestPrice(config.symbol());
        Position position = api.getPosition(config.symbol());
        position.setLastPrice(price);
        List<RuleType> rules = ruleService.evaluate(price, position.getTotalShares(), config, state);

        for (RuleType rule : rules) {
            if (!state.markTriggered(rule)) {
                continue;
            }
            handleRule(rule, position, price);
        }
        publishPosition(position, price);
    }

    private void handleRule(RuleType rule, Position position, BigDecimal marketPrice) {
        BigDecimal ruleValue = ruleConfiguredValue(rule);
        logger.log("Triggered: " + rule.name() +
                " | Rule Value: " + ruleValue.toPlainString() +
                " | Market Price: " + marketPrice.toPlainString());
        analyticsPublisher.publish(baseEvent("RULE_TRIGGERED").put("ruleType", rule.name()));
        switch (rule) {
            case BUY_RULE -> submitBuy(config.baseBuyQty());
            case LOSS_BUY_RULE -> submitBuy(config.lossBuyLevel1Qty());
            case LOSS_INVESTMENT_BUY_RULE -> submitBuy(config.lossBuyLevel2Qty());
            case SELL_RULE, STOP_LOSS_RULE -> {
                int qty = position.getTotalShares();
                if (qty > 0) submitSell(qty);
            }
        }

        if (api.getPosition(config.symbol()).getTotalShares() == 0) {
            state.reset();
            logger.log("Strategy reset after full exit");
            analyticsPublisher.publish(baseEvent("POSITION_CLOSED"));
        }
    }

    private BigDecimal ruleConfiguredValue(RuleType rule) {
        return switch (rule) {
            case BUY_RULE -> config.baseBuyPrice();
            case LOSS_BUY_RULE -> config.lossBuyLevel1Price();
            case LOSS_INVESTMENT_BUY_RULE -> config.lossBuyLevel2Price();
            case SELL_RULE -> config.sellTriggerPrice();
            case STOP_LOSS_RULE -> config.stopActivationPrice();
        };
    }

    private void submitBuy(int qty) {
        analyticsPublisher.publish(baseEvent("ORDER_SUBMITTED").put("side", "BUY").put("orderQuantity", qty));
        OrderResult result = api.placeBuyOrder(config.symbol(), qty);
        publishOrderResult(result, "BUY");
    }

    private void submitSell(int qty) {
        analyticsPublisher.publish(baseEvent("ORDER_SUBMITTED").put("side", "SELL").put("orderQuantity", qty));
        OrderResult result = api.placeSellOrder(config.symbol(), qty);
        publishOrderResult(result, "SELL");
    }

    private void publishOrderResult(OrderResult result, String side) {
        logger.log(side + " result: " + result.message() + " @ " + result.fillPrice());
        analyticsPublisher.publish(baseEvent(result.success() ? "ORDER_FILLED" : "ORDER_REJECTED")
                .put("side", side)
                .put("orderQuantity", result.quantity())
                .put("orderPrice", result.fillPrice()));
    }

    private void publishPosition(Position position, BigDecimal price) {
        analyticsPublisher.publish(baseEvent("POSITION_UPDATED")
                .put("symbol", config.symbol())
                .put("totalShares", position.getTotalShares())
                .put("averageCost", position.getAverageCost())
                .put("realizedPnl", position.getRealizedPnl())
                .put("unrealizedPnl", position.unrealizedPnl())
                .put("marketPrice", price)
                .put("paperTrading", config.paperTrading()));
    }

    private AnalyticsEvent baseEvent(String type) {
        return new AnalyticsEvent(type)
                .put("userId", userId)
                .put("sessionId", sessionId)
                .put("strategyId", "neo-mvp-v1");
    }

    public StrategyState getState() {
        return state;
    }
}
