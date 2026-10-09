package fr.eternom.eterEconomy.module.vault;

import fr.eternom.eterEconomy.api.EconomyApi;
import fr.eternom.eterEconomy.module.account.AccountRepository;
import fr.eternom.eterEconomy.module.bank.BankRepository;
import fr.eternom.eterEconomy.module.history.TransactionLog;
import fr.eternom.eterLib.module.player.PlayerDirectory;
import fr.eternom.eterLib.module.player.PlayerDirectory.NetworkPlayer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import net.milkbowl.vault.economy.EconomyResponse.ResponseType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Fournisseur d'économie de Vault : tous les plugins (EterEssential, EterTab, boutiques...) passent par lui.
 *
 * Chaque appel va directement en base (voir {@link AccountRepository}) : à appeler hors du thread principal, comme le
 * font les plugins Eter. Les montants sont arrondis aux décimales de la monnaie ; un montant négatif ou invalide est
 * refusé. Le monde est ignoré (une seule économie pour tout le réseau). Les variantes par pseudo retrouvent le joueur
 * dans eter_players (n'importe quel joueur déjà venu sur le réseau). Chaque mouvement réussi est journalisé
 * ({@link TransactionLog}).
 */
@SuppressWarnings("deprecation") // Vault impose aussi ses anciennes signatures (pseudo, monde)
public class VaultEconomy implements Economy, EconomyApi {

    private static final String INVALID_AMOUNT = "Invalid amount";
    private static final String UNKNOWN_PLAYER = "Unknown player";

    private final AccountRepository accounts;
    private final BankRepository banks; // null si les banques sont désactivées
    private final PlayerDirectory directory;
    private final TransactionLog log;
    private final String singular;
    private final String plural;
    private final int fractionalDigits;

    public VaultEconomy(AccountRepository accounts, BankRepository banks, PlayerDirectory directory, TransactionLog log,
                        String singular, String plural, int fractionalDigits) {
        this.accounts = accounts;
        this.banks = banks;
        this.directory = directory;
        this.log = log;
        this.singular = singular;
        this.plural = plural;
        this.fractionalDigits = fractionalDigits;
    }

    // ---------- Informations ----------

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
        return banks != null;
    }

    @Override
    public int fractionalDigits() {
        return fractionalDigits;
    }

    /** 1234.5 -> "1 234,50 Heloks" (séparateurs français), singulier pour 1. */
    @Override
    public String format(double amount) {
        String number = String.format(Locale.FRANCE, "%,." + fractionalDigits + "f", amount);
        return number + " " + (Math.abs(amount) == 1 ? singular : plural);
    }

    @Override
    public String currencyNamePlural() {
        return plural;
    }

    @Override
    public String currencyNameSingular() {
        return singular;
    }

    // ---------- EconomyApi (plugins Eter : la source est donnée) ----------

    @Override
    public double balance(UUID player) {
        return accounts.balance(player);
    }

    @Override
    public boolean has(UUID player, double amount) {
        return accounts.balance(player) >= amount;
    }

    @Override
    public boolean withdraw(UUID player, double amount, String source) {
        double rounded = round(amount);
        if (rounded < 0) {
            return false;
        }
        OptionalDouble balance = accounts.withdraw(player, rounded);
        balance.ifPresent(after -> log.player(player, -rounded, after, source));
        return balance.isPresent();
    }

    @Override
    public boolean deposit(UUID player, double amount, String source) {
        double rounded = round(amount);
        if (rounded < 0) {
            return false;
        }
        log.player(player, rounded, accounts.deposit(player, rounded), source);
        return true;
    }

    @Override
    public boolean transfer(UUID from, UUID to, double amount, String source) {
        if (!withdraw(from, amount, source)) {
            return false;
        }
        try {
            return deposit(to, amount, source);
        } catch (RuntimeException e) {
            deposit(from, amount, source); // versement raté : rendu
            throw e;
        }
    }

    // ---------- Comptes des joueurs ----------

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return accounts.exists(player.getUniqueId());
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return accounts.balance(player.getUniqueId());
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return getBalance(player) >= amount;
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        double rounded = round(amount);
        if (rounded < 0) {
            return failure(INVALID_AMOUNT);
        }
        UUID uuid = player.getUniqueId();
        OptionalDouble balance = accounts.withdraw(uuid, rounded);
        if (balance.isEmpty()) {
            return new EconomyResponse(0, accounts.balance(uuid), ResponseType.FAILURE, "Insufficient funds");
        }
        log.player(uuid, -rounded, balance.getAsDouble());
        return new EconomyResponse(rounded, balance.getAsDouble(), ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        double rounded = round(amount);
        if (rounded < 0) {
            return failure(INVALID_AMOUNT);
        }
        double balance = accounts.deposit(player.getUniqueId(), rounded);
        log.player(player.getUniqueId(), rounded, balance);
        return new EconomyResponse(rounded, balance, ResponseType.SUCCESS, null);
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return accounts.create(player.getUniqueId());
    }

    // ---------- Banques ----------

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer owner) {
        if (banks == null) {
            return noBanks();
        }
        return banks.create(name, owner.getUniqueId())
                ? new EconomyResponse(0, banks.balance(name).orElse(0), ResponseType.SUCCESS, null)
                : failure("A bank with this name already exists");
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        if (banks == null) {
            return noBanks();
        }
        return banks.delete(name) ? new EconomyResponse(0, 0, ResponseType.SUCCESS, null) : failure("Unknown bank");
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        if (banks == null) {
            return noBanks();
        }
        OptionalDouble balance = banks.balance(name);
        return balance.isPresent()
                ? new EconomyResponse(0, balance.getAsDouble(), ResponseType.SUCCESS, null)
                : failure("Unknown bank");
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        EconomyResponse balance = bankBalance(name);
        if (!balance.transactionSuccess()) {
            return balance;
        }
        return balance.balance >= amount
                ? new EconomyResponse(0, balance.balance, ResponseType.SUCCESS, null)
                : new EconomyResponse(0, balance.balance, ResponseType.FAILURE, "Insufficient funds");
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        if (banks == null) {
            return noBanks();
        }
        double rounded = round(amount);
        if (rounded < 0) {
            return failure(INVALID_AMOUNT);
        }
        OptionalDouble balance = banks.withdraw(name, rounded);
        if (balance.isEmpty()) {
            return failure("Unknown bank or insufficient funds");
        }
        log.bank(name, -rounded, balance.getAsDouble());
        return new EconomyResponse(rounded, balance.getAsDouble(), ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        if (banks == null) {
            return noBanks();
        }
        double rounded = round(amount);
        if (rounded < 0) {
            return failure(INVALID_AMOUNT);
        }
        OptionalDouble balance = banks.deposit(name, rounded);
        if (balance.isEmpty()) {
            return failure("Unknown bank");
        }
        log.bank(name, rounded, balance.getAsDouble());
        return new EconomyResponse(rounded, balance.getAsDouble(), ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        if (banks == null) {
            return noBanks();
        }
        return banks.owner(name).filter(player.getUniqueId()::equals).isPresent()
                ? new EconomyResponse(0, 0, ResponseType.SUCCESS, null)
                : failure("Not the bank owner");
    }

    /** Vault ne sait pas ajouter de membres : le propriétaire est le seul membre. */
    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return isBankOwner(name, player);
    }

    @Override
    public List<String> getBanks() {
        return banks == null ? List.of() : banks.names();
    }

    // ---------- Variantes par pseudo et par monde (anciennes signatures de Vault) ----------

    @Override
    public boolean hasAccount(String playerName) {
        UUID uuid = uuidOf(playerName);
        return uuid != null && accounts.exists(uuid);
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
        UUID uuid = uuidOf(playerName);
        return uuid == null ? 0 : accounts.balance(uuid);
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
    public boolean has(String playerName, double amount) {
        return getBalance(playerName) >= amount;
    }

    @Override
    public boolean has(String playerName, String world, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String world, double amount) {
        return has(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        OfflinePlayer player = offline(playerName);
        return player == null ? failure(UNKNOWN_PLAYER) : withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(String playerName, String world, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String world, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, double amount) {
        OfflinePlayer player = offline(playerName);
        return player == null ? failure(UNKNOWN_PLAYER) : depositPlayer(player, amount);
    }

    @Override
    public EconomyResponse depositPlayer(String playerName, String world, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String world, double amount) {
        return depositPlayer(player, amount);
    }

    @Override
    public boolean createPlayerAccount(String playerName) {
        UUID uuid = uuidOf(playerName);
        return uuid != null && accounts.create(uuid);
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
    public EconomyResponse createBank(String name, String playerName) {
        OfflinePlayer owner = offline(playerName);
        return owner == null ? failure(UNKNOWN_PLAYER) : createBank(name, owner);
    }

    @Override
    public EconomyResponse isBankOwner(String name, String playerName) {
        OfflinePlayer player = offline(playerName);
        return player == null ? failure(UNKNOWN_PLAYER) : isBankOwner(name, player);
    }

    @Override
    public EconomyResponse isBankMember(String name, String playerName) {
        return isBankOwner(name, playerName);
    }

    // ---------- Outils ----------

    /** Arrondi aux décimales de la monnaie ; -1 si le montant n'est pas un nombre valide. */
    @Override
    public double round(double amount) {
        if (!Double.isFinite(amount) || amount < 0) {
            return -1;
        }
        return BigDecimal.valueOf(amount).setScale(fractionalDigits, RoundingMode.HALF_UP).doubleValue();
    }

    private UUID uuidOf(String playerName) {
        return directory.find(playerName).map(NetworkPlayer::uuid).orElse(null);
    }

    private OfflinePlayer offline(String playerName) {
        UUID uuid = uuidOf(playerName);
        return uuid == null ? null : Bukkit.getOfflinePlayer(uuid);
    }

    private static EconomyResponse failure(String message) {
        return new EconomyResponse(0, 0, ResponseType.FAILURE, message);
    }

    private static EconomyResponse noBanks() {
        return new EconomyResponse(0, 0, ResponseType.NOT_IMPLEMENTED, "Banks are disabled");
    }
}
