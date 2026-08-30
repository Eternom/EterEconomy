package fr.eternom.etereconomy.helper.config;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Optional;

/**
 * Reads and exposes the plugin's config.yml.
 */
public class ConfigManager {

    private static final int REDIS_DEFAULT_PORT = 6379;
    private static final int REDIS_DEFAULT_TIMEOUT = 2000;
    private static final int MYSQL_DEFAULT_PORT = 3306;
    private static final int MYSQL_DEFAULT_POOL_SIZE = 10;
    private static final int POSTGRESQL_DEFAULT_PORT = 5432;
    private static final int POSTGRESQL_DEFAULT_POOL_SIZE = 10;
    private static final String SQLITE_DEFAULT_FILE = "etereconomy.db";
    private static final boolean BUNGEE_DEFAULT_ENABLED = false;
    private static final String ECONOMY_DEFAULT_CURRENCY_SINGULAR = "Helok";
    private static final String ECONOMY_DEFAULT_CURRENCY_PLURAL = "Heloks";
    private static final int ECONOMY_DEFAULT_FRACTIONAL_DIGITS = 0;
    private static final double ECONOMY_DEFAULT_STARTING_BALANCE = 0.0;
    private static final int ECONOMY_DEFAULT_TOP_LIST_SIZE = 10;
    private static final boolean BANK_DEFAULT_ENABLED = true;
    private static final double BANK_DEFAULT_STARTING_BALANCE = 0.0;
    private static final int BANK_DEFAULT_MAX_PER_PLAYER = 0;
    private static final boolean BACKUP_DEFAULT_ENABLED = true;
    private static final int BACKUP_DEFAULT_INTERVAL_MINUTES = 5;

    private ConfigurationSection redisConfig;
    private ConfigurationSection storageConfig;
    private ConfigurationSection informationStorageConfig;
    private ConfigurationSection mysqlConfig;
    private ConfigurationSection postgresqlConfig;
    private ConfigurationSection sqliteConfig;
    private ConfigurationSection bungeeConfig;
    private ConfigurationSection economyConfig;
    private ConfigurationSection bankConfig;
    private ConfigurationSection backupConfig;

    public ConfigManager(JavaPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        this.redisConfig = config.getConfigurationSection("redis");
        this.storageConfig = config.getConfigurationSection("storage");
        this.informationStorageConfig = config.getConfigurationSection("information_storage");
        this.mysqlConfig = config.getConfigurationSection("mysql");
        this.postgresqlConfig = config.getConfigurationSection("postgresql");
        this.sqliteConfig = config.getConfigurationSection("sqlite");
        this.bungeeConfig = config.getConfigurationSection("bungee");
        this.economyConfig = config.getConfigurationSection("economy");
        this.bankConfig = config.getConfigurationSection("bank");
        this.backupConfig = config.getConfigurationSection("backup");
        warnIfSqlLooksUnconfigured();
    }

    // Warns early if MySQL/PostgreSQL is the active backend but still has placeholder credentials,
    // instead of failing later with a confusing connection error.
    private void warnIfSqlLooksUnconfigured() {
        PersistenceFormat format = getPersistenceFormat();
        if (format == PersistenceFormat.MYSQL
                && ("username".equals(getMysqlUser()) || "password".equals(getMysqlPassword()) || "database_name".equals(getMysqlDatabase()))) {
            Bukkit.getLogger().warning("[EterEconomy] information_storage.format is 'mysql' but the mysql section still has "
                    + "placeholder values (username/password/database_name) - update config.yml before starting.");
        }
        if (format == PersistenceFormat.POSTGRESQL
                && ("username".equals(getPostgresqlUser()) || "password".equals(getPostgresqlPassword()) || "database_name".equals(getPostgresqlDatabase()))) {
            Bukkit.getLogger().warning("[EterEconomy] information_storage.format is 'postgresql' but the postgresql section still has "
                    + "placeholder values (username/password/database_name) - update config.yml before starting.");
        }
    }

    public String getRedisHost() {
        return section(redisConfig).map(s -> s.getString("host")).orElse("");
    }

    public int getRedisPort() {
        return section(redisConfig).map(s -> s.getInt("port", REDIS_DEFAULT_PORT)).orElse(REDIS_DEFAULT_PORT);
    }

    public String getRedisPassword() {
        return section(redisConfig).map(s -> s.getString("password")).orElse("");
    }

    public int getRedisTimeout() {
        int timeout = section(redisConfig).map(s -> s.getInt("timeout", REDIS_DEFAULT_TIMEOUT)).orElse(REDIS_DEFAULT_TIMEOUT);
        return Math.max(0, timeout);
    }

    public StorageType getStorageType() {
        String raw = section(storageConfig).map(s -> s.getString("type")).orElse("LOCAL");
        return parseEnum(StorageType.class, raw, StorageType.LOCAL, "storage.type");
    }

    public PersistenceFormat getPersistenceFormat() {
        String raw = section(informationStorageConfig).map(s -> s.getString("format")).orElse("YAML");
        return parseEnum(PersistenceFormat.class, raw, PersistenceFormat.YAML, "information_storage.format");
    }

    public String getMysqlHost() {
        return section(mysqlConfig).map(s -> s.getString("host")).orElse("");
    }

    public int getMysqlPort() {
        return section(mysqlConfig).map(s -> s.getInt("port", MYSQL_DEFAULT_PORT)).orElse(MYSQL_DEFAULT_PORT);
    }

