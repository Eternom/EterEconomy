package fr.eternom.etereconomy.module.bank;

import fr.eternom.etereconomy.core.Module;
import fr.eternom.etereconomy.helper.config.ConfigManager;
import fr.eternom.etereconomy.helper.config.Currency;
import fr.eternom.etereconomy.helper.config.MessageManager;
import fr.eternom.etereconomy.helper.storage.BankStore;
import fr.eternom.etereconomy.helper.storage.StoreFactory;
import fr.eternom.etereconomy.module.bank.command.CommandBank;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Owns Vault's "bank" extension of the Economy API: shared accounts with one owner and any
 * number of members. Same live/persistent store split as player balances (see
 * {@link fr.eternom.etereconomy.module.economy.EconomyManager}), kept in sync by this module's
 * startup load and the backup module's periodic flush. Registers {@code /bank} on {@link #enable()}.
 */
public class BankManager implements Module {

    // Dots would break the YAML store's "banks.<name>" path; length is capped for MySQL's VARCHAR(64).
    private static final Pattern VALID_BANK_NAME = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    private final JavaPlugin plugin;
    private final MessageManager messages;
    private final BankStore liveStore;
    private final BankStore persistentStore;
    private final boolean enabled;
    private final double startingBalance;
    private final int maxBanksPerPlayer;
    private final int fractionalDigits;
    private final String currencyNameSingular;
    private final String currencyNamePlural;

    public BankManager(JavaPlugin plugin, ConfigManager config, MessageManager messages) {
        this.plugin = plugin;
        this.messages = messages;
        this.liveStore = StoreFactory.liveBankStore(config);
        this.persistentStore = StoreFactory.persistentBankStore(config);
        this.enabled = config.isBankEnabled();
        this.startingBalance = config.getBankStartingBalance();
        this.maxBanksPerPlayer = config.getMaxBanksPerPlayer();
        this.fractionalDigits = config.getEconomyFractionalDigits();
        this.currencyNameSingular = config.getCurrencyNameSingular();
        this.currencyNamePlural = config.getCurrencyNamePlural();
    }

