package fr.eternom.etereconomy.helper.storage;

import fr.eternom.etereconomy.Main;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Durable bank store backed by a single banks.yml, one section per bank. Every bank shares this
 * one file, so access is synchronized on the store itself rather than per-key.
 */
public class YamlBankStore implements BankStore {

    private final File file;
    private final YamlConfiguration config;

    public YamlBankStore() {
        File dataFolder = Main.getInstance().getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            Bukkit.getLogger().warning("[EterEconomy] Failed to create plugin data folder.");
        }
        this.file = new File(dataFolder, "banks.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    private String path(String name) {
        return "banks." + name;
    }

    @Override
    public synchronized boolean exists(String name) {
        return config.isConfigurationSection(path(name));
    }

    @Override
    public synchronized boolean create(String name, UUID owner, double startingBalance) {
        config.set(path(name) + ".owner", owner.toString());
        config.set(path(name) + ".balance", startingBalance);
        config.set(path(name) + ".members", new ArrayList<String>());
        return save();
    }

    @Override
    public synchronized boolean delete(String name) {
        config.set(path(name), null);
        return save();
    }

    @Override
    public synchronized double getBalance(String name) {
        return config.getDouble(path(name) + ".balance", 0.0);
    }

    @Override
    public synchronized boolean setBalance(String name, double balance) {
        config.set(path(name) + ".balance", balance);
        return save();
    }

    @Override
    public synchronized double adjustBalance(String name, double delta) {
        double newBalance = getBalance(name) + delta;
        setBalance(name, newBalance);
        return newBalance;
    }

    @Override
    public synchronized UUID getOwner(String name) {
        String raw = config.getString(path(name) + ".owner");
        return raw != null ? UUID.fromString(raw) : null;
    }

    @Override
    public synchronized Set<UUID> getMembers(String name) {
        return config.getStringList(path(name) + ".members").stream()
                .map(UUID::fromString)
                .collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public synchronized boolean addMember(String name, UUID member) {
        Set<UUID> members = getMembers(name);
        members.add(member);
        return writeMembers(name, members);
    }

    @Override
    public synchronized boolean removeMember(String name, UUID member) {
        Set<UUID> members = getMembers(name);
        members.remove(member);
        return writeMembers(name, members);
    }

    private boolean writeMembers(String name, Set<UUID> members) {
        config.set(path(name) + ".members", members.stream().map(UUID::toString).collect(Collectors.toList()));
        return save();
    }

    @Override
    public synchronized List<String> getBankNames() {
        ConfigurationSection section = config.getConfigurationSection("banks");
        return section != null ? new ArrayList<>(section.getKeys(false)) : List.of();
    }

    private boolean save() {
        try {
            config.save(file);
            return true;
        } catch (IOException e) {
            Bukkit.getLogger().warning("[EterEconomy] Error saving banks.yml: " + e.getMessage());
            return false;
        }
    }
}
