package fr.eternom.etereconomy.helper.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.eternom.etereconomy.helper.config.ConfigManager;

/**
 * Durable bank store backed by two PostgreSQL tables (banks, and their members), pooled through HikariCP.
 */
public class PostgreSqlBankStore extends AbstractSqlBankStore {

    public PostgreSqlBankStore(ConfigManager config) {
        super(dataSource(config),
                "CREATE TABLE IF NOT EXISTS eter_banks (" +
                        "name VARCHAR(64) PRIMARY KEY, " +
                        "owner VARCHAR(36) NOT NULL, " +
                        "balance DOUBLE PRECISION NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS eter_bank_members (" +
                        "bank_name VARCHAR(64) NOT NULL, " +
                        "member VARCHAR(36) NOT NULL, " +
                        "PRIMARY KEY (bank_name, member))");
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
    protected String addMemberSql() {
        return "INSERT INTO eter_bank_members (bank_name, member) VALUES (?, ?) " +
                "ON CONFLICT (bank_name, member) DO NOTHING";
    }
}
