package fr.eternom.etereconomy.helper.storage;

import fr.eternom.etereconomy.helper.config.ConfigManager;
import fr.eternom.etereconomy.helper.config.StorageType;

/**
 * Picks the concrete {@link BalanceStore}/{@link BankStore} backend for {@code storage.type}
 * (live) and {@code information_storage.format} (persistent) - the one place that knows which
 * class backs which config value, instead of each manager repeating the same choice.
 */
public final class StoreFactory {

    private StoreFactory() {
    }

    public static BalanceStore liveBalanceStore(ConfigManager config) {
        return config.getStorageType() == StorageType.REDIS ? new RedisBalanceStore(config) : new MemoryBalanceStore();
    }

    public static BalanceStore persistentBalanceStore(ConfigManager config) {
        return switch (config.getPersistenceFormat()) {
            case MYSQL -> new MySqlBalanceStore(config);
            case POSTGRESQL -> new PostgreSqlBalanceStore(config);
            case SQLITE -> new SqliteBalanceStore(config);
            case YAML -> new YamlBalanceStore();
        };
    }

    public static BankStore liveBankStore(ConfigManager config) {
        return config.getStorageType() == StorageType.REDIS ? new RedisBankStore(config) : new MemoryBankStore();
    }

    public static BankStore persistentBankStore(ConfigManager config) {
        return switch (config.getPersistenceFormat()) {
            case MYSQL -> new MySqlBankStore(config);
            case POSTGRESQL -> new PostgreSqlBankStore(config);
            case SQLITE -> new SqliteBankStore(config);
            case YAML -> new YamlBankStore();
        };
    }
}
