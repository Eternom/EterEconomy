package fr.eternom.etereconomy.module.bank.command;

import fr.eternom.etereconomy.helper.command.CommandInput;
import fr.eternom.etereconomy.helper.config.MessageManager;
import fr.eternom.etereconomy.module.bank.BankManager;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CommandBank implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("create", "delete", "balance", "deposit", "withdraw", "invite", "kick", "list");
    private static final List<String> BANK_NAME_ARG_SUBCOMMANDS = List.of("delete", "balance", "deposit", "withdraw", "invite", "kick");
    private static final List<String> MEMBER_SUBCOMMANDS = List.of("invite", "kick");
    private static final String ADMIN_PERMISSION = "etereconomy.command.bank.admin";

    private final BankManager bankManager;
    private final MessageManager messages;

    public CommandBank(BankManager bankManager, MessageManager messages) {
        this.bankManager = bankManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!bankManager.isEnabled()) {
            sender.sendMessage(messages.get(sender, "bank-disabled"));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(messages.get(sender, "bank-usage"));
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create":
                handleCreate(sender, args);
                break;
            case "delete":
                handleDelete(sender, args);
                break;
            case "balance":
                handleBalance(sender, args);
                break;
            case "deposit":
                handleDeposit(sender, args);
                break;
            case "withdraw":
                handleWithdraw(sender, args);
                break;
            case "invite":
                handleMember(sender, args, true);
                break;
            case "kick":
                handleMember(sender, args, false);
                break;
            case "list":
                handleList(sender);
                break;
            default:
                sender.sendMessage(messages.get(sender, "bank-unknown-subcommand"));
        }

        return true;
    }

    private void handleCreate(CommandSender sender, String[] args) {
        Player player = requirePlayer(sender);
        if (player == null) {
            return;
        }
        if (args.length < 2) {
            sender.sendMessage(messages.get(sender, "bank-usage-create"));
            return;
        }

        report(sender, bankManager.createBank(args[1], player.getUniqueId()), messages.get(sender, "bank-created", "bank", args[1]));
    }

    private void handleDelete(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get(sender, "bank-usage-delete"));
            return;
        }
        if (!requireOwnerOrAdmin(sender, args[1])) {
            return;
        }

        report(sender, bankManager.deleteBank(args[1]), messages.get(sender, "bank-deleted", "bank", args[1]));
    }

    private void handleBalance(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(messages.get(sender, "bank-usage-balance"));
            return;
        }

        EconomyResponse response = bankManager.bankBalance(args[1]);
        if (!response.transactionSuccess()) {
            sender.sendMessage(errorMessage(sender, response));
            return;
        }
        sender.sendMessage(messages.get(sender, "bank-balance", "bank", args[1], "balance", bankManager.format(response.balance)));
    }

    private void handleDeposit(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get(sender, "bank-usage-deposit"));
            return;
        }
        if (!requireMemberOrAdmin(sender, args[1])) {
            return;
        }
        double amount = CommandInput.parseAmount(sender, args[2], messages);
        if (amount < 0) {
            return;
        }

        EconomyResponse response = bankManager.bankDeposit(args[1], amount);
        report(sender, response, messages.get(sender, "bank-deposit",
                "bank", args[1], "amount", bankManager.format(amount), "balance", bankManager.format(response.balance)));
    }

    private void handleWithdraw(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(messages.get(sender, "bank-usage-withdraw"));
            return;
        }
        if (!requireMemberOrAdmin(sender, args[1])) {
            return;
        }
        double amount = CommandInput.parseAmount(sender, args[2], messages);
        if (amount < 0) {
            return;
        }

        EconomyResponse response = bankManager.bankWithdraw(args[1], amount);
        report(sender, response, messages.get(sender, "bank-withdraw",
                "bank", args[1], "amount", bankManager.format(amount), "balance", bankManager.format(response.balance)));
    }

    private void handleMember(CommandSender sender, String[] args, boolean add) {
        if (args.length < 3) {
            sender.sendMessage(messages.get(sender, add ? "bank-usage-invite" : "bank-usage-kick"));
            return;
        }
        if (!requireOwnerOrAdmin(sender, args[1])) {
            return;
        }

        OfflinePlayer target = CommandInput.resolveKnownPlayer(sender, args[2], messages);
        if (target == null) {
            return;
        }

        boolean changed = add
                ? bankManager.addMember(args[1], target.getUniqueId())
                : bankManager.removeMember(args[1], target.getUniqueId());

        if (!changed) {
            sender.sendMessage(messages.get(sender, "bank-not-found"));
            return;
        }
        sender.sendMessage(messages.get(sender, add ? "bank-member-added" : "bank-member-removed", "player", target.getName(), "bank", args[1]));
    }

    private void handleList(CommandSender sender) {
        List<String> banks = bankManager.getBanks();
        if (banks.isEmpty()) {
            sender.sendMessage(messages.get(sender, "bank-list-empty"));
            return;
        }
        sender.sendMessage(messages.get(sender, "bank-list-header", "banks", String.join(", ", banks)));
    }

    private void report(CommandSender sender, EconomyResponse response, String successMessage) {
        sender.sendMessage(response.transactionSuccess() ? successMessage : errorMessage(sender, response));
    }

    // Maps a failed EconomyResponse's error text back to a configured message key.
    private String errorMessage(CommandSender sender, EconomyResponse response) {
        String key = switch (response.errorMessage) {
            case "A bank with that name already exists" -> "bank-already-exists";
            case "Bank names may only contain letters, digits, '_' and '-', up to 32 characters" -> "bank-invalid-name";
            case "That player has reached the maximum number of banks they can own" -> "bank-limit-reached";
            case "No such bank" -> "bank-not-found";
            case "The bank does not have enough money" -> "bank-insufficient-funds";
            case "That player does not own this bank" -> "bank-not-owner";
            case "That player is not a member of this bank" -> "bank-not-member";
            case "The bank feature is disabled on this server" -> "bank-disabled";
            case "Invalid amount" -> "invalid-amount";
            case "The resulting balance would be too large" -> "bank-amount-too-large";
            default -> "bank-error";
        };
        return messages.get(sender, key);
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(messages.get(sender, "bank-only-player"));
            return null;
        }
        return player;
    }

    private boolean requireOwnerOrAdmin(CommandSender sender, String bankName) {
        if (sender.isOp() || sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return false;
        }
        return checkResponse(sender, bankManager.isBankOwner(bankName, player.getUniqueId()));
    }

    private boolean requireMemberOrAdmin(CommandSender sender, String bankName) {
        if (sender.isOp() || sender.hasPermission(ADMIN_PERMISSION)) {
            return true;
        }
        Player player = requirePlayer(sender);
        if (player == null) {
            return false;
        }
        return checkResponse(sender, bankManager.isBankMember(bankName, player.getUniqueId()));
    }

    private boolean checkResponse(CommandSender sender, EconomyResponse response) {
        if (!response.transactionSuccess()) {
            sender.sendMessage(errorMessage(sender, response));
            return false;
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }

        String subCommand = args[0].toLowerCase(Locale.ROOT);

        if (args.length == 2 && BANK_NAME_ARG_SUBCOMMANDS.contains(subCommand)) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return bankManager.getBanks().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && MEMBER_SUBCOMMANDS.contains(subCommand)) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }
}
