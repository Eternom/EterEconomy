package fr.eternom.etereconomy.helper.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.eternom.etereconomy.Main;
import fr.eternom.etereconomy.helper.config.ConfigManager;
import org.bukkit.Bukkit;

import java.io.File;
import java.util.UUID;

/**
 * Durable balance store backed by a single local SQLite database file. Pooled through HikariCP
 * with a single connection - SQLite serializes writes at the file level anyway, and going
 * through one pooled connection avoids SQLITE_BUSY errors from several connections fighting
 * over the same file.
 */
public class SqliteBalanceStore extends AbstractSqlBalanceStore {

    public SqliteBalanceStore(ConfigManager config) {
        super(dataSource(config), "CREATE TABLE IF NOT EXISTS eter_balances (" +
                "uuid TEXT PRIMARY KEY, " +
                "balance REAL NOT NULL DEFAULT 0)");
    }

    private static HikariDataSource dataSource(ConfigManager config) {
        File dataFolder = new File(Main.getInstance().getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            Bukkit.getLogger().warning("[EterEconomy] Failed to create data folder for SQLite database.");
        }
        File dbFile = new File(dataFolder, config.getSqliteFile());

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        hikariConfig.setMaximumPoolSize(1);
        hikariConfig.setConnectionTimeout(10_000);
        hikariConfig.setConnectionInitSql("PRAGMA busy_timeout = 10000; PRAGMA journal_mode = WAL;");
        return new HikariDataSource(hikariConfig);
    }

    @Override
    public boolean setBalance(UUID uuid, double balance) {
        String sql = "INSERT INTO eter_balances (uuid, balance) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET balance = excluded.balance";
        return executeUpdate("saving balance for " + uuid, sql, uuid.toString(), balance);
    }

    @Override
    public double adjustBalance(UUID uuid, double delta, double baselineIfMissing) {
        // A single INSERT .. ON CONFLICT DO UPDATE is atomic: no read-modify-write race window.
        String sql = "INSERT INTO eter_balances (uuid, balance) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET balance = balance + ?";
        executeUpdate("adjusting balance for " + uuid, sql, uuid.toString(), baselineIfMissing + delta, delta);
        return getBalance(uuid);
    }
}
