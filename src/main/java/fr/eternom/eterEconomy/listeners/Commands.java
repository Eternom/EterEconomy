package fr.eternom.eterEconomy.listeners;

import fr.eternom.eterEconomy.Main;
import fr.eternom.eterEconomy.module.stats.StatsCommand;
import org.bukkit.command.PluginCommand;

import java.util.List;
import java.util.Objects;

public class Commands {

    public Commands(Main main) {
        PluginCommand stats = Objects.requireNonNull(main.getCommand("ecostats"), "Commande absente du plugin.yml : ecostats");
        stats.setExecutor(new StatsCommand(main.getStats(), main.getMessages()));
        stats.setTabCompleter((sender, command, label, args) -> List.of());
    }
}
