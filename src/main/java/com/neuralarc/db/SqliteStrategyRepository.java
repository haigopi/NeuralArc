package com.neuralarc.db;

import com.neuralarc.model.PauseReason;
import com.neuralarc.model.ProfitHoldType;
import com.neuralarc.model.StopLossType;
import com.neuralarc.model.Strategy;
import com.neuralarc.model.StrategyLifecycleState;
import com.neuralarc.model.StrategyMode;
import com.neuralarc.model.StrategyStatus;
import com.neuralarc.service.StrategyRepository;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite-backed {@link StrategyRepository}.
 *
 * <p>Keeps an in-memory {@code LinkedHashMap} cache that is invalidated on
 * every write, then lazily reloaded on the next read — preserving the same
 * hot-path performance contract as the file-backed repository.
 */
public final class SqliteStrategyRepository implements StrategyRepository {
    private final AppDatabase db;
    private final Map<String, Strategy> cache = new LinkedHashMap<>();
    private boolean cacheValid;

    public SqliteStrategyRepository(AppDatabase db) {
        this.db = db;
        seedCache();
    }

    // -----------------------------------------------------------------------

    /**
     * No-op for API compatibility with the file-backed repository.
     * SQLite writes are synchronous, so there is nothing to flush.
     */
    public void flushNow() {
        // intentionally empty — writes are committed immediately on save()
    }

    /**
     * Clears the in-memory cache so that the next read reloads from the DB.
     * Call this after an external truncation (e.g. {@link AppDatabase#resetAllData()}).
     */
    public synchronized void invalidateCache() {
        cache.clear();
        cacheValid = false;
    }

    @Override
    public synchronized void save(Strategy strategy) {
        upsert(strategy);
        cache.put(strategy.id(), strategy);
        cacheValid = true;
    }

    @Override
    public synchronized Optional<Strategy> findById(String id) {
        ensureCache();
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public synchronized List<Strategy> findAll() {
        ensureCache();
        return new ArrayList<>(cache.values());
    }

    @Override
    public synchronized List<Strategy> findActive() {
        ensureCache();
        List<Strategy> result = new ArrayList<>();
        for (Strategy s : cache.values()) {
            if (s.status() == StrategyStatus.ACTIVE) {
                result.add(s);
            }
        }
        return result;
    }

    @Override
    public synchronized void deleteById(String id) {
        try (PreparedStatement ps = db.get().prepareStatement("DELETE FROM strategies WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to delete strategy " + id, ex);
        }
        cache.remove(id);
    }

    // -----------------------------------------------------------------------
    // export / import (delegates AppDatabase helpers)
    // -----------------------------------------------------------------------

    /** Exports all strategies as pretty JSON (used by settings export feature). */
    public synchronized String exportJson(boolean pretty) {
        ensureCache();
        JSONArray arr = new JSONArray();
        for (Strategy s : cache.values()) {
            arr.put(toJsonObject(s));
        }
        return pretty ? arr.toString(2) : arr.toString();
    }

    /** Replaces all persisted strategies from a JSON string. */
    public synchronized void replaceAllFromJson(String json) {
        JSONArray arr = new JSONArray(json == null || json.isBlank() ? "[]" : json);
        try {
            Connection conn = db.get();
            conn.setAutoCommit(false);
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM strategies")) {
                del.executeUpdate();
            }
            for (int i = 0; i < arr.length(); i++) {
                Strategy s = fromJsonObject(arr.getJSONObject(i));
                insertRaw(conn, s);
            }
            conn.commit();
            conn.setAutoCommit(true);
        } catch (SQLException ex) {
            throw new IllegalStateException("replaceAllFromJson failed", ex);
        }
        cacheValid = false;
        ensureCache();
    }

    // -----------------------------------------------------------------------

    private void ensureCache() {
        if (!cacheValid) {
            seedCache();
        }
    }

    private void seedCache() {
        cache.clear();
        try (PreparedStatement ps = db.get().prepareStatement("SELECT * FROM strategies ORDER BY rowid");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Strategy s = fromResultSet(rs);
                cache.put(s.id(), s);
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to load strategies from DB", ex);
        }
        cacheValid = true;
    }

