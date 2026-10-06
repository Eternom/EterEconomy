package fr.eternom.eterEconomy.module.account;

import fr.eternom.eterLib.helper.cache.RedisCache;
import fr.eternom.eterLib.helper.sql.Column;
import fr.eternom.eterLib.helper.sql.Database;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.UUID;

/**
 * Soldes des joueurs, table etereconomy_balances : la base est la seule source de vérité.
 *
 * Chaque mouvement est UNE requête SQL relative ("balance + 50", "balance - 50 si balance >= 50") : deux serveurs ne
 * peuvent pas s'écraser et un retrait ne passe jamais en négatif, avec ou sans Redis.
 * Redis (facultatif) garde une copie des soldes lus, effacée à chaque mouvement : la prochaine lecture repart de la base.
 * Appels bloquants : hors du thread principal (les plugins appellent Vault depuis une tâche de fond).
 */
public class AccountRepository {

    private static final String TABLE = "balances";
    private static final Duration CACHE_TTL = Duration.ofMinutes(1);

    private final Database database;
    private final RedisCache redis; // null sans Redis
    private final double startingBalance;
    private final int fractionalDigits;

    public AccountRepository(Database database, RedisCache redis, double startingBalance, int fractionalDigits) {
        this.database = database;
        this.redis = redis;
        this.startingBalance = startingBalance;
        this.fractionalDigits = fractionalDigits;
        database.createTable(TABLE,
                Column.of("uuid", Column.Type.UUID).primaryKey(),
                Column.of("balance", Column.Type.DOUBLE).notNull());
    }

    public boolean exists(UUID player) {
        return find(player).isPresent();
    }

    /** Solde ; celui de départ si le compte n'existe pas encore. */
    public double balance(UUID player) {
        return find(player).orElse(startingBalance);
    }

    /** Crée le compte avec le solde de départ s'il n'existe pas. @return true s'il vient d'être créé */
    public boolean create(UUID player) {
        int created = database.execute("INSERT IGNORE INTO " + table() + " (uuid, balance) VALUES (?, ?)", player, startingBalance);
        invalidate(player);
        return created > 0;
    }

    /** @return le nouveau solde */
    public double deposit(UUID player, double amount) {
        // Compte absent : créé avec solde de départ + montant ; sinon ajout relatif, atomique
        database.execute("INSERT INTO " + table() + " (uuid, balance) VALUES (?, ?) ON DUPLICATE KEY UPDATE balance = "
                + round("balance + ?"), player, startingBalance + amount, amount);
        return reload(player);
    }

    /** @return le nouveau solde, vide si le solde ne suffit pas (rien n'est retiré) */
    public OptionalDouble withdraw(UUID player, double amount) {
        create(player);
        int updated = database.execute("UPDATE " + table() + " SET balance = " + round("balance - ?")
                + " WHERE uuid = ? AND balance >= ?", amount, player, amount);
        double balance = reload(player);
        return updated > 0 ? OptionalDouble.of(balance) : OptionalDouble.empty();
    }

    public void set(UUID player, double amount) {
        database.set(TABLE, Map.of("uuid", player, "balance", amount), "uuid");
        invalidate(player);
    }

    private Optional<Double> find(UUID player) {
        if (redis != null) {
            Optional<String> cached = redis.get(key(player));
            if (cached.isPresent()) {
                return Optional.of(Double.parseDouble(cached.get()));
            }
        }
        Optional<Double> stored = database.getFirst(TABLE, Map.of("uuid", player)).map(row -> row.getDouble("balance"));
        if (redis != null) {
            stored.ifPresent(balance -> redis.set(key(player), String.valueOf(balance), CACHE_TTL));
        }
        return stored;
    }

    /** Après un mouvement : la copie Redis est effacée, puis le vrai solde relu en base. */
    private double reload(UUID player) {
        invalidate(player);
        return balance(player);
    }

    private void invalidate(UUID player) {
        if (redis != null) {
            redis.delete(key(player));
        }
    }

    /** Arrondi fait par la base, aux décimales de la monnaie (évite les 0,30000000004). */
    private String round(String expression) {
        return "ROUND(" + expression + ", " + fractionalDigits + ")";
    }

    private String table() {
        return database.table(TABLE);
    }

    private static String key(UUID player) {
        return "economy:balance:" + player;
    }
}
