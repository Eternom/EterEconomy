package fr.eternom.etereconomy.module.economy.event;

import fr.eternom.etereconomy.helper.storage.BalanceStore;
import fr.eternom.etereconomy.module.bank.BankManager;
import fr.eternom.etereconomy.module.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;

/**
 * Keeps the live store in sync with the durable one: loads the persisted balance into memory on
 * join if it isn't live yet (in REDIS mode a fresher value shared by another sub-server already
 * wins and is never clobbered by a stale snapshot - see {@link BankManager}'s startup load for
 * the same pattern), and flushes it back on quit. Persistent-store I/O always runs off the main
 * thread.
 */
public class ConnectionListener implements Listener {

    private final Plugin plugin;
    private final EconomyManager economyManager;

    public ConnectionListener(Plugin plugin, EconomyManager economyManager) {
        this.plugin = plugin;
        this.economyManager = economyManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        BalanceStore liveStore = economyManager.getLiveStore();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Already live (e.g. a shared Redis store already has this player's balance) - don't
            // clobber it with a persisted snapshot that could be stale.
            if (liveStore.hasAccount(uuid)) {
                return;
            }

            double balance = economyManager.getPersistedOrStartingBalance(uuid);
            Bukkit.getScheduler().runTask(plugin, () -> liveStore.setBalance(uuid, balance));
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        BalanceStore liveStore = economyManager.getLiveStore();

        // Nothing loaded yet (very fast join/quit) - skip, or we'd overwrite the real balance.
        if (!liveStore.hasAccount(uuid)) {
            return;
        }

        double balance = liveStore.getBalance(uuid);
        BalanceStore persistentStore = economyManager.getPersistentStore();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (!persistentStore.setBalance(uuid, balance)) {
                Bukkit.getLogger().warning("[EterEconomy] Failed to save balance for " + uuid + " on quit - see the error above.");
            }
        });
    }
}
