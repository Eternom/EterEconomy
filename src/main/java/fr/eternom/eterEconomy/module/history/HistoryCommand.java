package fr.eternom.eterEconomy.module.history;

import fr.eternom.eterEconomy.module.history.TransactionLog.Entry;
import fr.eternom.eterLib.helper.economy.Money;
import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** /transactions : les 15 derniers mouvements de son argent (qui a payé, d'où vient un gain), du plus récent au plus ancien. */
public class HistoryCommand implements CommandExecutor {

    private static final int LIMIT = 15;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final JavaPlugin plugin;
    private final TransactionLog log;
    private final Messages messages;
    private final ZoneId zone;

    public HistoryCommand(JavaPlugin plugin, TransactionLog log, Messages messages, ZoneId zone) {
        this.plugin = plugin;
        this.log = log;
        this.messages = messages;
        this.zone = zone;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.players-only");
            return true;
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Entry> entries;
            try {
                entries = log.recent(player.getUniqueId(), LIMIT);
            } catch (RuntimeException e) {
                Bukkit.getScheduler().runTask(plugin, () -> messages.send(player, "error.generic"));
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> show(player, entries));
        });
        return true;
    }

    private void show(Player player, List<Entry> entries) {
        if (!player.isOnline()) {
            return;
        }
        if (entries.isEmpty()) {
            messages.send(player, "history.empty");
            return;
        }
        messages.send(player, "history.header", "count", String.valueOf(entries.size()));
        for (Entry entry : entries) {
            String date = DATE.format(Instant.ofEpochMilli(entry.createdAt()).atZone(zone));
            player.sendMessage(messages.get(player, entry.amount() >= 0 ? "history.in" : "history.out",
                    "date", date, "amount", Money.format(Math.abs(entry.amount())), "source", entry.source(),
                    "balance", Money.format(entry.balance())));
        }
    }
}
