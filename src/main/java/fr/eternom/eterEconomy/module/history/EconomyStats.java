package fr.eternom.eterEconomy.module.history;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import fr.eternom.eterLib.helper.sql.Row;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lectures pour le menu /ecostats, et relevé quotidien de la masse monétaire (etereconomy_supply : tout l'argent des
 * joueurs et des banques, un relevé par jour, réécrit à chaque passage pour garder la valeur de fin de journée).
 * Appels bloquants : hors du thread principal.
 */
public class EconomyStats {

    static final String SUPPLY = "supply";

    /** Totaux d'une source sur une période. net = created - destroyed (positif : la source crée de l'argent). */
    public record SourceTotal(String source, double created, double destroyed, long operations) {

        public double net() {
            return created - destroyed;
        }
    }

    /** Masse monétaire d'un jour (jour julien). */
    public record Supply(long day, double total, int accounts) {
    }

    public record Rich(UUID uuid, double balance) {
    }

    private final Database database;
    private final ZoneId zone;
    private final boolean banks;

    public EconomyStats(Database database, ZoneId zone, boolean banks) {
        this.database = database;
        this.zone = zone;
        this.banks = banks;
        database.createTable(SUPPLY,
                Column.of("day", Column.Type.LONG).primaryKey(),
                Column.of("total", Column.Type.DOUBLE).notNull(),
                Column.of("accounts", Column.Type.INT).notNull());
    }

    public long today() {
        return LocalDate.now(zone).toEpochDay();
    }

    /** Masse monétaire actuelle, calculée en direct. */
    public Supply currentSupply() {
        Row players = database.query("SELECT COALESCE(SUM(balance), 0) AS total, COUNT(*) AS accounts FROM "
                + database.table("balances")).getFirst();
        double total = players.getDouble("total");
        if (banks) {
            total += database.query("SELECT COALESCE(SUM(balance), 0) AS total FROM " + database.table("banks"))
                    .getFirst().getDouble("total");
        }
        return new Supply(today(), total, players.getInt("accounts"));
    }

    /** Écrit (ou réécrit) le relevé du jour. */
    public void snapshot() {
        Supply supply = currentSupply();
        database.set(SUPPLY, Map.of("day", supply.day(), "total", supply.total(), "accounts", supply.accounts()), "day");
    }

    /** Relevés des `days` derniers jours, du plus récent au plus ancien. */
    public List<Supply> supplyHistory(int days) {
        return database.query("SELECT day, total, accounts FROM " + database.table(SUPPLY) + " WHERE day > ? ORDER BY day DESC",
                        today() - days).stream()
                .map(row -> new Supply(row.getLong("day"), row.getDouble("total"), row.getInt("accounts")))
                .toList();
    }

    /** Totaux par source sur les `days` derniers jours (aujourd'hui compris), ceux qui pèsent le plus en premier. */
    public List<SourceTotal> bySource(int days) {
        return database.query("SELECT source, SUM(created) AS created, SUM(destroyed) AS destroyed, SUM(operations) AS operations"
                        + " FROM " + database.table(TransactionLog.DAILY) + " WHERE day > ? GROUP BY source", today() - days).stream()
                .map(row -> new SourceTotal(row.getString("source"), row.getDouble("created"), row.getDouble("destroyed"),
                        row.getLong("operations")))
                .sorted(Comparator.comparingDouble((SourceTotal total) -> Math.abs(total.net())).reversed())
                .toList();
    }

    /** Les joueurs les plus riches. */
    public List<Rich> richest(int limit) {
        return database.query("SELECT uuid, balance FROM " + database.table("balances") + " ORDER BY balance DESC LIMIT ?", limit)
                .stream()
                .map(row -> new Rich(row.getUUID("uuid"), row.getDouble("balance")))
                .toList();
    }
}
