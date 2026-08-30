package fr.eternom.etereconomy.helper.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.eternom.etereconomy.helper.config.ConfigManager;

import java.util.UUID;

/**
 * Durable balance store backed by a MySQL table, pooled through HikariCP.
 */
public class MySqlBalanceStore extends AbstractSqlBalanceStore {

    public MySqlBalanceStore(ConfigManager config) {
        super(dataSource(config), "CREATE TABLE IF NOT EXISTS eter_balances (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "balance DOUBLE NOT NULL DEFAULT 0)");
    }

    private static HikariDataSource dataSource(ConfigManager config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:mysql://" + config.getMysqlHost() + ":" + config.getMysqlPort() + "/" + config.getMysqlDatabase());
        hikariConfig.setUsername(config.getMysqlUser());
        hikariConfig.setPassword(config.getMysqlPassword());
        hikariConfig.setMaximumPoolSize(config.getMysqlPoolSize());
        hikariConfig.setConnectionTimeout(10_000);
        return new HikariDataSource(hikariConfig);
    }

    @Override
    public boolean setBalance(UUID uuid, double balance) {
        String sql = "INSERT INTO eter_balances (uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = ?";
        return executeUpdate("saving balance for " + uuid, sql, uuid.toString(), balance, balance);
    }

    @Override
    public double adjustBalance(UUID uuid, double delta, double baselineIfMissing) {
        // A single INSERT .. ON DUPLICATE KEY UPDATE is atomic at the database level: no
        // read-modify-write race window, even across multiple servers hitting the same database.
        String sql = "INSERT INTO eter_balances (uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = balance + ?";
        executeUpdate("adjusting balance for " + uuid, sql, uuid.toString(), baselineIfMissing + delta, delta);
        return getBalance(uuid);
    }
}
