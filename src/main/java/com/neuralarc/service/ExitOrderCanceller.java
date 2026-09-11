package com.neuralarc.service;

import com.neuralarc.api.AlpacaClient;
import com.neuralarc.api.AlpacaOrderData;
import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.util.BrokerOrderStatusUtil;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Cancels a working exit order and waits for the broker to confirm it is gone before a replacement
 * may be placed.
 *
 * <p>An accepted cancel request is not a cancellation: the order moves to pending-cancel and can
 * still fill in the meantime. Replacing on the strength of the request alone risked two sells on one
 * position, or recording a real fill as a cancel. So the broker's own status decides — cancelled,
 * filled during the cancel, or not settled yet — and the local record follows it.
 */
final class ExitOrderCanceller {
    private static final Logger LOGGER = Logger.getLogger(ExitOrderCanceller.class.getName());
    private static final Set<String> GONE = Set.of("canceled", "cancelled", "expired", "rejected", "replaced", "done_for_day");
    private static final int DEFAULT_CONFIRM_ATTEMPTS = 5;
    private static final long DEFAULT_CONFIRM_DELAY_MILLIS = 120L;

    enum Outcome {
        /** The broker confirms the order is no longer working; a replacement may be placed. */
        CANCELED,
        /** The order filled before the cancel took effect; the fill is recorded and nothing is replaced. */
        FILLED,
        /** The broker has not settled the cancel yet; leave it and look again on the next poll. */
        STILL_WORKING
    }

    private final AlpacaClient alpacaClient;
    private final StrategyOrderRepository orderRepository;
    private final int confirmAttempts;
    private final long confirmDelayMillis;

    ExitOrderCanceller(AlpacaClient alpacaClient, StrategyOrderRepository orderRepository) {
        this(alpacaClient, orderRepository, DEFAULT_CONFIRM_ATTEMPTS, DEFAULT_CONFIRM_DELAY_MILLIS);
    }

    ExitOrderCanceller(AlpacaClient alpacaClient, StrategyOrderRepository orderRepository, int confirmAttempts, long confirmDelayMillis) {
        this.alpacaClient = alpacaClient;
        this.orderRepository = orderRepository;
        this.confirmAttempts = Math.max(1, confirmAttempts);
        this.confirmDelayMillis = Math.max(0L, confirmDelayMillis);
    }

    Outcome cancelAndConfirm(StrategyOrder order) {
        String orderId = order.alpacaOrderId();
        if (orderId == null || orderId.isBlank()) {
            // Never reached the broker, so there is nothing working to cancel.
            markCanceled(order, null);
            return Outcome.CANCELED;
        }
        // The request's own result is not trusted either way: a filled order refuses the cancel, and an
        // accepted request does not mean the order has stopped. The status read below decides.
        alpacaClient.cancelOrder(orderId);
        for (int attempt = 0; attempt < confirmAttempts; attempt++) {
            Optional<AlpacaOrderData> remote = alpacaClient.getOrder(orderId);
            if (remote.isEmpty()) {
                // A missing order can also be a failed read. Only call it cancelled if the broker's
                // open-orders list agrees it is gone; otherwise a flaky read could stack a second sell.
                if (!stillOpenAtBroker(order)) {
                    markCanceled(order, null);
                    return Outcome.CANCELED;
                }
            } else {
                String status = BrokerOrderStatusUtil.normalize(remote.get().status());
                if (GONE.contains(status)) {
                    markCanceled(order, remote.get());
                    return Outcome.CANCELED;
                }
                if ("filled".equals(status) || "partially_filled".equals(status)) {
                    markFilled(order, remote.get(), "filled".equals(status));
                    return Outcome.FILLED;
                }
            }
            pause();
        }
        LOGGER.info(() -> "[EXIT_CANCEL][" + order.symbol() + "] Cancel of " + orderId
                + " not confirmed yet; leaving it and re-checking on the next poll");
        return Outcome.STILL_WORKING;
    }

    private boolean stillOpenAtBroker(StrategyOrder order) {
        return alpacaClient.getOpenOrders(order.symbol()).stream().anyMatch(remote ->
                remote.orderId().equals(order.alpacaOrderId())
                        || (!remote.clientOrderId().isBlank() && remote.clientOrderId().equals(order.clientOrderId())));
    }

    private void markCanceled(StrategyOrder order, AlpacaOrderData remote) {
        order.setStatus(StrategyOrderStatus.CANCELED);
        order.setUpdatedAt(Instant.now());
        if (remote != null) {
            order.setRawResponseJson(remote.rawJson());
        }
        orderRepository.save(order);
    }

    private void markFilled(StrategyOrder order, AlpacaOrderData remote, boolean fullyFilled) {
        order.setStatus(fullyFilled ? StrategyOrderStatus.FILLED : StrategyOrderStatus.PARTIALLY_FILLED);
        order.setFilledQuantity(remote.filledQuantity());
        order.setFilledAveragePrice(remote.filledAveragePrice());
        order.setRawResponseJson(remote.rawJson());
        order.setUpdatedAt(Instant.now());
        if (fullyFilled && order.filledAt() == null) {
            order.setFilledAt(Instant.now());
        }
        orderRepository.save(order);
    }

    private void pause() {
        if (confirmDelayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(confirmDelayMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
