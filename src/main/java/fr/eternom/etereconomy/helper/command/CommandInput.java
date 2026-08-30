package fr.eternom.etereconomy.helper.command;

import fr.eternom.etereconomy.helper.config.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;

/**
 * Argument parsing shared by {@code /eco} and {@code /bank}: both take a player name and, most
 * of the time, an amount, and both need to reject the same malformed input the same way.
 */
public final class CommandInput {

    private CommandInput() {
    }

    /** @return the parsed amount, or -1 (after messaging the sender) if it's not a valid non-negative number. */
    public static double parseAmount(CommandSender sender, String raw, MessageManager messages) {
        try {
            double amount = Double.parseDouble(raw);
            if (!Double.isFinite(amount)) {
                sender.sendMessage(messages.get(sender, "invalid-amount"));
                return -1;
            }
            if (amount < 0) {
                sender.sendMessage(messages.get(sender, "negative-amount"));
                return -1;
            }
            return amount;
        } catch (NumberFormatException e) {
            sender.sendMessage(messages.get(sender, "invalid-amount"));
            return -1;
        }
    }

    /** @return the named player, or null (after messaging the sender) if they've never been seen on this server. */
    public static OfflinePlayer resolveKnownPlayer(CommandSender sender, String name, MessageManager messages) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        if (!player.hasPlayedBefore() && !player.isOnline()) {
            sender.sendMessage(messages.get(sender, "player-never-joined"));
            return null;
        }
        return player;
    }
}
