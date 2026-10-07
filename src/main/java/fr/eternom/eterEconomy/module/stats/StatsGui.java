package fr.eternom.eterEconomy.module.stats;

import fr.eternom.eterEconomy.module.history.EconomyStats;
import fr.eternom.eterEconomy.module.history.EconomyStats.SourceTotal;
import fr.eternom.eterEconomy.module.history.EconomyStats.Supply;
import fr.eternom.eterLib.helper.gui.BackButton;
import fr.eternom.eterLib.helper.message.Messages;
import fr.eternom.eterLib.helper.task.Tasks;
import fr.eternom.eterLib.module.player.PlayerDirectory;
import fr.eternom.eterLib.module.player.PlayerDirectory.NetworkPlayer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Menus de /ecostats (admin) : masse monétaire, ce que chaque plugin crée ou détruit, les plus riches, l'évolution
 * jour par jour. Les données sont lues en tâche de fond, puis le menu s'ouvre sur le thread principal.
 */
public class StatsGui {

    /** Données de l'écran principal. history : relevés récents (pour les variations sur 1 et 7 jours). */
    record Overview(Supply supply, List<Supply> history, List<SourceTotal> sources, int days) {
    }

    /** Un joueur du classement, avec son pseudo. */
    record RichPlayer(UUID uuid, String name, double balance) {
    }

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JavaPlugin plugin;
    private final EconomyStats stats;
    private final PlayerDirectory directory;
    private final Economy economy;
    private final Messages messages;
    private final BackButton backButton;

    public StatsGui(JavaPlugin plugin, EconomyStats stats, PlayerDirectory directory, Economy economy, Messages messages,
                    BackButton backButton) {
        this.plugin = plugin;
        this.stats = stats;
        this.directory = directory;
        this.economy = economy;
        this.messages = messages;
        this.backButton = backButton;
    }

    /** Écran principal sur une période de `days` jours (1 = aujourd'hui). */
    public void open(Player viewer, int days) {
        Tasks.async(plugin, viewer, () -> new Overview(stats.currentSupply(), stats.supplyHistory(8), stats.bySource(days), days),
                overview -> viewer.openInventory(new StatsMenu(this, viewer, overview).getInventory()),
                () -> messages.send(viewer, "error.generic"));
    }

    void openRichest(Player viewer) {
        Tasks.async(plugin, viewer, () -> stats.richest(TopMenu.SIZE).stream()
                        .map(rich -> new RichPlayer(rich.uuid(), directory.get(rich.uuid()).map(NetworkPlayer::name).orElse("?"),
                                rich.balance()))
                        .toList(),
                richest -> viewer.openInventory(new TopMenu(this, viewer, richest).getInventory()),
                () -> messages.send(viewer, "error.generic"));
    }

    void openSupply(Player viewer) {
        Tasks.async(plugin, viewer, () -> stats.supplyHistory(SupplyMenu.SIZE),
                history -> viewer.openInventory(new SupplyMenu(this, viewer, history).getInventory()),
                () -> messages.send(viewer, "error.generic"));
    }

    /** Montant mis en forme par l'économie : « 1 234 Heloks ». */
    String money(double amount) {
        return economy.format(amount);
    }

    /** Montant avec son signe : « +1 234 Heloks », « -50 Heloks ». */
    String signed(double amount) {
        return (amount > 0 ? "+" : amount < 0 ? "-" : "") + economy.format(Math.abs(amount));
    }

    String date(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).format(DATE);
    }

    Messages messages() {
        return messages;
    }

    BackButton backButton() {
        return backButton;
    }
}
