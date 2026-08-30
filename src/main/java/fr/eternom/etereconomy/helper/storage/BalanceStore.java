package fr.eternom.etereconomy.helper.storage;

import java.util.Map;
import java.util.UUID;

/**
 * A place where player balances can be read from and written to, keyed by UUID so that
 * balances survive name changes. Implementations back either the live, in-session store
 * ({@link MemoryBalanceStore}, {@link RedisBalanceStore}) or the durable, restart-surviving
 * store ({@link YamlBalanceStore}, {@link MySqlBalanceStore}).
 */
public interface BalanceStore {

    boolean hasAccount(UUID uuid);

    double getBalance(UUID uuid);

    /** @return whether the write actually succeeded (failures are logged internally). */
    boolean setBalance(UUID uuid, double balance);

    /**
     * Atomically adds {@code delta}, using {@code baselineIfMissing} instead of 0 for an account
     * that doesn't exist yet. Race-free against concurrent callers; never mutate a balance via a
     * plain getBalance()/setBalance() pair.
     */
    double adjustBalance(UUID uuid, double delta, double baselineIfMissing);

    Map<UUID, Double> getAllBalances();

    default void close() {
    }
}
