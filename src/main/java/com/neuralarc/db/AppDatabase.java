package com.neuralarc.db;

import com.neuralarc.security.EncryptionUtil;
import com.neuralarc.util.AppMetadata;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Logger;

/**
 * Central SQLite connection manager.
 *
 * <p>One WAL-mode connection per process. Use {@link #get()} to obtain the
 * connection; all repository code executes on the caller's thread and must
 * synchronise access externally (repositories are already synchronized).
 *
 * <p>Schema migration is additive-only: new tables/columns are added by
 * inserting a new {@code applyMigration()} call at the bottom of
 * {@link #applyMigrations()}. Never drop or rename columns in existing
 * migrations — add a new migration instead.
 *
 * <p>Sensitive columns (API keys in app_settings) are stored AES-GCM
 * encrypted via {@link EncryptionUtil}.
 */
public final class AppDatabase {
    private static final Logger LOG = Logger.getLogger(AppDatabase.class.getName());

    /**
     * Machine-local passphrase derived from the host identifier.
     * Not a user secret — it prevents casual inspection of the DB file.
     */
    static final String DB_COLUMN_PASSPHRASE = "neuralarc-local-v1-" + localMachineId();

    private final Connection connection;
    private final EncryptionUtil encryptionUtil = new EncryptionUtil();
    private static AppDatabase INSTANCE;
    private static final Object LOCK = new Object();

    // -----------------------------------------------------------------------

    /** Returns or lazily creates the singleton backed by the default app-data path. */
    public static AppDatabase getInstance() {
        synchronized (LOCK) {
            if (INSTANCE == null) {
                INSTANCE = new AppDatabase(AppMetadata.appDataDirectory().resolve("neuralarc.db"));
            }
            return INSTANCE;
        }
    }

    /** Creates an isolated database instance for tests or scoped services. */
    public static AppDatabase open(Path dbPath) {
        return new AppDatabase(dbPath);
    }

    /** Package-visible constructor used directly by repository tests. */
    AppDatabase(Path dbPath) {
        try {
            Files.createDirectories(dbPath.getParent());
            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            connection = DriverManager.getConnection(url);
            try (Statement st = connection.createStatement()) {
                st.execute("PRAGMA journal_mode=WAL");
                st.execute("PRAGMA foreign_keys=ON");
                st.execute("PRAGMA synchronous=NORMAL");
            }
            applyMigrations();
            Runtime.getRuntime().addShutdownHook(new Thread(this::close, "neuralarc-db-shutdown"));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to open AppDatabase at " + dbPath, ex);
        }
    }

    /** Raw JDBC connection — all repositories use this. */
    public Connection get() {
        return connection;
    }

    public EncryptionUtil encryptionUtil() {
        return encryptionUtil;
    }

