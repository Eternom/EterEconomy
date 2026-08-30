package fr.eternom.etereconomy.helper.config;

/**
 * Where balances are durably persisted (used to survive restarts when
 * {@link StorageType#LOCAL} is active).
 */
public enum PersistenceFormat {
    YAML,
    MYSQL,
    SQLITE,
    POSTGRESQL
}