    @Override
    public void enable() {
        Objects.requireNonNull(plugin.getCommand("bank")).setExecutor(new CommandBank(this, messages));

        // Banks have no join/quit event to lazily load off of, so pull them in once at startup.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () ->
                Bukkit.getScheduler().runTask(plugin, this::loadPersistedBanksIntoLiveStore));
    }

    @Override
    public void disable() {
        liveStore.close();
        persistentStore.close();
    }

    /** Same currency (and formatting) as player balances - banks just hold a shared pool of it. */
    public String format(double value) {
        return Currency.format(value, fractionalDigits, currencyNameSingular, currencyNamePlural);
    }

    public BankStore getLiveStore() {
        return liveStore;
    }

    public BankStore getPersistentStore() {
        return persistentStore;
    }

    /**
     * Populates the live cache from the persistent store at startup. Skips banks already live,
     * so a fresher value already in a shared Redis store is never overwritten by a stale snapshot.
     */
    private void loadPersistedBanksIntoLiveStore() {
        for (String name : persistentStore.getBankNames()) {
            if (liveStore.exists(name)) {
                continue;
            }
            liveStore.create(name, persistentStore.getOwner(name), persistentStore.getBalance(name));
            for (UUID member : persistentStore.getMembers(name)) {
                liveStore.addMember(name, member);
            }
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public EconomyResponse createBank(String name, UUID owner) {
        if (!enabled) {
            return disabled();
        }
        if (!VALID_BANK_NAME.matcher(name).matches()) {
            return failure("Bank names may only contain letters, digits, '_' and '-', up to 32 characters");
        }
        if (liveStore.exists(name)) {
            return failure("A bank with that name already exists");
        }
        // Best-effort check: a tight race between two creates could exceed the cap by one, but
        // it's a soft limit, not worth a distributed lock over.
        if (maxBanksPerPlayer > 0 && countOwnedBanks(owner) >= maxBanksPerPlayer) {
            return failure("That player has reached the maximum number of banks they can own");
        }
        liveStore.create(name, owner, startingBalance);
        return success(0, startingBalance);
    }

    public EconomyResponse deleteBank(String name) {
        if (!enabled) {
            return disabled();
        }
        if (!liveStore.exists(name)) {
            return noSuchBank();
        }
        liveStore.delete(name);
        return success(0, 0);
    }

    public EconomyResponse bankBalance(String name) {
        if (!enabled) {
            return disabled();
        }
        if (!liveStore.exists(name)) {
            return noSuchBank();
        }
        double balance = liveStore.getBalance(name);
        return success(balance, balance);
    }

    public EconomyResponse bankHas(String name, double amount) {
        if (!enabled) {
            return disabled();
        }
        if (!Double.isFinite(amount) || amount < 0) {
            return invalidAmount();
        }
        if (!liveStore.exists(name)) {
            return noSuchBank();
        }
        double balance = liveStore.getBalance(name);
        return balance >= amount ? success(0, balance) : failure(balance, "The bank does not have enough money");
    }

    public EconomyResponse bankWithdraw(String name, double amount) {
        if (!enabled) {
            return disabled();
        }
        if (!Double.isFinite(amount) || amount < 0) {
            return invalidAmount();
        }
        if (!liveStore.exists(name)) {
            return noSuchBank();
        }

        double roundedAmount = Currency.round(amount, fractionalDigits);

        // Atomic decrement avoids a read-then-write race; roll back if it pushes the bank negative.
        double newBalance = liveStore.adjustBalance(name, -roundedAmount);
        if (newBalance < 0) {
            double restored = liveStore.adjustBalance(name, roundedAmount);
            return failure(restored, "The bank does not have enough money");
        }
        return success(roundedAmount, newBalance);
    }

    public EconomyResponse bankDeposit(String name, double amount) {
        if (!enabled) {
            return disabled();
        }
        if (!Double.isFinite(amount) || amount < 0) {
            return invalidAmount();
        }
        if (!liveStore.exists(name)) {
            return noSuchBank();
        }

        double roundedAmount = Currency.round(amount, fractionalDigits);
        double newBalance = liveStore.adjustBalance(name, roundedAmount);
        if (!Double.isFinite(newBalance)) {
            liveStore.adjustBalance(name, -roundedAmount);
            return failure("The resulting balance would be too large");
        }
        return success(roundedAmount, newBalance);
    }

    public EconomyResponse isBankOwner(String name, UUID player) {
        if (!enabled) {
            return disabled();
        }
        if (!liveStore.exists(name)) {
            return noSuchBank();
        }
        return player.equals(liveStore.getOwner(name)) ? success(0, 0) : failure("That player does not own this bank");
    }

    public EconomyResponse isBankMember(String name, UUID player) {
        if (!enabled) {
            return disabled();
        }
        if (!liveStore.exists(name)) {
            return noSuchBank();
        }
        boolean member = player.equals(liveStore.getOwner(name)) || liveStore.getMembers(name).contains(player);
        return member ? success(0, 0) : failure("That player is not a member of this bank");
    }

    public List<String> getBanks() {
        return enabled ? liveStore.getBankNames() : List.of();
    }

    public UUID getOwner(String name) {
        return liveStore.getOwner(name);
    }

    public Set<UUID> getMembers(String name) {
        return liveStore.getMembers(name);
    }

    public boolean addMember(String name, UUID member) {
        if (!enabled || !liveStore.exists(name)) {
            return false;
        }
        liveStore.addMember(name, member);
        return true;
    }

    public boolean removeMember(String name, UUID member) {
        if (!enabled || !liveStore.exists(name)) {
            return false;
        }
        liveStore.removeMember(name, member);
        return true;
    }

    private long countOwnedBanks(UUID owner) {
        return liveStore.getBankNames().stream().filter(name -> owner.equals(liveStore.getOwner(name))).count();
    }

    private EconomyResponse noSuchBank() {
        return failure("No such bank");
    }

    private EconomyResponse disabled() {
        return failure("The bank feature is disabled on this server");
    }

    private EconomyResponse invalidAmount() {
        return failure("Invalid amount");
    }

    private EconomyResponse success(double amount, double balance) {
        return new EconomyResponse(amount, balance, EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse failure(String message) {
        return failure(0, message);
    }

    private EconomyResponse failure(double balance, String message) {
        return new EconomyResponse(0, balance, EconomyResponse.ResponseType.FAILURE, message);
    }
}
