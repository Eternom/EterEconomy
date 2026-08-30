package fr.eternom.etereconomy.module.placeholder;

import fr.eternom.etereconomy.core.Module;
import fr.eternom.etereconomy.module.bank.BankManager;
import fr.eternom.etereconomy.module.economy.EconomyManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Exposes what Vault's own PlaceholderAPI expansion doesn't: bank balances and a player's
 * rank. Entirely optional - {@link #enable()} only registers with PlaceholderAPI if it's
 * actually installed. Placeholders:
 * <ul>
 *     <li>{@code %etereconomy_balance%} / {@code %etereconomy_balance_formatted%}</li>
 *     <li>{@code %etereconomy_rank%} - position in the full balance ranking, "-" if unranked</li>
 *     <li>{@code %etereconomy_currency_singular%} / {@code %etereconomy_currency_plural%}</li>
 *     <li>{@code %etereconomy_bank_count%} - number of banks the player owns</li>
 *     <li>{@code %etereconomy_bank_balance_<name>%}</li>
 * </ul>
 */
public class EterPlaceholderExpansion extends PlaceholderExpansion implements Module {

    private static final String BANK_BALANCE_PREFIX = "bank_balance_";

    private final JavaPlugin plugin;
    private final EconomyManager economyManager;
    private final BankManager bankManager;

    public EterPlaceholderExpansion(JavaPlugin plugin, EconomyManager economyManager, BankManager bankManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
        this.bankManager = bankManager;
    }

    @Override
    public void enable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            plugin.getLogger().info("PlaceholderAPI not found, skipping placeholder registration.");
            return;
        }
        register();
    }

    @Override
    public void disable() {
        unregister();
    }

    @Override
    public @NotNull String getIdentifier() {
        return "etereconomy";
    }

    @Override
    public @NotNull String getAuthor() {
        return "JanjeVuk";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) {
            return "";
        }

        String key = params.toLowerCase(Locale.ROOT);

        switch (key) {
            case "balance":
                return String.valueOf(economyManager.getBalance(player));
            case "balance_formatted":
                return economyManager.format(economyManager.getBalance(player));
            case "rank":
                int rank = economyManager.getRank(player.getUniqueId());
                return rank > 0 ? String.valueOf(rank) : "-";
            case "currency_singular":
                return economyManager.currencyNameSingular();
            case "currency_plural":
                return economyManager.currencyNamePlural();
            case "bank_count":
                return String.valueOf(bankManager.getBanks().stream()
                        .filter(name -> player.getUniqueId().equals(bankManager.getOwner(name)))
                        .count());
            default:
                break;
        }

        if (key.startsWith(BANK_BALANCE_PREFIX)) {
            String bankName = params.substring(BANK_BALANCE_PREFIX.length());
            EconomyResponse response = bankManager.bankBalance(bankName);
            return response.transactionSuccess() ? bankManager.format(response.balance) : null;
        }

        return null;
    }
}
