package com.neuralarc.db;

import com.neuralarc.model.StrategyOrder;
import com.neuralarc.model.StrategyOrderSide;
import com.neuralarc.model.StrategyOrderStatus;
import com.neuralarc.model.StrategyOrderType;
import com.neuralarc.model.StrategyStage;
import com.neuralarc.service.StrategyOrderRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite-backed {@link StrategyOrderRepository}.
 *
 * <p>Maintains in-memory indexes identical to the file-backed version so that
 * hot-path reads (by strategy-id, alpaca-order-id, client-order-id) remain
 * purely in-memory.  Writes go directly to the DB — no debounce needed because
 * SQLite WAL handles concurrent writes efficiently.
 */
public final class SqliteStrategyOrderRepository implements StrategyOrderRepository {

    private final AppDatabase db;

    // In-memory indexes — rebuilt from DB on construction and kept up-to-date on writes.
    private final List<StrategyOrder> orders = new ArrayList<>();
    private final Map<String, StrategyOrder> orderById = new HashMap<>();
    private final Map<String, List<StrategyOrder>> ordersByStrategyId = new HashMap<>();
    private final Map<String, StrategyOrder> latestByAlpacaOrderId = new HashMap<>();
    private final Map<String, StrategyOrder> latestByClientOrderId = new HashMap<>();

    public SqliteStrategyOrderRepository(AppDatabase db) {
        this.db = db;
        seedCache();
    }

    // -----------------------------------------------------------------------

    @Override
    public synchronized void save(StrategyOrder order) {
        upsert(order);
        // update in-memory state
        StrategyOrder existing = orderById.get(order.id());
        if (existing == null) {
            orders.add(order);
        } else {
            int idx = orders.indexOf(existing);
            if (idx >= 0) orders.set(idx, order);
        }
        rebuildIndexes();
    }

    @Override
    public synchronized List<StrategyOrder> findByStrategyId(String strategyId) {
        return new ArrayList<>(ordersByStrategyId.getOrDefault(strategyId, List.of()));
    }

    @Override
    public synchronized Optional<StrategyOrder> findLatestByStrategyStage(String strategyId, StrategyStage stage) {
        return findByStrategyId(strategyId).stream()
                .filter(o -> o.stage() == stage)
                .max(Comparator.comparing(StrategyOrder::submittedAt));
    }

    @Override
    public synchronized Optional<StrategyOrder> findByAlpacaOrderId(String alpacaOrderId) {
        if (alpacaOrderId == null || alpacaOrderId.isBlank()) return Optional.empty();
        return Optional.ofNullable(latestByAlpacaOrderId.get(alpacaOrderId));
    }

    @Override
    public synchronized Optional<StrategyOrder> findByClientOrderId(String clientOrderId) {
        if (clientOrderId == null || clientOrderId.isBlank()) return Optional.empty();
        return Optional.ofNullable(latestByClientOrderId.get(clientOrderId));
    }

