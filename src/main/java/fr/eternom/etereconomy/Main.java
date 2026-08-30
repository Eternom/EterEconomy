package fr.eternom.etereconomy;

import fr.eternom.etereconomy.core.Module;
import fr.eternom.etereconomy.core.ModuleManager;
import fr.eternom.etereconomy.helper.config.ConfigManager;
import fr.eternom.etereconomy.helper.config.MessageManager;
import fr.eternom.etereconomy.module.backup.BackupManager;
import fr.eternom.etereconomy.module.bank.BankManager;
import fr.eternom.etereconomy.module.economy.EconomyManager;
import fr.eternom.etereconomy.module.placeholder.EterPlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;

/**
 * Plugin entry point. Builds the shared config/messages, then constructs and registers each
 * feature module - adding a feature means writing a new {@link Module}
 * and registering it here, not touching anything else in this class.
 */
public class Main extends JavaPlugin {

    private static Main instance;

    private ModuleManager moduleManager;

    @Override
    public void onLoad() {
        instance = this;
    }

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault not found, disabling EterEconomy.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            saveDefaultConfig();
            ConfigManager config = new ConfigManager(this);
            MessageManager messages = new MessageManager(this);
            moduleManager = new ModuleManager();

            BankManager bankManager = new BankManager(this, config, messages);
            moduleManager.register(bankManager);

            EconomyManager economyManager = new EconomyManager(this, config, messages, bankManager);
            moduleManager.register(economyManager);

            moduleManager.register(new EterPlaceholderExpansion(this, economyManager, bankManager));

            moduleManager.register(new BackupManager(this, config, economyManager, bankManager));
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Failed to enable EterEconomy, disabling.", e);
            Bukkit.getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (moduleManager != null) {
            moduleManager.disableAll();
        }
    }

    public static Main getInstance() {
        return instance;
    }
}
