package fr.eternom.etereconomy.helper.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.eternom.etereconomy.helper.config.ConfigManager;

import java.util.UUID;

/**
 * Durable balance store backed by a PostgreSQL table, pooled through HikariCP.
 */
public class PostgreSqlBalanceStore extends AbstractSqlBalanceStore {

    public PostgreSqlBalanceStore(ConfigManager config) {
        super(dataSource(config), "CREATE TABLE IF NOT EXISTS eter_balances (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "balance DOUBLE PRECISION NOT NULL DEFAULT 0)");
    }

    private static HikariDataSource dataSource(ConfigManager config) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl("jdbc:postgresql://" + config.getPostgresqlHost() + ":" + config.getPostgresqlPort() + "/" + config.getPostgresqlDatabase());
        hikariConfig.setUsername(config.getPostgresqlUser());
        hikariConfig.setPassword(config.getPostgresqlPassword());
        hikariConfig.setMaximumPoolSize(config.getPostgresqlPoolSize());
        hikariConfig.setConnectionTimeout(10_000);
        return new HikariDataSource(hikariConfig);
    }

    @Override
    public boolean setBalance(UUID uuid, double balance) {
        String sql = "INSERT INTO eter_balances (uuid, balance) VALUES (?, ?) " +
                "ON CONFLICT (uuid) DO UPDATE SET balance = excluded.balance";
        return executeUpdate("saving balance for " + uuid, sql, uuid.toString(), balance);
    }

    @Override
    public double adjustBalance(UUID uuid, double delta, double baselineIfMissing) {
        // A single INSERT .. ON CONFLICT DO UPDATE is atomic at the database level: no
        // read-modify-write race window, even across multiple servers hitting the same database.
        String sql = "INSERT INTO eter_balances (uuid, balance) VALUES (?, ?) " +
                "ON CONFLICT (uuid) DO UPDATE SET balance = eter_balances.balance + ?";
        executeUpdate("adjusting balance for " + uuid, sql, uuid.toString(), baselineIfMissing + delta, delta);
        return getBalance(uuid);
    }
}
