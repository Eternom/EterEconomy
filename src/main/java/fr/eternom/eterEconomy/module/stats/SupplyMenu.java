package fr.eternom.eterEconomy.module.stats;

import fr.eternom.eterEconomy.module.history.EconomyStats.Supply;
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
 * Masse monétaire jour par jour sur les 28 derniers jours (le plus récent en haut à gauche), avec la variation par
 * rapport à la veille : vert si elle monte, rouge si elle baisse. Une montée rapide et durable = inflation.
 */
class SupplyMenu implements Menu {

    static final int SIZE = 28;
    private static final int BACK = 49;

    private final StatsGui gui;
    private final Messages messages;
    private final Player viewer;
    private final Inventory inventory;

    SupplyMenu(StatsGui gui, Player viewer, List<Supply> history) {
        this.gui = gui;
        this.messages = gui.messages();
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, 54, text("stats.supply.title"));
        ItemStack neutral = Items.pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot / 9 == 0 || slot / 9 == 5 || slot % 9 == 0 || slot % 9 == 8) {
                inventory.setItem(slot, neutral);
            }
        }
        for (int i = 0; i < history.size() && i < SIZE; i++) {
            Supply day = history.get(i);
            Supply previous = i + 1 < history.size() ? history.get(i + 1) : null;
            List<Component> lore = new ArrayList<>();
            lore.add(text("stats.supply.accounts", "count", String.valueOf(day.accounts())));
            Material icon = Material.PAPER;
            if (previous != null && previous.day() == day.day() - 1) {
                double change = day.total() - previous.total();
                icon = change > 0 ? Material.LIME_DYE : change < 0 ? Material.RED_DYE : Material.PAPER;
                lore.add(text("stats.supply.change", "amount", gui.signed(change)));
            }
            int slot = (i / 7 + 1) * 9 + i % 7 + 1;
            inventory.setItem(slot, Items.item(icon, text("stats.supply.day", "date", gui.date(day.day()),
                    "amount", gui.money(day.total())), lore));
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
