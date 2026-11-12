package fr.eternom.eterEconomy.module.history;

import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Journal de l'économie : chaque mouvement d'argent (joueur ou banque) est une ligne de etereconomy_transactions, et
 * s'ajoute aux totaux du jour par source (etereconomy_daily) : combien chaque plugin CRÉE d'argent (dépôts : récompenses,
 * ventes au serveur...) et combien il en DÉTRUIT (retraits : achats au serveur, taxes...). Un /pay apparaît des deux
 * côtés (retrait puis dépôt) : il ne crée rien.
 *
 * La source : donnée par les plugins Eter (EconomyApi, « EterMarket · boutique ») ; pour un plugin tiers passé par
 * Vault, le plugin trouvé dans la pile d'appels. Appels bloquants (base), déjà hors du thread principal comme tout appel à Vault ; une erreur d'écriture du
 * journal ne bloque jamais le mouvement lui-même.
 */
public class TransactionLog {

    static final String TRANSACTIONS = "transactions";
    static final String DAILY = "daily";
    /** Source quand aucun plugin n'est trouvé (console, serveur). */
    static final String SERVER_SOURCE = "Serveur";

    private final JavaPlugin plugin;
    private final Database database;
    private final String serverName;
    private final ZoneId zone;
    /** Gains de l'heure en cours par joueur (sans les /pay, qui ne créent rien) : alerte au-delà d'un seuil. */
    private final Map<UUID, double[]> hourlyGains = new ConcurrentHashMap<>();
    private double gainAlert;
    private Consumer<GainAlert> onGainAlert = alert -> { };

    /** Un joueur a gagné plus que le seuil en moins d'une heure. */
    public record GainAlert(UUID player, double amount, String lastSource) {
    }

    /** Une ligne du journal d'un joueur, pour /transactions. */
    public record Entry(double amount, double balance, String source, String server, long createdAt) {
    }

    /** Classe -> plugin qui l'a chargée : la recherche n'est faite qu'une fois par classe. */
    private final Map<Class<?>, Optional<String>> owners = new ConcurrentHashMap<>();

    public TransactionLog(JavaPlugin plugin, Database database, String serverName, ZoneId zone) {
        this.plugin = plugin;
        this.database = database;
        this.serverName = serverName;
        this.zone = zone;
        database.createTable(TRANSACTIONS,
                Column.of("id", Column.Type.LONG).autoIncrement(),
                Column.of("uuid", Column.Type.UUID),
                Column.of("bank", Column.Type.STRING).length(64),
                Column.of("amount", Column.Type.DOUBLE).notNull(),
                Column.of("balance", Column.Type.DOUBLE).notNull(),
                Column.of("source", Column.Type.STRING).length(64).notNull(),
                Column.of("server", Column.Type.STRING).length(64).notNull(),
                Column.of("created_at", Column.Type.LONG).notNull());
        database.createTable(DAILY,
                Column.of("day", Column.Type.LONG).primaryKey(),
                Column.of("source", Column.Type.STRING).length(64).primaryKey(),
                Column.of("created", Column.Type.DOUBLE).notNull(),
                Column.of("destroyed", Column.Type.DOUBLE).notNull(),
                Column.of("operations", Column.Type.INT).notNull());
    }

    /** Seuil d'alerte (Heloks gagnés en une heure, 0 : jamais) et ce qu'on fait de l'alerte (appelé hors du thread principal). */
    public void watchGains(double threshold, Consumer<GainAlert> onAlert) {
        this.gainAlert = threshold;
        this.onGainAlert = onAlert;
    }

    /** Bloquant (base) : les derniers mouvements d'un joueur, du plus récent au plus ancien. */
    public List<Entry> recent(UUID player, int limit) {
        return database.query("SELECT amount, balance, source, server, created_at FROM " + database.table(TRANSACTIONS)
                        + " WHERE uuid = ? ORDER BY id DESC LIMIT " + limit, player).stream()
                .map(row -> new Entry(row.getDouble("amount"), row.getDouble("balance"), row.getString("source"),
                        row.getString("server"), row.getLong("created_at")))
                .toList();
    }

    /** Mouvement d'un joueur par Vault (source devinée). amount : positif pour un dépôt, négatif pour un retrait. */
    public void player(UUID player, double amount, double balanceAfter) {
        record(player, null, amount, balanceAfter, callerPlugin());
    }

    /** Mouvement d'un joueur par EconomyApi : la source est donnée par le plugin (« EterMarket · boutique »). */
    public void player(UUID player, double amount, double balanceAfter, String source) {
        record(player, null, amount, balanceAfter, source.length() > 64 ? source.substring(0, 64) : source);
    }

    /** Mouvement d'une banque de Vault. */
    public void bank(String bank, double amount, double balanceAfter) {
        record(null, bank, amount, balanceAfter, callerPlugin());
    }

    private void record(UUID player, String bank, double amount, double balanceAfter, String source) {
        if (amount == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (player != null && amount > 0 && gainAlert > 0 && !source.contains("/pay")) {
            watch(player, amount, source, now);
        }
        try {
            Map<String, Object> values = new HashMap<>(); // HashMap : uuid ou bank vaut null
            values.put("uuid", player);
            values.put("bank", bank);
            values.put("amount", amount);
            values.put("balance", balanceAfter);
            values.put("source", source);
            values.put("server", serverName);
            values.put("created_at", now);
            database.insert(TRANSACTIONS, values);

            double created = Math.max(0, amount);
            double destroyed = Math.max(0, -amount);
            database.execute("INSERT INTO " + database.table(DAILY) + " (day, source, created, destroyed, operations)"
                            + " VALUES (?, ?, ?, ?, 1) ON DUPLICATE KEY UPDATE created = created + ?,"
                            + " destroyed = destroyed + ?, operations = operations + 1",
                    LocalDate.now(zone).toEpochDay(), source, created, destroyed, created, destroyed);
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.WARNING, "Mouvement non journalisé (" + source + ", " + amount + ")", e);
        }
    }

    /** Cumul sur une fenêtre d'une heure ; une seule alerte par fenêtre. */
    private void watch(UUID player, double amount, String source, long now) {
        double[] window = hourlyGains.compute(player, (uuid, old) -> {
            if (old == null || now - old[0] > 3_600_000L) {
                return new double[]{now, amount, 0};
            }
            old[1] += amount;
            return old;
        });
        if (window[2] == 0 && window[1] >= gainAlert) {
            window[2] = 1;
            onGainAlert.accept(new GainAlert(player, window[1], source));
        }
    }

    /** Premier plugin (autre qu'EterEconomy et Vault) dans la pile d'appels ; « Serveur » s'il n'y en a pas. */
    private String callerPlugin() {
        return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).walk(frames -> frames
                .map(frame -> owners.computeIfAbsent(frame.getDeclaringClass(), this::ownerOf))
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(SERVER_SOURCE));
    }

    private Optional<String> ownerOf(Class<?> type) {
        try {
            Plugin owner = JavaPlugin.getProvidingPlugin(type);
            return owner == plugin || owner.getName().equals("Vault") ? Optional.empty() : Optional.of(owner.getName());
        } catch (IllegalArgumentException | IllegalStateException notAPluginClass) {
            return Optional.empty(); // classe du serveur ou de Java
        }
    }
}
