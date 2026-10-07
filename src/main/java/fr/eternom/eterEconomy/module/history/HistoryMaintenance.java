package fr.eternom.eterEconomy.module.history;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import fr.eternom.eterLib.helper.sql.Row;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.zip.GZIPOutputStream;

/**
 * Entretien du journal, lancé en tâche de fond toutes les heures :
 * - relevé de la masse monétaire du jour (chaque serveur le réécrit : même valeur, peu importe lequel) ;
 * - une fois par jour sur UN seul serveur (le premier qui réserve la tâche du jour dans etereconomy_jobs) : les
 *   transactions plus vieilles que `retention-days` sont ARCHIVÉES (plugins/EterEconomy/archives/, CSV compressé),
 *   puis seulement supprimées de la base. Si l'archive ne peut pas être écrite, rien n'est supprimé.
 * Les totaux par jour (etereconomy_daily) et les relevés (etereconomy_supply) sont minuscules : gardés pour toujours.
 */
public class HistoryMaintenance {

    private static final String JOBS = "jobs";
    private static final int BATCH = 5000;

    private final JavaPlugin plugin;
    private final Database database;
    private final EconomyStats stats;
    private final ZoneId zone;
    private final int retentionDays;

    public HistoryMaintenance(JavaPlugin plugin, Database database, EconomyStats stats, ZoneId zone, int retentionDays) {
        this.plugin = plugin;
        this.database = database;
        this.stats = stats;
        this.zone = zone;
        this.retentionDays = retentionDays;
        database.createTable(JOBS,
                Column.of("name", Column.Type.STRING).length(32).primaryKey(),
                Column.of("day", Column.Type.LONG).primaryKey());
    }

    /** Bloquant : tâche de fond. */
    public void run() {
        try {
            stats.snapshot();
            if (reserveToday()) {
                archiveAndPurge();
            }
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Entretien du journal de l'économie interrompu", e);
        }
    }

    /** La première réservation du jour gagne : un seul serveur archive. */
    private boolean reserveToday() {
        return database.execute("INSERT IGNORE INTO " + database.table(JOBS) + " (name, day) VALUES (?, ?)",
                "archive", LocalDate.now(zone).toEpochDay()) > 0;
    }

    private void archiveAndPurge() {
        long cutoff = LocalDate.now(zone).minusDays(retentionDays).atStartOfDay(zone).toInstant().toEpochMilli();
        List<Row> old = database.query("SELECT * FROM " + database.table(TransactionLog.TRANSACTIONS)
                + " WHERE created_at < ? ORDER BY id LIMIT " + BATCH, cutoff);
        while (!old.isEmpty()) {
            long lastId = old.getLast().getLong("id");
            Path file = archiveFile(old.getFirst().getLong("created_at"), lastId);
            try {
                write(file, old);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Archive du journal impossible (" + file + ") : rien n'est supprimé", e);
                return;
            }
            database.execute("DELETE FROM " + database.table(TransactionLog.TRANSACTIONS) + " WHERE id <= ? AND created_at < ?",
                    lastId, cutoff);
            plugin.getLogger().info(old.size() + " transaction(s) de plus de " + retentionDays + " jours archivée(s) dans " + file.getFileName());
            old = database.query("SELECT * FROM " + database.table(TransactionLog.TRANSACTIONS)
                    + " WHERE created_at < ? ORDER BY id LIMIT " + BATCH, cutoff);
        }
    }

    /** archives/transactions-2026-10-07-1234.csv.gz (date de la plus ancienne ligne, dernier id). */
    private Path archiveFile(long firstCreatedAt, long lastId) {
        LocalDate date = Instant.ofEpochMilli(firstCreatedAt).atZone(zone).toLocalDate();
        return plugin.getDataFolder().toPath().resolve("archives").resolve("transactions-" + date + "-" + lastId + ".csv.gz");
    }

    private static void write(Path file, List<Row> rows) throws IOException {
        Files.createDirectories(file.getParent());
        try (Writer out = new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(file)), StandardCharsets.UTF_8)) {
            out.write("id,date,uuid,bank,amount,balance,source,server\n");
            for (Row row : rows) {
                Map<String, Object> values = row.asMap();
                out.write(row.getLong("id") + "," + Instant.ofEpochMilli(row.getLong("created_at")) + ","
                        + csv(values.get("uuid")) + "," + csv(values.get("bank")) + "," + row.getDouble("amount") + ","
                        + row.getDouble("balance") + "," + csv(values.get("source")) + "," + csv(values.get("server")) + "\n");
            }
        }
    }

    private static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        return text.contains(",") || text.contains("\"") ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }
}
