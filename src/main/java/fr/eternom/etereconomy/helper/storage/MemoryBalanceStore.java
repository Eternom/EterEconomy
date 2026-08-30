package fr.eternom.etereconomy.helper.storage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live balance store backed by a plain in-memory map. Used when storage.type = LOCAL;
 * balances are lost on restart unless a durable {@link BalanceStore} (YAML/MySQL) keeps
 * them in sync (see the economy module's connection listener).
 */
public class MemoryBalanceStore implements BalanceStore {

    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();

    @Override
    public boolean hasAccount(UUID uuid) {
        return balances.containsKey(uuid);
    }

    @Override
    public double getBalance(UUID uuid) {
        return balances.getOrDefault(uuid, 0.0);
    }

    @Override
    public boolean setBalance(UUID uuid, double balance) {
        balances.put(uuid, balance);
        return true;
    }

    @Override
    public double adjustBalance(UUID uuid, double delta, double baselineIfMissing) {
        return balances.compute(uuid, (key, current) -> (current == null ? baselineIfMissing : current) + delta);
    }

    @Override
    public Map<UUID, Double> getAllBalances() {
        return new HashMap<>(balances);
    }
}
