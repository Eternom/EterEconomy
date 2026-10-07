package fr.eternom.eterEconomy;

import fr.eternom.eterEconomy.listeners.Commands;
import fr.eternom.eterEconomy.module.account.AccountRepository;
import fr.eternom.eterEconomy.module.bank.BankRepository;
import fr.eternom.eterEconomy.module.history.EconomyStats;
import fr.eternom.eterEconomy.module.history.HistoryMaintenance;
import fr.eternom.eterEconomy.module.history.TransactionLog;
import fr.eternom.eterEconomy.module.stats.StatsGui;
import fr.eternom.eterEconomy.module.vault.VaultEconomy;
import fr.eternom.eterLib.EterLib;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.sql.Database;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.DateTimeException;
import java.time.ZoneId;

/**
 * Économie du réseau, fournie à Vault : soldes et banques en base commune (etereconomy_*), Redis facultatif, et un
 * journal de chaque mouvement pour suivre l'équilibre (/ecostats). Les commandes des joueurs (/money, /pay, /eco)
 * sont dans EterEssential.
 */
public final class Main extends JavaPlugin {

    /** Version minimale d'EterLib : menus avec bouton Retour/Fermer depuis 1.5.1. */
    private static final String REQUIRED_ETERLIB = "1.5.1";

    /** Préfixe des tables d'EterEconomy dans la base commune : etereconomy_balances, etereconomy_transactions... */
    private static final String TABLE_PREFIX = "etereconomy_";
    private static final long MAINTENANCE_TICKS = 60 * 60 * 20;

    private Messages messages;
    private StatsGui stats;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // En premier : vérifie la version d'EterLib (un EterLib < 1.3.0 n'a pas requireVersion, d'où le catch)
        try {
            if (!EterLib.requireVersion(this, REQUIRED_ETERLIB)) {
                return;
            }
        } catch (LinkageError tooOld) {
            getLogger().severe("EterLib " + REQUIRED_ETERLIB + " ou plus récent est nécessaire.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        EterLib lib = EterLib.get();
        messages = lib.messages(this, "en_us", "fr_fr");
        Database database = lib.database(TABLE_PREFIX);
        ZoneId zone = zone();

        int digits = Math.clamp(getConfig().getInt("currency.fractional-digits", 0), 0, 4);
        AccountRepository accounts = new AccountRepository(database, lib.getRedis(),
                Math.max(0, getConfig().getDouble("currency.starting-balance", 0)), digits);
        BankRepository banks = getConfig().getBoolean("banks.enabled", true)
                ? new BankRepository(database, Math.max(0, getConfig().getDouble("banks.starting-balance", 0)), digits)
                : null;
        TransactionLog log = new TransactionLog(this, database, lib.getServerName(), zone);

        VaultEconomy economy = new VaultEconomy(accounts, banks, lib.getPlayers(), log,
                getConfig().getString("currency.name-singular", "Helok"),
                getConfig().getString("currency.name-plural", "Heloks"), digits);
        // Priorité haute : EterEconomy l'emporte sur l'économie d'un autre plugin (Essentials...) si les deux sont installés
        Bukkit.getServicesManager().register(Economy.class, economy, this, ServicePriority.High);

        EconomyStats economyStats = new EconomyStats(database, zone, banks != null);
        stats = new StatsGui(this, economyStats, lib.getPlayers(), economy, messages,
                lib.backButton(getConfig().getString("menus.stats.back-command", "")));
        HistoryMaintenance maintenance = new HistoryMaintenance(this, database, economyStats, zone,
                Math.max(30, getConfig().getInt("history.retention-days", 365)));
        // Relevé de la masse monétaire et archivage : une minute après le démarrage, puis toutes les heures
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, maintenance::run, 60 * 20, MAINTENANCE_TICKS);

        new Commands(this);
        getLogger().info("Économie fournie à Vault" + (lib.getRedis() != null ? " (copie des soldes dans Redis)" : "")
                + (banks != null ? ", banques activées" : "") + ", journal des transactions actif");
    }

    @Override
    public void onDisable() {
        Bukkit.getServicesManager().unregisterAll(this);
    }

    /** Fuseau des journées du journal (history.time-zone), Europe/Paris si invalide. */
    private ZoneId zone() {
        String zone = getConfig().getString("history.time-zone", "Europe/Paris");
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException e) {
            getLogger().warning("history.time-zone invalide (" + zone + "), Europe/Paris utilisé");
            return ZoneId.of("Europe/Paris");
        }
    }

    public Messages getMessages() {
        return messages;
    }

    public StatsGui getStats() {
        return stats;
    }
}
