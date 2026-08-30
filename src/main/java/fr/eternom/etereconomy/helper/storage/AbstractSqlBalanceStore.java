package fr.eternom.etereconomy.helper.storage;

import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Shared JDBC plumbing for the SQL-backed {@link BalanceStore} implementations (MySQL,
 * PostgreSQL, SQLite): identical table layout and read queries everywhere, only the datasource
 * setup, table DDL and upsert dialect differ per database - those stay in the subclasses.
 */
abstract class AbstractSqlBalanceStore implements BalanceStore {

    private static final String TABLE = "eter_balances";

    protected final HikariDataSource dataSource;

    protected AbstractSqlBalanceStore(HikariDataSource dataSource, String createTableSql) {
        this.dataSource = dataSource;
        executeUpdate("creating balances table", createTableSql);
    }

    @Override
    public boolean hasAccount(UUID uuid) {
        String sql = "SELECT 1 FROM " + TABLE + " WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            warn("checking account " + uuid, e);
            return false;
        }
    }

    @Override
    public double getBalance(UUID uuid) {
        String sql = "SELECT balance FROM " + TABLE + " WHERE uuid = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble("balance") : 0.0;
            }
        } catch (SQLException e) {
            warn("reading balance for " + uuid, e);
            return 0.0;
        }
    }

    @Override
    public Map<UUID, Double> getAllBalances() {
        String sql = "SELECT uuid, balance FROM " + TABLE;
        Map<UUID, Double> result = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                result.put(UUID.fromString(resultSet.getString("uuid")), resultSet.getDouble("balance"));
            }
        } catch (SQLException e) {
            warn("loading balances", e);
        }
        return result;
    }

    @Override
    public void close() {
        if (!dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /** Runs a write statement, binding {@code params} in order (String and Double supported). */
    protected final boolean executeUpdate(String action, String sql, Object... params) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, params);
            statement.executeUpdate();
            return true;
        } catch (SQLException e) {
            warn(action, e);
            return false;
        }
    }

    private static void bind(PreparedStatement statement, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            switch (params[i]) {
                case String value -> statement.setString(i + 1, value);
                case Double value -> statement.setDouble(i + 1, value);
                default -> throw new IllegalArgumentException("Unsupported parameter type: " + params[i]);
            }
        }
    }

    private static void warn(String action, SQLException e) {
        Bukkit.getLogger().warning("[EterEconomy] Error " + action + ": " + e.getMessage());
    }
}
