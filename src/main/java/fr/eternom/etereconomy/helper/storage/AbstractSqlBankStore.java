package fr.eternom.etereconomy.helper.storage;

import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Shared JDBC plumbing for the SQL-backed {@link BankStore} implementations (MySQL, PostgreSQL,
 * SQLite): identical table layout and queries everywhere, only the datasource setup, table DDL
 * and the "insert member if absent" dialect differ per database - those stay in the subclasses.
 */
abstract class AbstractSqlBankStore implements BankStore {

    private static final String BANKS_TABLE = "eter_banks";
    private static final String MEMBERS_TABLE = "eter_bank_members";

    protected final HikariDataSource dataSource;

    protected AbstractSqlBankStore(HikariDataSource dataSource, String createBanksTableSql, String createMembersTableSql) {
        this.dataSource = dataSource;
        executeUpdate("creating bank tables", createBanksTableSql);
        executeUpdate("creating bank tables", createMembersTableSql);
    }

    @Override
    public boolean exists(String name) {
        String sql = "SELECT 1 FROM " + BANKS_TABLE + " WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException e) {
            warn("checking bank " + name, e);
            return false;
        }
    }

    @Override
    public boolean create(String name, UUID owner, double startingBalance) {
        String sql = "INSERT INTO " + BANKS_TABLE + " (name, owner, balance) VALUES (?, ?, ?)";
        return executeUpdate("creating bank " + name, sql, name, owner.toString(), startingBalance);
    }

    @Override
    public boolean delete(String name) {
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + MEMBERS_TABLE + " WHERE bank_name = ?")) {
                statement.setString(1, name);
                statement.executeUpdate();
            }
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + BANKS_TABLE + " WHERE name = ?")) {
                statement.setString(1, name);
                statement.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            warn("deleting bank " + name, e);
            return false;
        }
    }

    @Override
    public double getBalance(String name) {
        String sql = "SELECT balance FROM " + BANKS_TABLE + " WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? resultSet.getDouble("balance") : 0.0;
            }
        } catch (SQLException e) {
            warn("reading balance of bank " + name, e);
            return 0.0;
        }
    }

    @Override
    public boolean setBalance(String name, double balance) {
        String sql = "UPDATE " + BANKS_TABLE + " SET balance = ? WHERE name = ?";
        return executeUpdate("saving balance of bank " + name, sql, balance, name);
    }

    @Override
    public double adjustBalance(String name, double delta) {
        String sql = "UPDATE " + BANKS_TABLE + " SET balance = balance + ? WHERE name = ?";
        executeUpdate("adjusting balance of bank " + name, sql, delta, name);
        return getBalance(name);
    }

    @Override
    public UUID getOwner(String name) {
        String sql = "SELECT owner FROM " + BANKS_TABLE + " WHERE name = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? UUID.fromString(resultSet.getString("owner")) : null;
            }
        } catch (SQLException e) {
            warn("reading owner of bank " + name, e);
            return null;
        }
    }

    @Override
    public Set<UUID> getMembers(String name) {
        String sql = "SELECT member FROM " + MEMBERS_TABLE + " WHERE bank_name = ?";
        Set<UUID> members = new HashSet<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(UUID.fromString(resultSet.getString("member")));
                }
            }
        } catch (SQLException e) {
            warn("reading members of bank " + name, e);
        }
        return members;
    }

    @Override
    public boolean addMember(String name, UUID member) {
        return executeUpdate("adding member to bank " + name, addMemberSql(), name, member.toString());
    }

    /** Only real dialect difference for member inserts: how to no-op on an already-present member. */
    protected abstract String addMemberSql();

    @Override
    public boolean removeMember(String name, UUID member) {
        String sql = "DELETE FROM " + MEMBERS_TABLE + " WHERE bank_name = ? AND member = ?";
        return executeUpdate("removing member from bank " + name, sql, name, member.toString());
    }

    @Override
    public List<String> getBankNames() {
        String sql = "SELECT name FROM " + BANKS_TABLE;
        List<String> names = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                names.add(resultSet.getString("name"));
            }
        } catch (SQLException e) {
            warn("listing banks", e);
        }
        return names;
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
