package fr.eternom.eterEconomy.module.stats;

import fr.eternom.eterEconomy.module.stats.StatsGui.RichPlayer;
import fr.eternom.eterLib.helper.gui.Items;
import fr.eternom.eterLib.helper.gui.Menu;
import fr.eternom.eterLib.helper.gui.Sounds;
import fr.eternom.eterLib.helper.message.Messages;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Les 28 joueurs les plus riches (tête, rang, solde, part de la masse des 28), 6 lignes ; « = retour au menu /ecostats.
 * Aide à repérer une fortune anormale (duplication, faille dans un prix...).
 */
class TopMenu implements Menu {

    static final int SIZE = 28;
    private static final int BACK = 49;

    private final StatsGui gui;
    private final Messages messages;
    private final Player viewer;
    private final Inventory inventory;

    TopMenu(StatsGui gui, Player viewer, List<RichPlayer> richest) {
        this.gui = gui;
        this.messages = gui.messages();
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54, text("stats.richest.title"));
        ItemStack neutral = Items.pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot / 9 == 0 || slot / 9 == 5 || slot % 9 == 0 || slot % 9 == 8) {
                inventory.setItem(slot, neutral);
            }
        }
        double total = richest.stream().mapToDouble(RichPlayer::balance).sum();
        for (int i = 0; i < richest.size(); i++) {
            RichPlayer rich = richest.get(i);
            int slot = (i / 7 + 1) * 9 + i % 7 + 1;
            String share = total > 0 ? String.valueOf(Math.round(rich.balance() * 100 / total)) : "0";
            List<Component> lore = new ArrayList<>();
            lore.add(text("stats.richest.balance", "amount", gui.money(rich.balance())));
            lore.add(text("stats.richest.share", "share", share));
            inventory.setItem(slot, Items.head(Bukkit.createProfile(rich.uuid(), rich.name()),
                    text("stats.richest.player", "rank", String.valueOf(i + 1), "player", rich.name()), lore));
        }
        inventory.setItem(BACK, Items.item(Material.OAK_DOOR, text("stats.back"), List.of()));
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        if (slot == BACK) {
            Sounds.page(player);
            gui.open(player, 7);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private Component text(String key, String... placeholders) {
        return messages.get(viewer, key, placeholders);
    }
}
