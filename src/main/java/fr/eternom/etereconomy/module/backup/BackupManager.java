package fr.eternom.etereconomy.module.backup;

import fr.eternom.etereconomy.core.Module;
import fr.eternom.etereconomy.helper.config.ConfigManager;
import fr.eternom.etereconomy.helper.storage.BalanceStore;
import fr.eternom.etereconomy.helper.storage.BankStore;
import fr.eternom.etereconomy.module.bank.BankManager;
import fr.eternom.etereconomy.module.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Periodically flushes the live economy/bank data (in-memory or Redis) into their persistent
 * store, and once more at shutdown. {@link #runBackup()} does blocking disk/DB I/O and must only
 * ever be called off the main thread.
 */
public class BackupManager implements Module {

    private static final long TICKS_PER_MINUTE = 1200L;

    private record BackupTally(int succeeded, int failed) {
    }

    private final JavaPlugin plugin;
    private final ConfigManager config;
    private final EconomyManager economyManager;
    private final BankManager bankManager;
    private BukkitTask task;
    private boolean scheduled;

    public BackupManager(JavaPlugin plugin, ConfigManager config, EconomyManager economyManager, BankManager bankManager) {
        this.plugin = plugin;
        this.config = config;
        this.economyManager = economyManager;
        this.bankManager = bankManager;
    }

    @Override
    public void enable() {
        if (!config.isBackupEnabled()) {
            Bukkit.getLogger().info("[EterEconomy] backup.enabled is false - periodic backups are disabled.");
            return;
        }

        long intervalTicks = config.getBackupIntervalMinutes() * TICKS_PER_MINUTE;
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::runBackup, intervalTicks, intervalTicks);
        scheduled = true;
    }

    @Override
    public void disable() {
        if (task != null) {
            task.cancel();
        }
        if (scheduled) {
            // Async tasks aren't guaranteed to run once the plugin is disabling, so flush synchronously here.
            runBackup();
        }
    }

    public void runBackup() {
        BackupTally economy = backupEconomy();
        BackupTally banks = backupBanks();

        if (economy.failed() == 0 && banks.failed() == 0) {
            Bukkit.getLogger().info("[EterEconomy] Backup complete: " + economy.succeeded() + " balance(s), " + banks.succeeded() + " bank(s) saved.");
        } else {
            Bukkit.getLogger().warning("[EterEconomy] Backup finished with errors: "
                    + economy.succeeded() + " balance(s) saved (" + economy.failed() + " failed), "
                    + banks.succeeded() + " bank(s) saved (" + banks.failed() + " failed) - see warnings above for details.");
        }
    }

    private BackupTally backupEconomy() {
        BalanceStore persistent = economyManager.getPersistentStore();
        Map<UUID, Double> live = economyManager.getLiveStore().getAllBalances();

        int failed = 0;
        for (Map.Entry<UUID, Double> entry : live.entrySet()) {
            if (!persistent.setBalance(entry.getKey(), entry.getValue())) {
                failed++;
            }
        }
        return new BackupTally(live.size() - failed, failed);
    }

    private BackupTally backupBanks() {
        BankStore persistent = bankManager.getPersistentStore();
        BankStore live = bankManager.getLiveStore();

        Set<String> liveNames = new HashSet<>(live.getBankNames());
        Set<String> persistedNames = new HashSet<>(persistent.getBankNames());

        int succeeded = 0;
        int failed = 0;

        for (String name : liveNames) {
            boolean success = persistedNames.contains(name)
                    ? persistent.setBalance(name, live.getBalance(name))
                    : persistent.create(name, live.getOwner(name), live.getBalance(name));
            success &= reconcileMembers(persistent, name, live.getMembers(name));

            if (success) {
                succeeded++;
            } else {
                failed++;
            }
        }

        // Deleted from the live store since the last backup - remove it persistently too.
        for (String name : persistedNames) {
            if (!liveNames.contains(name)) {
                if (persistent.delete(name)) {
                    succeeded++;
                } else {
                    failed++;
                }
            }
        }

        return new BackupTally(succeeded, failed);
    }

    private boolean reconcileMembers(BankStore persistent, String name, Set<UUID> liveMembers) {
        Set<UUID> persistedMembers = persistent.getMembers(name);
        boolean success = true;
        for (UUID member : liveMembers) {
            if (!persistedMembers.contains(member)) {
                success &= persistent.addMember(name, member);
            }
        }
        for (UUID member : persistedMembers) {
            if (!liveMembers.contains(member)) {
                success &= persistent.removeMember(name, member);
            }
        }
        return success;
    }
}