    private void upsert(Strategy s) {
        String sql = """
                INSERT INTO strategies (
                    id, name, symbol, mode, status, current_state,
                    base_buy_limit_price, base_buy_quantity,
                    buy_limit1_price, buy_limit1_quantity,
                    buy_limit2_price, buy_limit2_quantity,
                    loss_buy_levels_enabled, automated_stop_loss_enabled,
                    stop_loss_type, stop_loss_price, stop_loss_percent,
                    optional_loss_exit_enabled, optional_loss_exit_price,
                    target_sell_enabled, target_sell_price,
                    target_sell_qty_or_pct, target_sell_percent_based,
                    alpaca_trailing_stop_enabled,
                    profit_hold_enabled, profit_hold_type,
                    profit_hold_percent, profit_hold_amount,
                    highest_observed_price, restart_after_exit_enabled,
                    max_total_quantity, max_capital_allowed,
                    polling_interval_seconds, last_triggered_rule_type,
                    last_polled_at, last_error, last_event,
                    latest_order_status, latest_alpaca_order_id,
                    pause_reason, resume_state_before_pause,
                    created_at, updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    name=excluded.name, symbol=excluded.symbol,
                    mode=excluded.mode, status=excluded.status, current_state=excluded.current_state,
                    base_buy_limit_price=excluded.base_buy_limit_price,
                    base_buy_quantity=excluded.base_buy_quantity,
                    buy_limit1_price=excluded.buy_limit1_price,
                    buy_limit1_quantity=excluded.buy_limit1_quantity,
                    buy_limit2_price=excluded.buy_limit2_price,
                    buy_limit2_quantity=excluded.buy_limit2_quantity,
                    loss_buy_levels_enabled=excluded.loss_buy_levels_enabled,
                    automated_stop_loss_enabled=excluded.automated_stop_loss_enabled,
                    stop_loss_type=excluded.stop_loss_type,
                    stop_loss_price=excluded.stop_loss_price,
                    stop_loss_percent=excluded.stop_loss_percent,
                    optional_loss_exit_enabled=excluded.optional_loss_exit_enabled,
                    optional_loss_exit_price=excluded.optional_loss_exit_price,
                    target_sell_enabled=excluded.target_sell_enabled,
                    target_sell_price=excluded.target_sell_price,
                    target_sell_qty_or_pct=excluded.target_sell_qty_or_pct,
                    target_sell_percent_based=excluded.target_sell_percent_based,
                    alpaca_trailing_stop_enabled=excluded.alpaca_trailing_stop_enabled,
                    profit_hold_enabled=excluded.profit_hold_enabled,
                    profit_hold_type=excluded.profit_hold_type,
                    profit_hold_percent=excluded.profit_hold_percent,
                    profit_hold_amount=excluded.profit_hold_amount,
                    highest_observed_price=excluded.highest_observed_price,
                    restart_after_exit_enabled=excluded.restart_after_exit_enabled,
                    max_total_quantity=excluded.max_total_quantity,
                    max_capital_allowed=excluded.max_capital_allowed,
                    polling_interval_seconds=excluded.polling_interval_seconds,
                    last_triggered_rule_type=excluded.last_triggered_rule_type,
                    last_polled_at=excluded.last_polled_at,
                    last_error=excluded.last_error,
                    last_event=excluded.last_event,
                    latest_order_status=excluded.latest_order_status,
                    latest_alpaca_order_id=excluded.latest_alpaca_order_id,
                    pause_reason=excluded.pause_reason,
                    resume_state_before_pause=excluded.resume_state_before_pause,
                    created_at=excluded.created_at,
                    updated_at=excluded.updated_at
                """;
        try (PreparedStatement ps = db.get().prepareStatement(sql)) {
            bindStrategy(ps, s);
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed to upsert strategy " + s.id(), ex);
        }
    }