    /** Encrypt a string for storage in a sensitive column. Null-safe. */
    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return "";
        }
        return encryptionUtil.encrypt(plaintext, DB_COLUMN_PASSPHRASE);
    }

    /** Decrypt a string from a sensitive column. Null-safe. */
    public String decrypt(String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank()) {
            return "";
        }
        try {
            return encryptionUtil.decrypt(ciphertext, DB_COLUMN_PASSPHRASE);
        } catch (Exception ex) {
            // Graceful degradation — return empty rather than crashing on corrupt data.
            return "";
        }
    }

    // -----------------------------------------------------------------------
    // Export / Import (JSON round-trip for each table)
    // -----------------------------------------------------------------------

    /**
     * Exports all application data to a single JSON object suitable for
     * backup or cross-device transfer.  Sensitive columns are NOT included in
     * the export to avoid leaking credentials.
     */
    public JSONObject exportAll() throws SQLException {
        JSONObject root = new JSONObject();
        root.put("strategies", exportTable("strategies"));
        root.put("strategy_orders", exportTable("strategy_orders"));
        root.put("strategy_events", exportTable("strategy_events"));
        root.put("aggregate_pnl", exportTable("aggregate_pnl"));
        // app_settings deliberately excluded — it contains encrypted credentials.
        return root;
    }

    /**
     * Truncates all user data tables (strategies, strategy_orders, strategy_events,
     * aggregate_pnl, app_settings) in a single transaction.
     *
     * <p>The SQLite connection and schema remain open and intact after this call.
     * In-memory repository caches must be invalidated by the caller.
     */
    public void resetAllData() throws SQLException {
        connection.setAutoCommit(false);
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM strategy_events");
            st.execute("DELETE FROM strategy_orders");
            st.execute("DELETE FROM strategies");
            st.execute("DELETE FROM aggregate_pnl");
            st.execute("DELETE FROM app_settings");
            connection.commit();
            LOG.info("resetAllData: all user data tables truncated");
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    /**
     * Imports data from a JSON object previously created by {@link #exportAll()}.
     * Each present table key replaces the corresponding table's contents.
     */
    public void importAll(JSONObject data) throws SQLException {
        connection.setAutoCommit(false);
        try {
            if (data.has("strategies"))      importTable("strategies",      data.getJSONArray("strategies"));
            if (data.has("strategy_orders")) importTable("strategy_orders", data.getJSONArray("strategy_orders"));
            if (data.has("strategy_events")) importTable("strategy_events", data.getJSONArray("strategy_events"));
            if (data.has("aggregate_pnl"))   importTable("aggregate_pnl",   data.getJSONArray("aggregate_pnl"));
            connection.commit();
        } catch (Exception ex) {
            connection.rollback();
            throw new SQLException("Import failed: " + ex.getMessage(), ex);
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // -----------------------------------------------------------------------
    // Schema migrations — APPEND ONLY
    // -----------------------------------------------------------------------

    private void applyMigrations() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS schema_migrations (
                        id      INTEGER PRIMARY KEY AUTOINCREMENT,
                        version TEXT    NOT NULL UNIQUE,
                        applied_at TEXT NOT NULL DEFAULT (datetime('now'))
                    )""");
        }
        applyMigration("001_initial_schema", this::migration001);
        applyMigration("002_app_settings",   this::migration002);
        applyMigration("003_profit_controls", this::migration003);
        applyMigration("004_profit_control_modes", this::migration004);
        applyMigration("005_resubmit_on_expiry", this::migration005);
        applyMigration("006_time_in_force", this::migration006);
        applyMigration("007_base_buy_repost_reduction_percent", this::migration007);
        applyMigration("008_strategy_workspaces", this::migration008);
        applyMigration("009_gap_and_go_schedules", this::migration009);
        applyMigration("010_orb_schedules",         this::migration010);
        applyMigration("011_dip_hunter_schedules",   this::migration011);
        applyMigration("012_vwap_schedules",         this::migration012);
        applyMigration("013_swing_schedules",        this::migration013);
        applyMigration("014_auto_adjust_risk",       this::migration014);
        applyMigration("015_strategy_scan_history",   this::migration015);
    }

    /** Apply a single named migration if not already recorded. */
    private void applyMigration(String version, SqlRunnable migration) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COUNT(*) FROM schema_migrations WHERE version=?")) {
            ps.setString(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return; // already applied
                }
            }
        }
        connection.setAutoCommit(false);
        try {
            migration.run();
            try (PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO schema_migrations (version) VALUES (?)")) {
                ps.setString(1, version);
                ps.executeUpdate();
            }
            connection.commit();
            LOG.info("Applied DB migration: " + version);
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    // ── Migration 001 — core data tables ────────────────────────────────────

    private void migration001() throws SQLException {
        try (Statement st = connection.createStatement()) {
            // Strategies
            st.execute("""
                    CREATE TABLE IF NOT EXISTS strategies (
                        id                              TEXT PRIMARY KEY,
                        name                            TEXT NOT NULL DEFAULT '',
                        symbol                          TEXT NOT NULL,
                        mode                            TEXT NOT NULL DEFAULT 'PAPER',
                        status                          TEXT NOT NULL DEFAULT 'CREATED',
                        current_state                   TEXT NOT NULL DEFAULT 'CREATED',
                        base_buy_limit_price            TEXT NOT NULL DEFAULT '0.00',
                        base_buy_quantity               INTEGER NOT NULL DEFAULT 0,
                        buy_limit1_price                TEXT NOT NULL DEFAULT '0.00',
                        buy_limit1_quantity             INTEGER NOT NULL DEFAULT 0,
                        buy_limit2_price                TEXT NOT NULL DEFAULT '0.00',
                        buy_limit2_quantity             INTEGER NOT NULL DEFAULT 0,
                        loss_buy_levels_enabled         INTEGER NOT NULL DEFAULT 1,
                        automated_stop_loss_enabled     INTEGER NOT NULL DEFAULT 0,
                        stop_loss_type                  TEXT NOT NULL DEFAULT 'FIXED_PRICE',
                        stop_loss_price                 TEXT NOT NULL DEFAULT '0.00',
                        stop_loss_percent               TEXT NOT NULL DEFAULT '0.00',
                        optional_loss_exit_enabled      INTEGER NOT NULL DEFAULT 0,
                        optional_loss_exit_price        TEXT NOT NULL DEFAULT '0.00',
                        target_sell_enabled             INTEGER NOT NULL DEFAULT 1,
                        target_sell_price               TEXT NOT NULL DEFAULT '0.00',
                        target_sell_qty_or_pct          TEXT NOT NULL DEFAULT '100.00',
                        target_sell_percent_based       INTEGER NOT NULL DEFAULT 1,
                        alpaca_trailing_stop_enabled    INTEGER NOT NULL DEFAULT 0,
                        profit_hold_enabled             INTEGER NOT NULL DEFAULT 0,
                        profit_hold_type                TEXT NOT NULL DEFAULT 'PERCENT_TRAILING',
                        profit_hold_percent             TEXT NOT NULL DEFAULT '0.00',
                        profit_hold_amount              TEXT NOT NULL DEFAULT '0.00',
                        highest_observed_price          TEXT NOT NULL DEFAULT '0.00',
                        restart_after_exit_enabled      INTEGER NOT NULL DEFAULT 0,
                        resubmit_on_expiry_enabled      INTEGER NOT NULL DEFAULT 0,
                        base_buy_repost_reduction_percent TEXT NOT NULL DEFAULT '2.00',
                        time_in_force                   TEXT NOT NULL DEFAULT 'DAY',
                        max_total_quantity              INTEGER NOT NULL DEFAULT 0,
                        max_capital_allowed             TEXT NOT NULL DEFAULT '0.00',
                        polling_interval_seconds        INTEGER NOT NULL DEFAULT 10,
                        last_triggered_rule_type        TEXT NOT NULL DEFAULT '',
                        last_polled_at                  TEXT,
                        last_error                      TEXT NOT NULL DEFAULT '',
                        last_event                      TEXT NOT NULL DEFAULT '',
                        latest_order_status             TEXT NOT NULL DEFAULT '',
                        latest_alpaca_order_id          TEXT NOT NULL DEFAULT '',
                        pause_reason                    TEXT NOT NULL DEFAULT 'NONE',
                        resume_state_before_pause       TEXT NOT NULL DEFAULT 'CREATED',
                        created_at                      TEXT NOT NULL,
                        updated_at                      TEXT NOT NULL
                    )""");

            // Strategy orders
            st.execute("""
                    CREATE TABLE IF NOT EXISTS strategy_orders (
                        id                    TEXT PRIMARY KEY,
                        strategy_id           TEXT NOT NULL,
                        stage                 TEXT NOT NULL,
                        alpaca_order_id       TEXT,
                        client_order_id       TEXT NOT NULL,
                        symbol                TEXT NOT NULL,
                        side                  TEXT NOT NULL,
                        order_type            TEXT NOT NULL,
                        limit_price           TEXT NOT NULL DEFAULT '0.00',
                        stop_price            TEXT NOT NULL DEFAULT '0.00',
                        requested_quantity    TEXT NOT NULL DEFAULT '0',
                        filled_quantity       TEXT NOT NULL DEFAULT '0.00',
                        filled_average_price  TEXT NOT NULL DEFAULT '0.00',
                        status                TEXT NOT NULL,
                        time_in_force         TEXT NOT NULL DEFAULT 'DAY',
                        submitted_at          TEXT NOT NULL,
                        updated_at            TEXT,
                        filled_at             TEXT,
                        raw_response_json     TEXT NOT NULL DEFAULT '{}'
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_orders_strategy_id ON strategy_orders(strategy_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_orders_alpaca_id ON strategy_orders(alpaca_order_id)");
            st.execute("CREATE INDEX IF NOT EXISTS idx_orders_client_id ON strategy_orders(client_order_id)");

            // Execution events
            st.execute("""
                    CREATE TABLE IF NOT EXISTS strategy_events (
                        id            TEXT PRIMARY KEY,
                        strategy_id   TEXT NOT NULL,
                        event_type    TEXT NOT NULL,
                        message       TEXT NOT NULL DEFAULT '',
                        metadata_json TEXT NOT NULL DEFAULT '{}',
                        created_at    TEXT NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_events_strategy_id ON strategy_events(strategy_id)");

            // Aggregate P&L
            st.execute("""
                    CREATE TABLE IF NOT EXISTS aggregate_pnl (
                        mode              TEXT PRIMARY KEY,
                        archived_realized TEXT NOT NULL DEFAULT '0.00'
                    )""");
        }
    }

    // ── Migration 002 — encrypted app settings ───────────────────────────────

    private void migration002() throws SQLException {
        try (Statement st = connection.createStatement()) {
            // key/value settings store; sensitive values are AES-GCM encrypted
            st.execute("""
                    CREATE TABLE IF NOT EXISTS app_settings (
                        key         TEXT PRIMARY KEY,
                        value       TEXT NOT NULL DEFAULT '',
                        encrypted   INTEGER NOT NULL DEFAULT 0
                    )""");
        }
    }

    private void migration003() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE strategies ADD COLUMN alpaca_trailing_stop_enabled INTEGER NOT NULL DEFAULT 0");
        } catch (SQLException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (!message.contains("duplicate column name")) {
                throw ex;
            }
        }
    }

    private void migration004() throws SQLException {
        addColumnIfMissing("strategies", "profit_control_mode", "TEXT NOT NULL DEFAULT 'NONE'");
        addColumnIfMissing("strategies", "automatic_stop_sell_threshold_type", "TEXT NOT NULL DEFAULT 'FIXED_AMOUNT'");
        addColumnIfMissing("strategies", "automatic_stop_sell_threshold", "TEXT NOT NULL DEFAULT '0.00'");
        addColumnIfMissing("strategies", "automatic_stop_sell_trailing_type", "TEXT NOT NULL DEFAULT 'PERCENTAGE'");
        addColumnIfMissing("strategies", "automatic_stop_sell_trailing_value", "TEXT NOT NULL DEFAULT '0.00'");
    }

    private void migration005() throws SQLException {
        addColumnIfMissing("strategies", "resubmit_on_expiry_enabled", "INTEGER NOT NULL DEFAULT 0");
    }

    private void migration006() throws SQLException {
        addColumnIfMissing("strategies", "time_in_force", "TEXT NOT NULL DEFAULT 'DAY'");
        addColumnIfMissing("strategy_orders", "time_in_force", "TEXT NOT NULL DEFAULT 'DAY'");
    }

    private void migration007() throws SQLException {
        addColumnIfMissing("strategies", "base_buy_repost_reduction_percent", "TEXT NOT NULL DEFAULT '2.00'");
    }

    // ── Migration 008 — strategy workspaces (additive, backward compatible) ──
    // New table groups existing per-symbol strategies into higher-level workspaces; a nullable
    // workspace_id link is added to strategies. Existing rows stay NULL (unassigned / All Stocks).
    private void migration008() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS strategy_workspaces (
                        id          TEXT PRIMARY KEY,
                        name        TEXT NOT NULL DEFAULT '',
                        code        TEXT NOT NULL,
                        mode        TEXT NOT NULL DEFAULT 'PAPER',
                        archived    INTEGER NOT NULL DEFAULT 0,
                        created_at  TEXT NOT NULL,
                        updated_at  TEXT NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_workspaces_mode ON strategy_workspaces(mode)");
            st.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_workspaces_code_mode ON strategy_workspaces(code, mode)");
        }
        addColumnIfMissing("strategies", "workspace_id", "TEXT");
    }

    // ── Migration 009 — autonomous gap-and-go schedules ─────────────────────

    private void migration009() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS gap_and_go_schedules (
                        id                       TEXT PRIMARY KEY,
                        enabled                  INTEGER NOT NULL DEFAULT 1,
                        scan_time_et             TEXT NOT NULL,
                        window_start_et          TEXT NOT NULL,
                        window_end_et            TEXT NOT NULL,
                        execute_after_scan       INTEGER NOT NULL DEFAULT 0,
                        workspace_id             TEXT NOT NULL DEFAULT '',
                        config_json              TEXT NOT NULL,
                        updated_at               TEXT NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_gap_schedules_workspace ON gap_and_go_schedules(workspace_id)");
        }
    }

    // ── Migration 010 — autonomous ORB schedules ────────────────────────────

    private void migration010() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS orb_schedules (
                        id                        TEXT PRIMARY KEY,
                        enabled                   INTEGER NOT NULL DEFAULT 1,
                        range_analysis_time_et    TEXT NOT NULL,
                        window_end_et             TEXT NOT NULL,
                        execute_after_range_close INTEGER NOT NULL DEFAULT 0,
                        workspace_id              TEXT NOT NULL DEFAULT '',
                        config_json               TEXT NOT NULL,
                        updated_at                TEXT NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_orb_schedules_workspace ON orb_schedules(workspace_id)");
        }
    }

    // ── Migration 011 — autonomous Dip Hunter schedules ─────────────────────

    private void migration011() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS dip_hunter_schedules (
                        id                       TEXT PRIMARY KEY,
                        enabled                  INTEGER NOT NULL DEFAULT 1,
                        scan_time_et             TEXT NOT NULL,
                        window_start_et          TEXT NOT NULL,
                        window_end_et            TEXT NOT NULL,
                        execute_after_scan       INTEGER NOT NULL DEFAULT 0,
                        workspace_id             TEXT NOT NULL DEFAULT '',
                        config_json              TEXT NOT NULL,
                        updated_at               TEXT NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_dip_hunter_schedules_workspace ON dip_hunter_schedules(workspace_id)");
        }
    }

    // ── Migration 012 — autonomous VWAP Desk schedules ──────────────────────

    private void migration012() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS vwap_schedules (
                        id                       TEXT PRIMARY KEY,
                        enabled                  INTEGER NOT NULL DEFAULT 1,
                        scan_time_et             TEXT NOT NULL,
                        window_start_et          TEXT NOT NULL,
                        window_end_et            TEXT NOT NULL,
                        execute_after_scan       INTEGER NOT NULL DEFAULT 0,
                        workspace_id             TEXT NOT NULL DEFAULT '',
                        config_json              TEXT NOT NULL,
                        updated_at               TEXT NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_vwap_schedules_workspace ON vwap_schedules(workspace_id)");
        }
    }

    // ── Migration 013 — autonomous Swing Vault schedules ────────────────────

    private void migration013() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS swing_schedules (
                        id                       TEXT PRIMARY KEY,
                        enabled                  INTEGER NOT NULL DEFAULT 1,
                        scan_time_et             TEXT NOT NULL,
                        window_start_et          TEXT NOT NULL,
                        window_end_et            TEXT NOT NULL,
                        execute_after_scan       INTEGER NOT NULL DEFAULT 0,
                        workspace_id             TEXT NOT NULL DEFAULT '',
                        config_json              TEXT NOT NULL,
                        updated_at               TEXT NOT NULL
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_swing_schedules_workspace ON swing_schedules(workspace_id)");
        }
    }

    // ── Migration 014 — Auto Adjust Risk & Stop Loss (per-strategy columns) ──

    private void migration014() throws SQLException {
        addColumnIfMissing("strategies", "auto_adjust_enabled", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("strategies", "auto_adjust_monitoring_days", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("strategies", "auto_adjust_daily_percent", "TEXT NOT NULL DEFAULT '0.00'");
        addColumnIfMissing("strategies", "auto_adjust_after_close", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing("strategies", "auto_adjust_on_decrease", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing("strategies", "auto_adjust_on_increase", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing("strategies", "auto_adjust_day_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("strategies", "auto_adjust_last_date", "TEXT NOT NULL DEFAULT ''");
        addColumnIfMissing("strategies", "auto_adjust_reference_price", "TEXT NOT NULL DEFAULT '0.00'");
    }

    // ── Migration 015 — strategy scan history (per-workspace scan run log) ───

    private void migration015() throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS strategy_scan_history (
                        id            TEXT PRIMARY KEY,
                        workspace_id  TEXT NOT NULL DEFAULT '',
                        ran_at        TEXT NOT NULL,
                        trigger       TEXT NOT NULL DEFAULT 'Manual',
                        summary       TEXT NOT NULL DEFAULT ''
                    )""");
            st.execute("CREATE INDEX IF NOT EXISTS idx_scan_history_workspace "
                    + "ON strategy_scan_history(workspace_id, ran_at)");
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        } catch (SQLException ex) {
            String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
            if (!message.contains("duplicate column name")) {
                throw ex;
            }
        }
    }

    // -----------------------------------------------------------------------
    // Internal helpers
    // -----------------------------------------------------------------------

    private JSONArray exportTable(String tableName) throws SQLException {
        JSONArray arr = new JSONArray();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM " + tableName)) {
            int colCount = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                JSONObject row = new JSONObject();
                for (int i = 1; i <= colCount; i++) {
                    row.put(rs.getMetaData().getColumnName(i), rs.getString(i));
                }
                arr.put(row);
            }
        }
        return arr;
    }

    private void importTable(String tableName, JSONArray rows) throws SQLException {
        try (Statement st = connection.createStatement()) {
            st.execute("DELETE FROM " + tableName);
        }
        if (rows.isEmpty()) {
            return;
        }
        JSONObject first = rows.getJSONObject(0);
        String cols = String.join(",", first.keySet());
        String placeholders = "?,".repeat(first.keySet().size());
        placeholders = placeholders.substring(0, placeholders.length() - 1);
        String sql = "INSERT OR REPLACE INTO " + tableName + " (" + cols + ") VALUES (" + placeholders + ")";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int r = 0; r < rows.length(); r++) {
                JSONObject row = rows.getJSONObject(r);
                int idx = 1;
                for (String key : first.keySet()) {
                    ps.setString(idx++, row.optString(key, null));
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (Exception ignored) {
        }
    }

    private static String localMachineId() {
        try {
            return System.getProperty("user.name", "neuralarc") + "-" + System.getProperty("os.name", "local");
        } catch (Exception ex) {
            return "neuralarc-local";
        }
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }
}
