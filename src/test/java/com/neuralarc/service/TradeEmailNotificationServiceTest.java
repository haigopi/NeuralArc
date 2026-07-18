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
import com.neuralarc.model.StrategyWorkspace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeEmailNotificationServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void sendsBuyExpectedWhenPreferenceIsEnabled() throws Exception {
        AppSettingsService settings = settings(true, false);
        RecordingSender sender = new RecordingSender();
        TradeEmailNotificationService service = new TradeEmailNotificationService(
                settings,
                sender,
                Runnable::run,
                "ops@example.com"
        );
        RecordingEmailListener listener = new RecordingEmailListener();
        service.setNotificationListener(listener);

        service.notifyBuyExpected(strategy(), order(StrategyOrderSide.BUY, StrategyStage.BASE_BUY, StrategyOrderStatus.SUBMITTED));

        assertEquals(1, sender.messages.size());
        assertEquals("ops@example.com", sender.messages.getFirst().recipient());
        assertTrue(sender.messages.getFirst().subject().contains("buy order placed"));
        assertEquals(1, listener.sent.size());
        assertTrue(listener.sent.getFirst().contains("BUY_EXPECTED:AAPL:ops@example.com"));
    }

    @Test
    void skipsPaperOrderNotifications() throws Exception {
        AppSettingsService settings = settings(true, true);
        RecordingSender sender = new RecordingSender();
        TradeEmailNotificationService service = new TradeEmailNotificationService(
                settings,
                sender,
                Runnable::run,
                "ops@example.com"
        );

        service.notifyBuyExpected(paperStrategy(), order(StrategyOrderSide.BUY, StrategyStage.BASE_BUY, StrategyOrderStatus.SUBMITTED));
        service.notifySellExecuted(paperStrategy(), order(StrategyOrderSide.SELL, StrategyStage.TARGET_SELL, StrategyOrderStatus.FILLED));

        assertEquals(0, sender.messages.size());
    }

    @Test
    void skipsBuyExpectedWhenPreferenceIsDisabled() throws Exception {
        AppSettingsService settings = settings(false, true);
        RecordingSender sender = new RecordingSender();
        TradeEmailNotificationService service = new TradeEmailNotificationService(
                settings,
                sender,
                Runnable::run,
                "ops@example.com"
        );

        service.notifyBuyExpected(strategy(), order(StrategyOrderSide.BUY, StrategyStage.BASE_BUY, StrategyOrderStatus.SUBMITTED));

        assertEquals(0, sender.messages.size());
    }

    @Test
    void sendsSellExecutedWhenPreferenceIsEnabled() throws Exception {
        AppSettingsService settings = settings(false, true);
        RecordingSender sender = new RecordingSender();
        TradeEmailNotificationService service = new TradeEmailNotificationService(
                settings,
                sender,
                Runnable::run,
                "ops@example.com"
        );

        service.notifySellExecuted(strategy(), order(StrategyOrderSide.SELL, StrategyStage.TARGET_SELL, StrategyOrderStatus.FILLED));

        assertEquals(1, sender.messages.size());
        assertEquals("ops@example.com", sender.messages.getFirst().recipient());
        assertTrue(sender.messages.getFirst().subject().contains("sell order executed"));
    }

    @Test
    void liveEmailIncludesWorkspacePnlAndOrderHistoryTable() throws Exception {
        AppSettingsService settings = settings(false, true);
        RecordingSender sender = new RecordingSender();
        FileStrategyRepository strategies = new FileStrategyRepository(tempDir.resolve("strategies-email.json"));
        FileStrategyOrderRepository orders = new FileStrategyOrderRepository(tempDir.resolve("orders-email.json"));
        InMemoryWorkspaceRepository workspaces = new InMemoryWorkspaceRepository();
        StrategyWorkspace workspace = new StrategyWorkspace(
                "workspace-1",
                "ORB Engine",
                "ORB",
                StrategyMode.LIVE,
                false,
                Instant.now(),
                Instant.now()
        );
        workspaces.save(workspace);
        Strategy strategy = strategy();
        strategy.setWorkspaceId(workspace.id());
        strategies.save(strategy);
        orders.save(order("buy-1", StrategyOrderSide.BUY, StrategyStage.BASE_BUY, StrategyOrderStatus.FILLED, "100.00", "100.00"));
        StrategyOrder sellOrder = order("sell-1", StrategyOrderSide.SELL, StrategyStage.TARGET_SELL, StrategyOrderStatus.FILLED, "120.00", "120.00");
        orders.save(sellOrder);
        TradeEmailNotificationService service = new TradeEmailNotificationService(
                settings,
                sender,
                Runnable::run,
                "ops@example.com",
                new RepositoryTradeEmailNotificationContextProvider(strategies, orders, workspaces)
        );

        service.notifySellExecuted(strategy, sellOrder);

        Message message = sender.messages.getFirst();
        assertTrue(message.textBody().contains("Workspace net P&L: 200.00"));
        assertTrue(message.htmlBody().contains("Workspace Details"));
        assertTrue(message.htmlBody().contains("Order History"));
        assertTrue(message.htmlBody().contains("<table"));
        assertTrue(message.htmlBody().contains("ORB Engine"));
        assertTrue(message.htmlBody().contains("Strategy realized net P&amp;L"));
    }

    @Test
    void automatedNotificationsUseConfiguredRecipientInsteadOfSettingsEmail() throws Exception {
        AppSettingsService settings = settingsWithUserEmail("", true, false);
        RecordingSender sender = new RecordingSender();
        TradeEmailNotificationService service = new TradeEmailNotificationService(
                settings,
                sender,
                Runnable::run,
                "configured-to@example.com"
        );

        service.notifyBuyExpected(strategy(), order(StrategyOrderSide.BUY, StrategyStage.BASE_BUY, StrategyOrderStatus.SUBMITTED));

        assertEquals(1, sender.messages.size());
        assertEquals("configured-to@example.com", sender.messages.getFirst().recipient());
    }

    @Test
    void reportsFailureWhenEmailSenderFails() throws Exception {
        AppSettingsService settings = settings(true, false);
        TradeEmailNotificationService service = new TradeEmailNotificationService(
                settings,
                new FailingSender(),
                Runnable::run,
                "ops@example.com"
        );
        RecordingEmailListener listener = new RecordingEmailListener();
        service.setNotificationListener(listener);

        service.notifyBuyExpected(strategy(), order(StrategyOrderSide.BUY, StrategyStage.BASE_BUY, StrategyOrderStatus.SUBMITTED));

        assertEquals(1, listener.failed.size());
        assertTrue(listener.failed.getFirst().contains("BUY_EXPECTED:AAPL:ops@example.com:boom"));
    }

    private AppSettingsService settings(boolean buyExpected, boolean sellExecuted) throws Exception {
        return settingsWithUserEmail("user@example.com", buyExpected, sellExecuted);
    }

    private AppSettingsService settingsWithUserEmail(String userEmail, boolean buyExpected, boolean sellExecuted) throws Exception {
        AppSettingsService service = new AppSettingsService(tempDir.resolve("settings-" + buyExpected + "-" + sellExecuted + ".db"));
        service.save(new AppSettingsService.AppSettings(
                userEmail,
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
                StrategyMode.LIVE,
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

    private Strategy paperStrategy() {
        Strategy strategy = strategy();
        strategy.setMode(StrategyMode.PAPER);
        return strategy;
    }

    private StrategyOrder order(StrategyOrderSide side, StrategyStage stage, StrategyOrderStatus status) {
        return order("order-1", side, stage, status, "100.00", status == StrategyOrderStatus.FILLED ? "120.00" : "0.00");
    }

    private StrategyOrder order(
            String id,
            StrategyOrderSide side,
            StrategyStage stage,
            StrategyOrderStatus status,
            String limitPrice,
            String fillPrice
    ) {
        return new StrategyOrder(
                id,
                "strategy-1",
                stage,
                "alpaca-" + id,
                "client-" + id,
                "AAPL",
                side,
                StrategyOrderType.LIMIT,
                new BigDecimal(limitPrice),
                BigDecimal.ZERO,
                BigDecimal.TEN,
                status == StrategyOrderStatus.FILLED ? BigDecimal.TEN : BigDecimal.ZERO,
                status == StrategyOrderStatus.FILLED ? new BigDecimal(fillPrice) : BigDecimal.ZERO,
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

    private static final class FailingSender implements TradeEmailNotificationService.EmailSender {
        @Override
        public void send(String recipientEmail, String subject, String textBody, String htmlBody) {
            throw new IllegalStateException("boom");
        }
    }

    private static final class RecordingEmailListener implements TradeEmailNotificationService.EmailNotificationListener {
        private final List<String> sent = new ArrayList<>();
        private final List<String> failed = new ArrayList<>();

        @Override
        public void onEmailSent(String eventType, String symbol, String recipientEmail, String subject) {
            sent.add(eventType + ":" + symbol + ":" + recipientEmail + ":" + subject);
        }

        @Override
        public void onEmailFailed(String eventType, String symbol, String recipientEmail, String subject, String error) {
            failed.add(eventType + ":" + symbol + ":" + recipientEmail + ":" + error);
        }
    }

    private record Message(String recipient, String subject, String textBody, String htmlBody) {
    }

    private static final class InMemoryWorkspaceRepository implements WorkspaceRepository {
        private final Map<String, StrategyWorkspace> workspaces = new HashMap<>();

        @Override
        public void save(StrategyWorkspace workspace) {
            workspaces.put(workspace.id(), workspace);
        }

        @Override
        public Optional<StrategyWorkspace> findById(String id) {
            return Optional.ofNullable(workspaces.get(id));
        }

        @Override
        public List<StrategyWorkspace> findAll() {
            return new ArrayList<>(workspaces.values());
        }

        @Override
        public List<StrategyWorkspace> findByMode(StrategyMode mode) {
            return workspaces.values().stream().filter(workspace -> workspace.mode() == mode).toList();
        }

        @Override
        public List<StrategyWorkspace> findActive(StrategyMode mode) {
            return workspaces.values().stream()
                    .filter(workspace -> workspace.mode() == mode && !workspace.archived())
                    .toList();
        }

        @Override
        public void deleteById(String id) {
            workspaces.remove(id);
        }
    }
}
