package fr.eternom.etereconomy.module.economy;

import fr.eternom.etereconomy.core.Module;
import fr.eternom.etereconomy.helper.config.ConfigManager;
import fr.eternom.etereconomy.helper.config.Currency;
import fr.eternom.etereconomy.helper.config.MessageManager;
import fr.eternom.etereconomy.helper.storage.BalanceStore;
import fr.eternom.etereconomy.helper.storage.StoreFactory;
import fr.eternom.etereconomy.module.bank.BankManager;
import fr.eternom.etereconomy.module.economy.command.CommandEco;
import fr.eternom.etereconomy.module.economy.event.ConnectionListener;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Vault Economy provider for EterEconomy, and the module that registers it. Balances are keyed
 * by {@link UUID} so renaming a player never loses their balance. {@code liveStore} holds the
 * in-session data (memory or Redis), {@code persistentStore} is the durable backend
 * (YAML/MySQL/...) kept in sync on join/quit and by the backup module. Registers {@code /eco},
 * the join/quit sync listener, and this instance as Vault's Economy provider on {@link #enable()}.
 */
public class EconomyManager implements Economy, Module {

    // Rank lookups (e.g. the %etereconomy_rank% placeholder) must return instantly on whatever
    // thread calls them, so the full-store scan behind a rank is precomputed on this interval
    // instead of running per request.
    private static final long RANK_CACHE_REFRESH_TICKS = 600L;

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final BalanceStore liveStore;
    private final BalanceStore persistentStore;
    private final BankManager bankManager;

    private final String currencyNameSingular;
    private final String currencyNamePlural;
    private final int fractionalDigits;
    private final double startingBalance;
    private final int topListSize;
    private final boolean bankEnabled;

    private volatile Map<UUID, Integer> rankCache = Map.of();
    private BukkitTask rankCacheTask;

    public EconomyManager(JavaPlugin plugin, ConfigManager config, MessageManager messages, BankManager bankManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.liveStore = StoreFactory.liveBalanceStore(config);
        this.persistentStore = StoreFactory.persistentBalanceStore(config);
        this.bankManager = bankManager;

        this.currencyNameSingular = config.getCurrencyNameSingular();
        this.currencyNamePlural = config.getCurrencyNamePlural();
        this.fractionalDigits = config.getEconomyFractionalDigits();
        this.startingBalance = config.getEconomyStartingBalance();
        this.topListSize = config.getEconomyTopListSize();
        this.bankEnabled = config.isBankEnabled();
    }

