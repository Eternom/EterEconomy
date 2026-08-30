package fr.eternom.etereconomy.helper.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.eternom.etereconomy.Main;
import fr.eternom.etereconomy.helper.config.ConfigManager;
import org.bukkit.Bukkit;

import java.io.File;

/**
 * Durable bank store backed by two tables (banks, and their members) in a single local SQLite
 * database file. Pooled through HikariCP with a single connection - see {@link SqliteBalanceStore}.
 */
public class SqliteBankStore extends AbstractSqlBankStore {

    public SqliteBankStore(ConfigManager config) {
        super(dataSource(config),
                "CREATE TABLE IF NOT EXISTS eter_banks (" +
                        "name TEXT PRIMARY KEY, " +
                        "owner TEXT NOT NULL, " +
                        "balance REAL NOT NULL DEFAULT 0)",
                "CREATE TABLE IF NOT EXISTS eter_bank_members (" +
                        "bank_name TEXT NOT NULL, " +
                        "member TEXT NOT NULL, " +
                        "PRIMARY KEY (bank_name, member))");
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
    protected String addMemberSql() {
        return "INSERT OR IGNORE INTO eter_bank_members (bank_name, member) VALUES (?, ?)";
    }
}
