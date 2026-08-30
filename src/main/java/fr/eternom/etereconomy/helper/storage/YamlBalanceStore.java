package fr.eternom.etereconomy.helper.storage;

import fr.eternom.etereconomy.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Durable balance store: one YAML file per player, named after their UUID. Mutations are
 * serialized per-UUID since both the main thread and async join/quit tasks can reach this.
 */
public class YamlBalanceStore implements BalanceStore {

    private final File dataFolder;
    private final Map<UUID, Object> locks = new ConcurrentHashMap<>();

    public YamlBalanceStore() {
        this.dataFolder = new File(Main.getInstance().getDataFolder(), "data");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            Bukkit.getLogger().warning("[EterEconomy] Failed to create data folder for player balances.");
        }
    }

    private File fileFor(UUID uuid) {
        return new File(dataFolder, uuid + ".yml");
    }

    private Object lockFor(UUID uuid) {
        return locks.computeIfAbsent(uuid, key -> new Object());
    }

    @Override
    public boolean hasAccount(UUID uuid) {
        return fileFor(uuid).exists();
    }

    @Override
    public double getBalance(UUID uuid) {
        synchronized (lockFor(uuid)) {
            return getBalanceLocked(uuid);
        }
    }

    private double getBalanceLocked(UUID uuid) {
        File file = fileFor(uuid);
        if (!file.exists()) {
            return 0.0;
        }
        try {
            YamlConfiguration config = new YamlConfiguration();
            config.load(file);
            return config.getDouble("balance", 0.0);
        } catch (IOException | InvalidConfigurationException e) {
            Bukkit.getLogger().warning("[EterEconomy] Error loading balance for " + uuid + ": " + e.getMessage());
            return 0.0;
        }
    }

    @Override
    public boolean setBalance(UUID uuid, double balance) {
        synchronized (lockFor(uuid)) {
            return setBalanceLocked(uuid, balance);
        }
    }

    private boolean setBalanceLocked(UUID uuid, double balance) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("balance", balance);
        try {
            config.save(fileFor(uuid));
            return true;
        } catch (IOException e) {
            Bukkit.getLogger().warning("[EterEconomy] Error saving balance for " + uuid + ": " + e.getMessage());
            return false;
        }
    }

    @Override
    public double adjustBalance(UUID uuid, double delta, double baselineIfMissing) {
        synchronized (lockFor(uuid)) {
            double base = fileFor(uuid).exists() ? getBalanceLocked(uuid) : baselineIfMissing;
            double newBalance = base + delta;
            setBalanceLocked(uuid, newBalance);
            return newBalance;
        }
    }

    @Override
    public Map<UUID, Double> getAllBalances() {
        File[] files = dataFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) {
            return Map.of();
        }
        Map<UUID, Double> result = new HashMap<>();
        for (File file : files) {
            String baseName = file.getName().substring(0, file.getName().length() - ".yml".length());
            try {
                UUID uuid = UUID.fromString(baseName);
                result.put(uuid, getBalance(uuid));
            } catch (IllegalArgumentException ignored) {
                // Not a UUID-named file, skip it.
            }
        }
        return result;
    }
}
