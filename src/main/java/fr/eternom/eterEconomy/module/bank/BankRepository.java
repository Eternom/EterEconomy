package fr.eternom.eterEconomy.module.bank;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import fr.eternom.eterLib.helper.sql.Row;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Comptes partagés de Vault (createBank, bankDeposit...), table etereconomy_banks. Comme pour les joueurs, chaque
 * mouvement est une requête SQL relative et un retrait ne passe jamais en négatif. Vault ne gère pas de membres :
 * seul le propriétaire est membre. Appels bloquants : hors du thread principal.
 */
public class BankRepository {

    private static final String TABLE = "banks";

    private final Database database;
    private final double startingBalance;
    private final int fractionalDigits;

    public BankRepository(Database database, double startingBalance, int fractionalDigits) {
        this.database = database;
        this.startingBalance = startingBalance;
        this.fractionalDigits = fractionalDigits;
        database.createTable(TABLE,
                Column.of("name", Column.Type.STRING).length(64).primaryKey(),
                Column.of("owner", Column.Type.UUID).notNull(),
                Column.of("balance", Column.Type.DOUBLE).notNull());
    }

    /** @return false si une banque porte déjà ce nom */
    public boolean create(String name, UUID owner) {
        return database.execute("INSERT IGNORE INTO " + table() + " (name, owner, balance) VALUES (?, ?, ?)",
                name, owner, startingBalance) > 0;
    }

    public boolean delete(String name) {
        return database.delete(TABLE, Map.of("name", name)) > 0;
    }

    public OptionalDouble balance(String name) {
        return find(name).map(row -> OptionalDouble.of(row.getDouble("balance"))).orElse(OptionalDouble.empty());
    }

    public Optional<UUID> owner(String name) {
        return find(name).map(row -> row.getUUID("owner"));
    }

    /** @return le nouveau solde, vide si la banque n'existe pas */
    public OptionalDouble deposit(String name, double amount) {
        int updated = database.execute("UPDATE " + table() + " SET balance = " + round("balance + ?") + " WHERE name = ?",
                amount, name);
        return updated > 0 ? balance(name) : OptionalDouble.empty();
    }

    /** @return le nouveau solde, vide si la banque n'existe pas ou que son solde ne suffit pas */
    public OptionalDouble withdraw(String name, double amount) {
        int updated = database.execute("UPDATE " + table() + " SET balance = " + round("balance - ?")
                + " WHERE name = ? AND balance >= ?", amount, name, amount);
        return updated > 0 ? balance(name) : OptionalDouble.empty();
    }

    public List<String> names() {
        return database.get(TABLE, Map.of()).stream().map(row -> row.getString("name")).toList();
    }

    private Optional<Row> find(String name) {
        return database.getFirst(TABLE, Map.of("name", name));
    }

    private String round(String expression) {
        return "ROUND(" + expression + ", " + fractionalDigits + ")";
    }

    private String table() {
        return database.table(TABLE);
    }
}