    @Override
    public synchronized void deleteByStrategyId(String strategyId) {
        try (PreparedStatement ps = db.get().prepareStatement(
                "DELETE FROM strategy_orders WHERE strategy_id=?")) {
            ps.setString(1, strategyId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete orders for strategy " + strategyId, ex);
        }
        orders.removeIf(o -> o.strategyId().equals(strategyId));
        rebuildIndexes();
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    /**
     * Clears all in-memory indexes. Call this after an external truncation
     * (e.g. {@link AppDatabase#resetAllData()}).
     */
    public synchronized void invalidateCache() {
        orders.clear();
        orderById.clear();
        ordersByStrategyId.clear();
        latestByAlpacaOrderId.clear();
        latestByClientOrderId.clear();
    }

    private void seedCache() {
        orders.clear();
        try (PreparedStatement ps = db.get().prepareStatement(
                "SELECT * FROM strategy_orders ORDER BY submitted_at");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                orders.add(fromResultSet(rs));
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load strategy orders from DB", ex);
        }
        rebuildIndexes();
    }

    private void upsert(StrategyOrder o) {
        String sql = """
                INSERT INTO strategy_orders (
                    id, strategy_id, stage, alpaca_order_id, client_order_id,
                    symbol, side, order_type, limit_price, stop_price,
                    requested_quantity, filled_quantity, filled_average_price,
                    status, submitted_at, updated_at, filled_at, raw_response_json
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    strategy_id=excluded.strategy_id,
                    stage=excluded.stage,
                    alpaca_order_id=excluded.alpaca_order_id,
                    client_order_id=excluded.client_order_id,
                    symbol=excluded.symbol,
                    side=excluded.side,
                    order_type=excluded.order_type,
                    limit_price=excluded.limit_price,
                    stop_price=excluded.stop_price,
                    requested_quantity=excluded.requested_quantity,
                    filled_quantity=excluded.filled_quantity,
                    filled_average_price=excluded.filled_average_price,
                    status=excluded.status,
                    submitted_at=excluded.submitted_at,
                    updated_at=excluded.updated_at,
                    filled_at=excluded.filled_at,
                    raw_response_json=excluded.raw_response_json
                """;
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            int i = 1;
            ps.setString(i++, o.id());
            ps.setString(i++, o.strategyId());
            ps.setString(i++, o.stage().name());
            ps.setString(i++, o.alpacaOrderId());
            ps.setString(i++, o.clientOrderId());
            ps.setString(i++, o.symbol());
            ps.setString(i++, o.side().name());
            ps.setString(i++, o.orderType().name());
            ps.setString(i++, o.limitPrice().toPlainString());
            ps.setString(i++, o.stopPrice().toPlainString());
            ps.setString(i++, o.requestedQuantity().toPlainString());
            ps.setString(i++, o.filledQuantity().toPlainString());
            ps.setString(i++, o.filledAveragePrice().toPlainString());
            ps.setString(i++, o.status().name());
            ps.setString(i++, o.submittedAt().toString());
            ps.setString(i++, o.updatedAt() == null ? null : o.updatedAt().toString());
            ps.setString(i++, o.filledAt() == null ? null : o.filledAt().toString());
            ps.setString(i, o.rawResponseJson() == null ? "{}" : o.rawResponseJson());
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to upsert order " + o.id(), ex);
        }
    }

    private StrategyOrder fromResultSet(ResultSet rs) throws SQLException {
        return new StrategyOrder(
                rs.getString("id"),
                rs.getString("strategy_id"),
                StrategyStage.valueOf(rs.getString("stage")),
                rs.getString("alpaca_order_id"),
                rs.getString("client_order_id"),
                rs.getString("symbol"),
                StrategyOrderSide.valueOf(rs.getString("side")),
                StrategyOrderType.valueOf(rs.getString("order_type")),
                decimal(rs, "limit_price"),
                decimal(rs, "stop_price"),
                decimal(rs, "requested_quantity"),
                decimal(rs, "filled_quantity"),
                decimal(rs, "filled_average_price"),
                StrategyOrderStatus.valueOf(rs.getString("status")),
                Instant.parse(rs.getString("submitted_at")),
                parseInstant(rs.getString("updated_at")),
                parseInstant(rs.getString("filled_at")),
                rs.getString("raw_response_json")
        );
    }

    private void rebuildIndexes() {
        orderById.clear();
        ordersByStrategyId.clear();
        latestByAlpacaOrderId.clear();
        latestByClientOrderId.clear();
        for (StrategyOrder order : orders) {
            orderById.put(order.id(), order);
            ordersByStrategyId.computeIfAbsent(order.strategyId(), k -> new ArrayList<>()).add(order);
            if (order.alpacaOrderId() != null && !order.alpacaOrderId().isBlank()) {
                latestByAlpacaOrderId.merge(order.alpacaOrderId(), order, this::latestOrder);
            }
            if (order.clientOrderId() != null && !order.clientOrderId().isBlank()) {
                latestByClientOrderId.merge(order.clientOrderId(), order, this::latestOrder);
            }
        }
    }

    private StrategyOrder latestOrder(StrategyOrder left, StrategyOrder right) {
        return Comparator.comparing(StrategyOrder::submittedAt).compare(left, right) >= 0 ? left : right;
    }

    private BigDecimal decimal(ResultSet rs, String col) throws SQLException {
        String v = rs.getString(col);
        try {
            return v == null || v.isBlank() ? BigDecimal.ZERO : new BigDecimal(v);
        } catch (NumberFormatException ex) {
            return BigDecimal.ZERO;
        }
    }

    private Instant parseInstant(String v) {
        if (v == null || v.isBlank()) return null;
        try {
            return Instant.parse(v);
        } catch (Exception ex) {
            return null;
        }
    }

    /** Imports all orders from a JSON-exported list (used by AppDatabase.importAll). */
    public synchronized void reloadFromDb() {
        orders.clear();
        seedCache();
    }
}