    public String getMysqlUser() {
        return section(mysqlConfig).map(s -> s.getString("user")).orElse("");
    }

    public String getMysqlPassword() {
        return section(mysqlConfig).map(s -> s.getString("password")).orElse("");
    }

    public String getMysqlDatabase() {
        return section(mysqlConfig).map(s -> s.getString("database")).orElse("");
    }

    public int getMysqlPoolSize() {
        int poolSize = section(mysqlConfig).map(s -> s.getInt("pool-size", MYSQL_DEFAULT_POOL_SIZE)).orElse(MYSQL_DEFAULT_POOL_SIZE);
        return Math.max(1, poolSize);
    }

    public String getPostgresqlHost() {
        return section(postgresqlConfig).map(s -> s.getString("host")).orElse("");
    }

    public int getPostgresqlPort() {
        return section(postgresqlConfig).map(s -> s.getInt("port", POSTGRESQL_DEFAULT_PORT)).orElse(POSTGRESQL_DEFAULT_PORT);
    }

    public String getPostgresqlUser() {
        return section(postgresqlConfig).map(s -> s.getString("user")).orElse("");
    }

    public String getPostgresqlPassword() {
        return section(postgresqlConfig).map(s -> s.getString("password")).orElse("");
    }

    public String getPostgresqlDatabase() {
        return section(postgresqlConfig).map(s -> s.getString("database")).orElse("");
    }

    public int getPostgresqlPoolSize() {
        int poolSize = section(postgresqlConfig).map(s -> s.getInt("pool-size", POSTGRESQL_DEFAULT_POOL_SIZE)).orElse(POSTGRESQL_DEFAULT_POOL_SIZE);
        return Math.max(1, poolSize);
    }

    public String getSqliteFile() {
        String file = section(sqliteConfig).map(s -> s.getString("file", SQLITE_DEFAULT_FILE)).orElse(SQLITE_DEFAULT_FILE);
        return file == null || file.isBlank() ? SQLITE_DEFAULT_FILE : file;
    }

    public boolean isBungeeEnabled() {
        return section(bungeeConfig).map(s -> s.getBoolean("enabled", BUNGEE_DEFAULT_ENABLED)).orElse(BUNGEE_DEFAULT_ENABLED);
    }

    public String getCurrencyNameSingular() {
        return section(economyConfig).map(s -> s.getString("currency-name-singular")).orElse(ECONOMY_DEFAULT_CURRENCY_SINGULAR);
    }

    public String getCurrencyNamePlural() {
        return section(economyConfig).map(s -> s.getString("currency-name-plural")).orElse(ECONOMY_DEFAULT_CURRENCY_PLURAL);
    }

    public int getEconomyFractionalDigits() {
        int digits = section(economyConfig).map(s -> s.getInt("fractional-digits", ECONOMY_DEFAULT_FRACTIONAL_DIGITS)).orElse(ECONOMY_DEFAULT_FRACTIONAL_DIGITS);
        // Clamped to keep Math.pow(10, digits) in round() well away from overflowing a double.
        return Math.min(10, Math.max(0, digits));
    }

    public double getEconomyStartingBalance() {
        double balance = section(economyConfig).map(s -> s.getDouble("starting-balance", ECONOMY_DEFAULT_STARTING_BALANCE)).orElse(ECONOMY_DEFAULT_STARTING_BALANCE);
        return Double.isFinite(balance) ? Math.max(0, balance) : ECONOMY_DEFAULT_STARTING_BALANCE;
    }

    public int getEconomyTopListSize() {
        int size = section(economyConfig).map(s -> s.getInt("top-list-size", ECONOMY_DEFAULT_TOP_LIST_SIZE)).orElse(ECONOMY_DEFAULT_TOP_LIST_SIZE);
        return Math.max(1, size);
    }

    public boolean isBankEnabled() {
        return section(bankConfig).map(s -> s.getBoolean("enabled", BANK_DEFAULT_ENABLED)).orElse(BANK_DEFAULT_ENABLED);
    }

    public double getBankStartingBalance() {
        double balance = section(bankConfig).map(s -> s.getDouble("starting-balance", BANK_DEFAULT_STARTING_BALANCE)).orElse(BANK_DEFAULT_STARTING_BALANCE);
        return Double.isFinite(balance) ? Math.max(0, balance) : BANK_DEFAULT_STARTING_BALANCE;
    }

    public int getMaxBanksPerPlayer() {
        int max = section(bankConfig).map(s -> s.getInt("max-banks-per-player", BANK_DEFAULT_MAX_PER_PLAYER)).orElse(BANK_DEFAULT_MAX_PER_PLAYER);
        return Math.max(0, max);
    }

    public boolean isBackupEnabled() {
        return section(backupConfig).map(s -> s.getBoolean("enabled", BACKUP_DEFAULT_ENABLED)).orElse(BACKUP_DEFAULT_ENABLED);
    }

    public int getBackupIntervalMinutes() {
        int minutes = section(backupConfig).map(s -> s.getInt("interval-minutes", BACKUP_DEFAULT_INTERVAL_MINUTES)).orElse(BACKUP_DEFAULT_INTERVAL_MINUTES);
        return Math.max(1, minutes);
    }

    private static Optional<ConfigurationSection> section(ConfigurationSection section) {
        return Optional.ofNullable(section);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw, E fallback, String path) {
        try {
            return Enum.valueOf(type, raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().warning("[EterEconomy] Unknown value '" + raw + "' for " + path + ", defaulting to " + fallback + ".");
            return fallback;
        }
    }
}