    @Override
    public void enable() {
        Bukkit.getServicesManager().register(Economy.class, this, plugin, ServicePriority.Normal);

        // Defers to whichever Economy provider Bukkit actually resolved, in case another plugin
        // registered one at a higher priority.
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        Economy economy = registration != null ? registration.getProvider() : this;

        Objects.requireNonNull(plugin.getCommand("eco")).setExecutor(new CommandEco(plugin, economy, this, messages));

        Bukkit.getPluginManager().registerEvents(new ConnectionListener(plugin, this), plugin);

        rankCacheTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshRankCache, 0L, RANK_CACHE_REFRESH_TICKS);
    }

    @Override
    public void disable() {
        if (rankCacheTask != null) {
            rankCacheTask.cancel();
        }
        liveStore.close();
        persistentStore.close();
    }

    public BalanceStore getLiveStore() {
        return liveStore;
    }

    public BalanceStore getPersistentStore() {
        return persistentStore;
    }

    // Falls back to the persistent store directly for offline players never loaded into liveStore.
    private BalanceStore activeStoreFor(UUID uuid) {
        return liveStore.hasAccount(uuid) ? liveStore : persistentStore;
    }

    private double effectiveBalance(BalanceStore store, UUID uuid) {
        return store.hasAccount(uuid) ? store.getBalance(uuid) : startingBalance;
    }

    public double getPersistedOrStartingBalance(UUID uuid) {
        return effectiveBalance(persistentStore, uuid);
    }

    public List<Map.Entry<UUID, Double>> getTopBalances() {
        return mergedBalances().entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(topListSize)
                .collect(Collectors.toList());
    }

    /**
     * 1-based rank across the full balance list, or -1 if the player has no recorded balance.
     * Reads a cache refreshed every {@link #RANK_CACHE_REFRESH_TICKS} off the main thread - see
     * {@link #refreshRankCache()} - instead of scanning the persistent store per call, since this
     * backs a placeholder that must return synchronously on whatever thread requests it.
     */
    public int getRank(UUID uuid) {
        return rankCache.getOrDefault(uuid, -1);
    }

    private void refreshRankCache() {
        List<UUID> ranked = mergedBalances().entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        Map<UUID, Integer> ranks = new HashMap<>();
        for (int i = 0; i < ranked.size(); i++) {
            ranks.put(ranked.get(i), i + 1);
        }
        rankCache = ranks;
    }

    private Map<UUID, Double> mergedBalances() {
        Map<UUID, Double> merged = new HashMap<>();
        merged.putAll(persistentStore.getAllBalances());
        merged.putAll(liveStore.getAllBalances());
        return merged;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "EterEconomy";
    }

    @Override
    public boolean hasBankSupport() {
        return bankEnabled;
    }

    @Override
    public int fractionalDigits() {
        return fractionalDigits;
    }

    @Override
    public String format(double value) {
        return Currency.format(value, fractionalDigits, currencyNameSingular, currencyNamePlural);
    }

    @Override
    public String currencyNamePlural() {
        return currencyNamePlural;
    }

    @Override
    public String currencyNameSingular() {
        return currencyNameSingular;
    }

    @Override
    public boolean hasAccount(String playerName) {
        return hasAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        return liveStore.hasAccount(uuid) || persistentStore.hasAccount(uuid);
    }

    @Override
    public boolean hasAccount(String playerName, String world) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String world) {
        return hasAccount(player);
    }

    @Override
    public double getBalance(String playerName) {
        return getBalance(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        return effectiveBalance(activeStoreFor(uuid), uuid);
    }

    @Override
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    public boolean has(String playerName, double value) {
        return has(Bukkit.getOfflinePlayer(playerName), value);
    }

    @Override
    public boolean has(OfflinePlayer player, double value) {
        return getBalance(player) >= value;
    }

    @Override
    public boolean has(String playerName, String world, double value) {
        return has(playerName, value);
    }

    @Override
    public boolean has(OfflinePlayer player, String world, double value) {
        return has(player, value);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double value) {
        return withdrawPlayer(Bukkit.getOfflinePlayer(playerName), value);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double value) {
        if (!Double.isFinite(value) || value < 0) {
            return invalidAmount();
        }

        UUID uuid = player.getUniqueId();
        BalanceStore store = activeStoreFor(uuid);
        double roundedValue = Currency.round(value, fractionalDigits);

        // Atomic decrement avoids a read-then-write race between concurrent withdrawals; roll
        // back if it pushes the balance negative.
        double newBalance = store.adjustBalance(uuid, -roundedValue, startingBalance);
        if (newBalance < 0) {
            double restored = store.adjustBalance(uuid, roundedValue, startingBalance);
            return new EconomyResponse(0, restored, EconomyResponse.ResponseType.FAILURE, "Player does not have enough money");
        }

        return new EconomyResponse(roundedValue, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String world, double value) {
        return withdrawPlayer(playerName, value);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double value) {
        return withdrawPlayer(player, value);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double value) {
        return depositPlayer(Bukkit.getOfflinePlayer(playerName), value);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double value) {
        if (!Double.isFinite(value) || value < 0) {
            return invalidAmount();
        }

        UUID uuid = player.getUniqueId();
        BalanceStore store = activeStoreFor(uuid);
        double roundedValue = Currency.round(value, fractionalDigits);

        double newBalance = store.adjustBalance(uuid, roundedValue, startingBalance);
        if (!Double.isFinite(newBalance)) {
            // Overflow: undo rather than leave an Infinity/NaN balance.
            store.adjustBalance(uuid, -roundedValue, startingBalance);
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "The resulting balance would be too large");
        }

        return new EconomyResponse(roundedValue, newBalance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse invalidAmount() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "Invalid amount");
    }

    /** Sets a balance directly - Vault's Economy interface only has deposit/withdraw. */
    public EconomyResponse setBalance(OfflinePlayer player, double value) {
        if (!Double.isFinite(value) || value < 0) {
            return invalidAmount();
        }
        UUID uuid = player.getUniqueId();
        double rounded = Currency.round(value, fractionalDigits);
        activeStoreFor(uuid).setBalance(uuid, rounded);
        return new EconomyResponse(rounded, rounded, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String world, double value) {
        return depositPlayer(playerName, value);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double value) {
        return depositPlayer(player, value);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        return createPlayerAccount(Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        if (hasAccount(player)) {
            return false;
        }
        UUID uuid = player.getUniqueId();
        activeStoreFor(uuid).setBalance(uuid, startingBalance);
        return true;
    }

    @Override
    public boolean createPlayerAccount(String playerName, String world) {
        return createPlayerAccount(playerName);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String world) {
        return createPlayerAccount(player);
    }

    @Override
    public EconomyResponse createBank(String name, String player) {
        return createBank(name, Bukkit.getOfflinePlayer(player));
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return bankManager.createBank(name, player.getUniqueId());
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return bankManager.deleteBank(name);
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return bankManager.bankBalance(name);
    }

    @Override
    public EconomyResponse bankHas(String name, double value) {
        return bankManager.bankHas(name, value);
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double value) {
        return bankManager.bankWithdraw(name, value);
    }

    @Override
    public EconomyResponse bankDeposit(String name, double value) {
        return bankManager.bankDeposit(name, value);
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        return isBankOwner(name, Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return bankManager.isBankOwner(name, player.getUniqueId());
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return isBankMember(name, Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return bankManager.isBankMember(name, player.getUniqueId());
    }

    @Override
    public List<String> getBanks() {
        return bankManager.getBanks();
    }
}
