package fr.eternom.eterEconomy.module.stats;

import fr.eternom.eterEconomy.module.history.EconomyStats.SourceTotal;
import fr.eternom.eterEconomy.module.history.EconomyStats.Supply;
import fr.eternom.eterEconomy.module.stats.StatsGui.Overview;
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
import java.util.Map;
import java.util.Set;

/**
 * Menu /ecostats, 6 lignes :
 * <pre>
 *  ▣ ▣ ▢ ▢ $ ▢ ▢ ▣ ▣     $ = masse monétaire (variations sur 1 et 7 jours, totaux de la période)
 *  ▣ · ⏱ · ⏱ · ⏱ · ▣     ⏱ = période : aujourd'hui, 7 jours, 30 jours (la choisie brille)
 *  ▢ · · · · · · · ▢     sources (plugins) : émeraude = crée de l'argent, redstone = en détruit,
 *  ▢ · · · · · · · ▢     les plus importantes en premier
 *  ▣ · · · · · · · ▣
 *  ▣ ▣ ▢ ≡ « ♛ ▢ ▣ ▣     ≡ = masse jour par jour, ♛ = les plus riches, « = retour ou fermer
 * </pre>
 */
class StatsMenu implements Menu {

    private static final int SUMMARY = 4;
    /** Emplacement -> nombre de jours de la période. */
    private static final Map<Integer, Integer> PERIODS = Map.of(11, 1, 13, 7, 15, 30);
    private static final List<Integer> SOURCE_SLOTS = List.of(19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43);
    private static final int SUPPLY = 48;
    private static final int BACK = 49;
    private static final int RICHEST = 50;
    private static final Set<Integer> ACCENT_FRAME = Set.of(0, 1, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53);

    private final StatsGui gui;
    private final Messages messages;
    private final Player viewer;
    private final Overview overview;
    private final Inventory inventory;

    StatsMenu(StatsGui gui, Player viewer, Overview overview) {
        this.gui = gui;
        this.messages = gui.messages();
        this.viewer = viewer;
        this.overview = overview;
        this.inventory = Bukkit.createInventory(this, 54, text("stats.title"));
        render();
    }

    @Override
    public void onClick(Player player, int slot, ClickType click) {
        Integer days = PERIODS.get(slot);
        if (days != null && days != overview.days()) {
            Sounds.page(player);
            gui.open(player, days);
        } else if (slot == SUPPLY) {
            Sounds.page(player);
            gui.openSupply(player);
        } else if (slot == RICHEST) {
            Sounds.page(player);
            gui.openRichest(player);
        } else if (slot == BACK) {
            gui.backButton().click(player);
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private void render() {
        ItemStack accent = Items.pane(Material.ORANGE_STAINED_GLASS_PANE);
        ItemStack neutral = Items.pane(Material.GRAY_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) {
                inventory.setItem(slot, ACCENT_FRAME.contains(slot) ? accent : neutral);
            }
        }
        inventory.setItem(SUMMARY, summary());
        PERIODS.forEach((slot, days) -> inventory.setItem(slot, Items.item(Material.CLOCK,
                text("stats.period." + days), List.of(text(days == overview.days() ? "stats.period.selected" : "stats.period.click")),
                days == overview.days())));

        List<SourceTotal> sources = overview.sources();
        for (int i = 0; i < sources.size() && i < SOURCE_SLOTS.size(); i++) {
            inventory.setItem(SOURCE_SLOTS.get(i), sourceItem(sources.get(i)));
        }
        if (sources.isEmpty()) {
            inventory.setItem(31, Items.item(Material.PAPER, text("stats.source.none"), List.of()));
        }
        inventory.setItem(SUPPLY, Items.item(Material.BOOK, text("stats.supply.button"), List.of(text("stats.supply.button-lore"))));
        inventory.setItem(RICHEST, Items.item(Material.GOLD_INGOT, text("stats.richest.button"), List.of(text("stats.richest.button-lore"))));
        inventory.setItem(BACK, gui.backButton().item(viewer));
    }

    private ItemStack summary() {
        Supply supply = overview.supply();
        List<Component> lore = new ArrayList<>();
        lore.add(text("stats.summary.accounts", "count", String.valueOf(supply.accounts())));
        lore.add(change("stats.summary.change-day", 1));
        lore.add(change("stats.summary.change-week", 7));
        lore.add(Component.empty());
        double created = overview.sources().stream().mapToDouble(SourceTotal::created).sum();
        double destroyed = overview.sources().stream().mapToDouble(SourceTotal::destroyed).sum();
        lore.add(text("stats.summary.period", "period", messages.plain(viewer, "stats.period." + overview.days())));
        lore.add(text("stats.summary.created", "amount", gui.signed(created)));
        lore.add(text("stats.summary.destroyed", "amount", gui.signed(-destroyed)));
        lore.add(text(created >= destroyed ? "stats.summary.net-up" : "stats.summary.net-down", "amount", gui.signed(created - destroyed)));
        return Items.item(Material.GOLD_BLOCK, text("stats.summary.name", "amount", gui.money(supply.total())), lore, true);
    }

    /** Variation de la masse monétaire depuis le relevé d'il y a `days` jours (s'il existe). */
    private Component change(String key, int days) {
        long day = overview.supply().day() - days;
        return overview.history().stream()
                .filter(supply -> supply.day() == day)
                .findFirst()
                .map(past -> text(key, "amount", gui.signed(overview.supply().total() - past.total())))
                .orElse(text(key, "amount", messages.plain(viewer, "stats.summary.unknown")));
    }

    private ItemStack sourceItem(SourceTotal source) {
        double net = source.net();
        Material icon = net > 0 ? Material.EMERALD : net < 0 ? Material.REDSTONE : Material.PAPER;
        List<Component> lore = List.of(
                text("stats.source.created", "amount", gui.signed(source.created())),
                text("stats.source.destroyed", "amount", gui.signed(-source.destroyed())),
                text("stats.source.operations", "count", String.valueOf(source.operations())),
                Component.empty(),
                text(net > 0 ? "stats.source.faucet" : net < 0 ? "stats.source.sink" : "stats.source.neutral", "amount", gui.signed(net)));
        return Items.item(icon, text("stats.source.name", "source", source.source()), lore);
    }

    private Component text(String key, String... placeholders) {
        return messages.get(viewer, key, placeholders);
    }
}
