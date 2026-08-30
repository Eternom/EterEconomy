package fr.eternom.etereconomy.module.economy.command;

import fr.eternom.etereconomy.helper.command.CommandInput;
import fr.eternom.etereconomy.helper.config.MessageManager;
import fr.eternom.etereconomy.module.economy.EconomyManager;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public class CommandEco implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("give", "set", "take", "reset", "balance", "top");

    private final Plugin plugin;
    private final Economy economy;
    private final EconomyManager economyManager;
    private final MessageManager messages;

    public CommandEco(Plugin plugin, Economy economy, EconomyManager economyManager, MessageManager messages) {
        this.plugin = plugin;
        this.economy = economy;
        this.economyManager = economyManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length < 1) {
            sender.sendMessage(messages.get(sender, "eco-usage"));
            return true;
        }

        String subCommand = args[0].toLowerCase();

        if (!SUBCOMMANDS.contains(subCommand)) {
            sender.sendMessage(messages.get(sender, "eco-unknown-subcommand"));
            return true;
        }

        switch (subCommand) {
            case "give":
            case "set":
            case "take":
            case "reset":
                handleBalanceModification(sender, args);
                break;
            case "balance":
                handleBalanceCheck(sender, args);
                break;
            case "top":
                handleTopPlayers(sender);
                break;
        }

        return true;
    }

    private void handleBalanceModification(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get(sender, "eco-usage-amount", "sub", args[0]));
            return;
        }

        OfflinePlayer target = CommandInput.resolveKnownPlayer(sender, args[1], messages);
        if (target == null) {
            return;
        }

        double amount = CommandInput.parseAmount(sender, args[2], messages);
        if (amount < 0) {
            return;
        }

        // Cached account: fast path, stays on the main thread.
        if (economyManager.getLiveStore().hasAccount(target.getUniqueId())) {
            performBalanceModification(sender, args[0], target, amount);
            return;
        }

        // Cold account: falls through to the persistent store, so run off the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            EconomyResponse response = applyBalanceModification(args[0], target, amount);
            String successMessage = successMessageFor(sender, args[0], target, amount);
            Bukkit.getScheduler().runTask(plugin, () ->
                    sender.sendMessage(response.transactionSuccess() ? successMessage : errorMessage(sender, response, target)));
        });
    }

    private void handleBalanceCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get(sender, "eco-usage-balance"));
            return;
        }

        OfflinePlayer target = CommandInput.resolveKnownPlayer(sender, args[1], messages);
        if (target == null) {
            return;
        }

        if (economyManager.getLiveStore().hasAccount(target.getUniqueId())) {
            sender.sendMessage(messages.get(sender, "eco-balance",
                    "player", target.getName(),
                    "balance", economy.format(economy.getBalance(target))));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String formatted = economy.format(economy.getBalance(target));
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(messages.get(sender, "eco-balance",
                    "player", target.getName(),
                    "balance", formatted)));
        });
    }

    private void handleTopPlayers(CommandSender sender) {
        // getTopBalances() scans the whole persistent store, so keep it off the main thread.
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Map.Entry<UUID, Double>> top = economyManager.getTopBalances();
            Bukkit.getScheduler().runTask(plugin, () -> sendTopPlayers(sender, top));
        });
    }

    private void sendTopPlayers(CommandSender sender, List<Map.Entry<UUID, Double>> top) {
        if (top.isEmpty()) {
            sender.sendMessage(messages.get(sender, "eco-top-empty"));
            return;
        }

        sender.sendMessage(messages.get(sender, "eco-top-header"));
        int rank = 1;
        for (Map.Entry<UUID, Double> entry : top) {
            String name = Optional.ofNullable(Bukkit.getOfflinePlayer(entry.getKey()).getName()).orElse(entry.getKey().toString());
            sender.sendMessage(messages.get(sender, "eco-top-entry",
                    "rank", String.valueOf(rank++),
                    "player", name,
                    "balance", economy.format(entry.getValue())));
        }
    }

    private void performBalanceModification(CommandSender sender, @NotNull String operation, OfflinePlayer target, double amount) {
        EconomyResponse response = applyBalanceModification(operation, target, amount);
        String successMessage = successMessageFor(sender, operation, target, amount);
        sender.sendMessage(response.transactionSuccess() ? successMessage : errorMessage(sender, response, target));
    }

    private EconomyResponse applyBalanceModification(@NotNull String operation, OfflinePlayer target, double amount) {
        return switch (operation) {
            case "give" -> economy.depositPlayer(target, amount);
            case "set" -> economyManager.setBalance(target, amount);
            case "take" -> economy.withdrawPlayer(target, amount);
            case "reset" -> economyManager.setBalance(target, 0);
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    private String successMessageFor(CommandSender sender, @NotNull String operation, OfflinePlayer target, double amount) {
        return switch (operation) {
            case "give" -> messages.get(sender, "eco-give", "player", target.getName(), "amount", economy.format(amount));
            case "set" -> messages.get(sender, "eco-set", "player", target.getName(), "amount", economy.format(amount));
            case "take" -> messages.get(sender, "eco-take", "player", target.getName(), "amount", economy.format(amount));
            case "reset" -> messages.get(sender, "eco-reset", "player", target.getName());
            default -> throw new IllegalArgumentException("Unknown operation: " + operation);
        };
    }

    private String errorMessage(CommandSender sender, EconomyResponse response, OfflinePlayer target) {
        return switch (response.errorMessage) {
            case "Player does not have enough money" -> messages.get(sender, "eco-insufficient-funds", "player", target.getName());
            case "The resulting balance would be too large" -> messages.get(sender, "eco-amount-too-large");
            default -> messages.get(sender, "invalid-amount");
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && !"top".equals(args[0].toLowerCase())) {
            String prefix = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
