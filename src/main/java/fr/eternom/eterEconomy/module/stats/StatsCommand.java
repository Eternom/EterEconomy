package fr.eternom.eterEconomy.module.stats;

import fr.eternom.eterLib.helper.message.Messages;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** /ecostats : tableau de bord de l'économie (admin), sur les 7 derniers jours par défaut. */
public class StatsCommand implements CommandExecutor {

    private final StatsGui gui;
    private final Messages messages;

    public StatsCommand(StatsGui gui, Messages messages) {
        this.gui = gui;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) {
            gui.open(player, 7);
        } else {
            messages.send(sender, "command.players-only");
        }
        return true;
    }
}