    private void insertRaw(Connection conn, Strategy s) throws SQLException {
        String sql = """
                INSERT OR REPLACE INTO strategies (
                    id, name, symbol, mode, status, current_state,
                    base_buy_limit_price, base_buy_quantity,
                    buy_limit1_price, buy_limit1_quantity,
                    buy_limit2_price, buy_limit2_quantity,
                    loss_buy_levels_enabled, automated_stop_loss_enabled,
                    stop_loss_type, stop_loss_price, stop_loss_percent,
                    optional_loss_exit_enabled, optional_loss_exit_price,
                    target_sell_enabled, target_sell_price,
                    target_sell_qty_or_pct, target_sell_percent_based,
                    alpaca_trailing_stop_enabled,
                    profit_hold_enabled, profit_hold_type,
                    profit_hold_percent, profit_hold_amount,
                    highest_observed_price, restart_after_exit_enabled,
                    max_total_quantity, max_capital_allowed,
                    polling_interval_seconds, last_triggered_rule_type,
                    last_polled_at, last_error, last_event,
                    latest_order_status, latest_alpaca_order_id,
                    pause_reason, resume_state_before_pause,
                    created_at, updated_at
                ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            bindStrategy(ps, s);
            ps.executeUpdate();
        }
    }

    private void bindStrategy(PreparedStatement ps, Strategy s) throws SQLException {
        int i = 1;
        ps.setString(i++, s.id());
        ps.setString(i++, s.name());
        ps.setString(i++, s.symbol());
        ps.setString(i++, s.mode().name());
        ps.setString(i++, s.status().name());
        ps.setString(i++, s.currentState().name());
        ps.setString(i++, s.baseBuyLimitPrice().toPlainString());
        ps.setInt(i++, s.baseBuyQuantity());
        ps.setString(i++, s.buyLimit1Price().toPlainString());
        ps.setInt(i++, s.buyLimit1Quantity());
        ps.setString(i++, s.buyLimit2Price().toPlainString());
        ps.setInt(i++, s.buyLimit2Quantity());
        ps.setInt(i++, s.lossBuyLevelsEnabled() ? 1 : 0);
        ps.setInt(i++, s.automatedStopLossEnabled() ? 1 : 0);
        ps.setString(i++, s.stopLossType().name());
        ps.setString(i++, s.stopLossPrice().toPlainString());
        ps.setString(i++, s.stopLossPercent().toPlainString());
        ps.setInt(i++, s.optionalLossExitEnabled() ? 1 : 0);
        ps.setString(i++, s.optionalLossExitPrice().toPlainString());
        ps.setInt(i++, s.targetSellEnabled() ? 1 : 0);
        ps.setString(i++, s.targetSellPrice().toPlainString());
        ps.setString(i++, s.targetSellQuantityOrPercent().toPlainString());
        ps.setInt(i++, s.targetSellPercentBased() ? 1 : 0);
        ps.setInt(i++, s.alpacaTrailingStopEnabled() ? 1 : 0);
        ps.setInt(i++, s.profitHoldEnabled() ? 1 : 0);
        ps.setString(i++, s.profitHoldType().name());
        ps.setString(i++, s.profitHoldPercent().toPlainString());
        ps.setString(i++, s.profitHoldAmount().toPlainString());
        ps.setString(i++, s.highestObservedPriceAfterTarget().toPlainString());
        ps.setInt(i++, s.restartAfterExitEnabled() ? 1 : 0);
        ps.setInt(i++, s.maxTotalQuantity());
        ps.setString(i++, s.maxCapitalAllowed().toPlainString());
        ps.setInt(i++, s.pollingIntervalSeconds());
        ps.setString(i++, s.lastTriggeredRuleType() == null ? "" : s.lastTriggeredRuleType());
        ps.setString(i++, s.lastPolledAt() == null ? null : s.lastPolledAt().toString());
        ps.setString(i++, s.lastError() == null ? "" : s.lastError());
        ps.setString(i++, s.lastEvent() == null ? "" : s.lastEvent());
        ps.setString(i++, s.latestOrderStatus() == null ? "" : s.latestOrderStatus());
        ps.setString(i++, s.latestAlpacaOrderId() == null ? "" : s.latestAlpacaOrderId());
        ps.setString(i++, s.pauseReason().name());
        ps.setString(i++, s.resumeStateBeforePause().name());
        ps.setString(i++, s.createdAt().toString());
        ps.setString(i, s.updatedAt().toString());
    }

    private Strategy fromResultSet(ResultSet rs) throws SQLException {
        Strategy s = new Strategy(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("symbol"),
                StrategyMode.valueOf(rs.getString("mode")),
                StrategyStatus.valueOf(rs.getString("status")),
                safeLifecycle(rs.getString("current_state")),
                decimal(rs, "base_buy_limit_price"),
                rs.getInt("base_buy_quantity"),
                decimal(rs, "buy_limit1_price"),
                rs.getInt("buy_limit1_quantity"),
                decimal(rs, "buy_limit2_price"),
                rs.getInt("buy_limit2_quantity"),
                rs.getInt("automated_stop_loss_enabled") == 1,
                safeStopLossType(rs.getString("stop_loss_type")),
                decimal(rs, "stop_loss_price"),
                decimal(rs, "stop_loss_percent"),
                rs.getInt("optional_loss_exit_enabled") == 1,
                decimal(rs, "optional_loss_exit_price"),
                rs.getInt("target_sell_enabled") == 1,
                decimal(rs, "target_sell_price"),
                decimal(rs, "target_sell_qty_or_pct"),
                rs.getInt("target_sell_percent_based") == 1,
                rs.getInt("alpaca_trailing_stop_enabled") == 1,
                rs.getInt("profit_hold_enabled") == 1,
                safeProfitHoldType(rs.getString("profit_hold_type")),
                decimal(rs, "profit_hold_percent"),
                decimal(rs, "profit_hold_amount"),
                decimal(rs, "highest_observed_price"),
                rs.getInt("restart_after_exit_enabled") == 1,
                rs.getInt("max_total_quantity"),
                decimal(rs, "max_capital_allowed"),
                rs.getInt("polling_interval_seconds"),
                parseInstant(rs.getString("created_at")),
                parseInstant(rs.getString("updated_at"))
        );
        String lastPolledAt = rs.getString("last_polled_at");
        if (lastPolledAt != null && !lastPolledAt.isBlank()) {
            s.setLastPolledAt(Instant.parse(lastPolledAt));
        }
        s.setLastError(rs.getString("last_error"));
        s.setLossBuyLevelsEnabled(rs.getInt("loss_buy_levels_enabled") == 1);
        s.setLastEvent(rs.getString("last_event"));
        s.setLatestOrderStatus(rs.getString("latest_order_status"));
        s.setLatestAlpacaOrderId(rs.getString("latest_alpaca_order_id"));
        s.setPauseReason(safePauseReason(rs.getString("pause_reason")));
        s.setResumeStateBeforePause(safeLifecycle(rs.getString("resume_state_before_pause")));
        return s;
    }

    private Strategy fromJsonObject(JSONObject o) {
        Strategy s = new Strategy(
                o.getString("id"),
                o.optString("name", ""),
                o.optString("symbol", ""),
                StrategyMode.valueOf(o.optString("mode", "PAPER")),
                StrategyStatus.valueOf(o.optString("status", "CREATED")),
                safeLifecycle(o.optString("currentState", "CREATED")),
                decimal(o, "baseBuyLimitPrice"),
                o.optInt("baseBuyQuantity", 0),
                decimal(o, "buyLimit1Price"),
                o.optInt("buyLimit1Quantity", 0),
                decimal(o, "buyLimit2Price"),
                o.optInt("buyLimit2Quantity", 0),
                o.optBoolean("automatedStopLossEnabled", false),
                safeStopLossType(o.optString("stopLossType", "FIXED_PRICE")),
                decimal(o, "stopLossPrice"),
                decimal(o, "stopLossPercent"),
                o.optBoolean("optionalLossExitEnabled", false),
                decimal(o, "optionalLossExitPrice"),
                o.optBoolean("targetSellEnabled", true),
                decimal(o, "targetSellPrice"),
                decimal(o, "targetSellQuantityOrPercent"),
                o.optBoolean("targetSellPercentBased", true),
                o.optBoolean("alpacaTrailingStopEnabled", false),
                o.optBoolean("profitHoldEnabled", false),
                safeProfitHoldType(o.optString("profitHoldType", "PERCENT_TRAILING")),
                decimal(o, "profitHoldPercent"),
                decimal(o, "profitHoldAmount"),
                decimal(o, "highestObservedPriceAfterTarget"),
                o.optBoolean("restartAfterExitEnabled", false),
                o.optInt("maxTotalQuantity", 0),
                decimal(o, "maxCapitalAllowed"),
                o.optInt("pollingIntervalSeconds", 10),
                parseInstant(o.optString("createdAt", Instant.now().toString())),
                parseInstant(o.optString("updatedAt", Instant.now().toString()))
        );
        String lastPolledAt = o.optString("lastPolledAt", "");
        if (!lastPolledAt.isBlank()) s.setLastPolledAt(Instant.parse(lastPolledAt));
        s.setLastError(o.optString("lastError", ""));
        s.setLossBuyLevelsEnabled(o.optBoolean("lossBuyLevelsEnabled", true));
        s.setLastEvent(o.optString("lastEvent", ""));
        s.setLatestOrderStatus(o.optString("latestOrderStatus", ""));
        s.setLatestAlpacaOrderId(o.optString("latestAlpacaOrderId", ""));
        s.setPauseReason(safePauseReason(o.optString("pauseReason", "NONE")));
        s.setResumeStateBeforePause(safeLifecycle(o.optString("resumeStateBeforePause", s.currentState().name())));
        return s;
    }

    private JSONObject toJsonObject(Strategy s) {
        JSONObject o = new JSONObject();
        o.put("id", s.id()); o.put("name", s.name()); o.put("symbol", s.symbol());
        o.put("mode", s.mode().name()); o.put("status", s.status().name());
        o.put("currentState", s.currentState().name());
        o.put("baseBuyLimitPrice", s.baseBuyLimitPrice().toPlainString());
        o.put("baseBuyQuantity", s.baseBuyQuantity());
        o.put("buyLimit1Price", s.buyLimit1Price().toPlainString());
        o.put("buyLimit1Quantity", s.buyLimit1Quantity());
        o.put("buyLimit2Price", s.buyLimit2Price().toPlainString());
        o.put("buyLimit2Quantity", s.buyLimit2Quantity());
        o.put("lossBuyLevelsEnabled", s.lossBuyLevelsEnabled());
        o.put("automatedStopLossEnabled", s.automatedStopLossEnabled());
        o.put("stopLossType", s.stopLossType().name());
        o.put("stopLossPrice", s.stopLossPrice().toPlainString());
        o.put("stopLossPercent", s.stopLossPercent().toPlainString());
        o.put("optionalLossExitEnabled", s.optionalLossExitEnabled());
        o.put("optionalLossExitPrice", s.optionalLossExitPrice().toPlainString());
        o.put("targetSellEnabled", s.targetSellEnabled());
        o.put("targetSellPrice", s.targetSellPrice().toPlainString());
        o.put("targetSellQuantityOrPercent", s.targetSellQuantityOrPercent().toPlainString());
        o.put("targetSellPercentBased", s.targetSellPercentBased());
        o.put("alpacaTrailingStopEnabled", s.alpacaTrailingStopEnabled());
        o.put("profitHoldEnabled", s.profitHoldEnabled());
        o.put("profitHoldType", s.profitHoldType().name());
        o.put("profitHoldPercent", s.profitHoldPercent().toPlainString());
        o.put("profitHoldAmount", s.profitHoldAmount().toPlainString());
        o.put("highestObservedPriceAfterTarget", s.highestObservedPriceAfterTarget().toPlainString());
        o.put("restartAfterExitEnabled", s.restartAfterExitEnabled());
        o.put("maxTotalQuantity", s.maxTotalQuantity());
        o.put("maxCapitalAllowed", s.maxCapitalAllowed().toPlainString());
        o.put("pollingIntervalSeconds", s.pollingIntervalSeconds());
        o.put("createdAt", s.createdAt().toString());
        o.put("updatedAt", s.updatedAt().toString());
        o.put("lastPolledAt", s.lastPolledAt() == null ? "" : s.lastPolledAt().toString());
        o.put("lastError", s.lastError() == null ? "" : s.lastError());
        o.put("lastEvent", s.lastEvent() == null ? "" : s.lastEvent());
        o.put("latestOrderStatus", s.latestOrderStatus() == null ? "" : s.latestOrderStatus());
        o.put("latestAlpacaOrderId", s.latestAlpacaOrderId() == null ? "" : s.latestAlpacaOrderId());
        o.put("pauseReason", s.pauseReason().name());
        o.put("resumeStateBeforePause", s.resumeStateBeforePause().name());
        return o;
    }

    private BigDecimal decimal(ResultSet rs, String col) throws SQLException {
        String v = rs.getString(col);
        try { return v == null || v.isBlank() ? BigDecimal.ZERO : new BigDecimal(v); }
        catch (NumberFormatException ex) { return BigDecimal.ZERO; }
    }

    private BigDecimal decimal(JSONObject o, String key) {
        String v = o.optString(key, "");
        try { return v.isBlank() ? BigDecimal.ZERO : new BigDecimal(v); }
        catch (NumberFormatException ex) { return BigDecimal.ZERO; }
    }

    private StrategyLifecycleState safeLifecycle(String v) {
        try { return StrategyLifecycleState.valueOf(v == null ? "CREATED" : v); }
        catch (IllegalArgumentException ex) { return StrategyLifecycleState.CREATED; }
    }

    private StopLossType safeStopLossType(String v) {
        try { return StopLossType.valueOf(v == null ? "FIXED_PRICE" : v); }
        catch (IllegalArgumentException ex) { return StopLossType.FIXED_PRICE; }
    }

    private ProfitHoldType safeProfitHoldType(String v) {
        try { return ProfitHoldType.valueOf(v == null ? "PERCENT_TRAILING" : v); }
        catch (IllegalArgumentException ex) { return ProfitHoldType.PERCENT_TRAILING; }
    }

    private PauseReason safePauseReason(String v) {
        try { return PauseReason.valueOf(v == null ? "NONE" : v); }
        catch (IllegalArgumentException ex) { return PauseReason.NONE; }
    }

    private Instant parseInstant(String v) {
        try { return v == null || v.isBlank() ? Instant.now() : Instant.parse(v); }
        catch (Exception ex) { return Instant.now(); }
    }
}

