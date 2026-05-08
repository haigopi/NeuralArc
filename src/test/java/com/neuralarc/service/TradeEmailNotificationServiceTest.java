package com.neuralarc.service;

import com.neuralarc.model.ApplicationMode;
import com.neuralarc.model.BrokerType;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.model.StrategyStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeEmailNotificationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void sendsBuyExpectedWhenPreferenceIsEnabled() throws Exception {
        AppSettingsService settings = settings(true, false);
        RecordingSender sender = new RecordingSender();
        TradeEmailNotificationService service = new TradeEmailNotificationService(settings, sender, Runnable::run);

        service.notifyBuyExpected(strategy(), order(StrategyOrderSide.BUY, StrategyStage.BASE_BUY, StrategyOrderStatus.SUBMITTED));

        assertEquals(1, sender.messages.size());
        assertEquals("user@example.com", sender.messages.getFirst().recipient());
        assertTrue(sender.messages.getFirst().subject().contains("buy order placed"));
    }

    @Test
    void skipsBuyExpectedWhenPreferenceIsDisabled() throws Exception {
        AppSettingsService settings = settings(false, true);
        RecordingSender sender = new RecordingSender();
        TradeEmailNotificationService service = new TradeEmailNotificationService(settings, sender, Runnable::run);

        service.notifyBuyExpected(strategy(), order(StrategyOrderSide.BUY, StrategyStage.BASE_BUY, StrategyOrderStatus.SUBMITTED));

        assertEquals(0, sender.messages.size());
    }

    @Test
    void sendsSellExecutedWhenPreferenceIsEnabled() throws Exception {
        AppSettingsService settings = settings(false, true);
        RecordingSender sender = new RecordingSender();
        TradeEmailNotificationService service = new TradeEmailNotificationService(settings, sender, Runnable::run);

        service.notifySellExecuted(strategy(), order(StrategyOrderSide.SELL, StrategyStage.TARGET_SELL, StrategyOrderStatus.FILLED));

        assertEquals(1, sender.messages.size());
        assertEquals("user@example.com", sender.messages.getFirst().recipient());
        assertTrue(sender.messages.getFirst().subject().contains("sell order executed"));
    }

    private AppSettingsService settings(boolean buyExpected, boolean sellExecuted) throws Exception {
        AppSettingsService service = new AppSettingsService(tempDir.resolve("settings-" + buyExpected + "-" + sellExecuted + ".db"));
        service.save(new AppSettingsService.AppSettings(
                "user@example.com",
                true,
                true,
                false,
                BrokerType.ALPACA,
                ApplicationMode.PAPER,
                false,
                buyExpected,
                sellExecuted
        ));
        return service;
    }

    private Strategy strategy() {
        return new Strategy(
                "strategy-1",
                "Test Strategy",
                "AAPL",
                StrategyMode.PAPER,
                StrategyStatus.ACTIVE,
                StrategyLifecycleState.BASE_BUY_PLACED,
                new BigDecimal("100.00"),
                10,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                0,
                false,
                StopLossType.FIXED_PRICE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                BigDecimal.ZERO,
                true,
                new BigDecimal("120.00"),
                BigDecimal.ONE,
                false,
                false,
                ProfitHoldType.PERCENT_TRAILING,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false,
                10,
                new BigDecimal("1000.00"),
                30,
                Instant.now(),
                Instant.now()
        );
    }

    private StrategyOrder order(StrategyOrderSide side, StrategyStage stage, StrategyOrderStatus status) {
        return new StrategyOrder(
                "order-1",
                "strategy-1",
                stage,
                "alpaca-order-1",
                "client-order-1",
                "AAPL",
                side,
                StrategyOrderType.LIMIT,
                new BigDecimal("100.00"),
                BigDecimal.ZERO,
                BigDecimal.TEN,
                status == StrategyOrderStatus.FILLED ? BigDecimal.TEN : BigDecimal.ZERO,
                status == StrategyOrderStatus.FILLED ? new BigDecimal("120.00") : BigDecimal.ZERO,
                status,
                Instant.now(),
                Instant.now(),
                status == StrategyOrderStatus.FILLED ? Instant.now() : null,
                "{}"
        );
    }

    private static final class RecordingSender implements TradeEmailNotificationService.EmailSender {
        private final List<Message> messages = new ArrayList<>();

        @Override
        public void send(String recipientEmail, String subject, String textBody, String htmlBody) {
            messages.add(new Message(recipientEmail, subject, textBody, htmlBody));
        }
    }

    private record Message(String recipient, String subject, String textBody, String htmlBody) {
    }
}
