package fr.eternom.etereconomy.helper.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.eternom.etereconomy.helper.config.ConfigManager;

/**
 * Durable bank store backed by two MySQL tables (banks, and their members), pooled through HikariCP.
 */
public class MySqlBankStore extends AbstractSqlBankStore {

    public MySqlBankStore(ConfigManager config) {
        super(dataSource(config),
                "CREATE TABLE IF NOT EXISTS eter_banks (" +
                        "name VARCHAR(64) PRIMARY KEY, " +
                        "owner VARCHAR(36) NOT NULL, " +
                        "balance DOUBLE NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS eter_bank_members (" +
                        "bank_name VARCHAR(64) NOT NULL, " +
                        "member VARCHAR(36) NOT NULL, " +
                        "PRIMARY KEY (bank_name, member))");
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
    protected String addMemberSql() {
        return "INSERT IGNORE INTO eter_bank_members (bank_name, member) VALUES (?, ?)";
    }
}
