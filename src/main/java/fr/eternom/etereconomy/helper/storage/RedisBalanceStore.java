package fr.eternom.etereconomy.helper.storage;

import fr.eternom.etereconomy.helper.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Live balance store backed by a single shared Redis hash ("balances"), keyed by player UUID.
 * Used when storage.type = REDIS to keep balances in sync network-wide. Not itself durable -
 * a separate {@link BalanceStore} backs it up periodically.
 */
public class RedisBalanceStore implements BalanceStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisBalanceStore.class);
    private static final String BALANCES_KEY = "balances";

    private final JedisPool jedisPool;

    public RedisBalanceStore(ConfigManager config) {
        this.jedisPool = new JedisPool(
                new JedisPoolConfig(),
                config.getRedisHost(),
                config.getRedisPort(),
                config.getRedisTimeout(),
                config.getRedisPassword()
        );
    }

    @Override
    public boolean hasAccount(UUID uuid) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.hexists(BALANCES_KEY, uuid.toString());
        } catch (Exception e) {
            logger.error("Error checking Redis account {}.", uuid, e);
            return false;
        }
    }

    @Override
    public double getBalance(UUID uuid) {
        try (Jedis jedis = jedisPool.getResource()) {
            String balance = jedis.hget(BALANCES_KEY, uuid.toString());
            return balance != null ? Double.parseDouble(balance) : 0.0;
        } catch (Exception e) {
            logger.error("Error reading Redis balance for {}.", uuid, e);
            return 0.0;
        }
    }

    @Override
    public boolean setBalance(UUID uuid, double balance) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.hset(BALANCES_KEY, uuid.toString(), String.valueOf(balance));
            return true;
        } catch (Exception e) {
            logger.error("Error saving Redis balance for {}.", uuid, e);
            return false;
        }
    }

    @Override
    public double adjustBalance(UUID uuid, double delta, double baselineIfMissing) {
        try (Jedis jedis = jedisPool.getResource()) {
            // HSETNX only sets the field if missing, so concurrent seeding races resolve safely.
            if (baselineIfMissing != 0.0) {
                jedis.hsetnx(BALANCES_KEY, uuid.toString(), String.valueOf(baselineIfMissing));
            }
            return jedis.hincrByFloat(BALANCES_KEY, uuid.toString(), delta);
        } catch (Exception e) {
            logger.error("Error adjusting Redis balance for {}.", uuid, e);
            return getBalance(uuid);
        }
    }

    @Override
    public Map<UUID, Double> getAllBalances() {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, String> raw = jedis.hgetAll(BALANCES_KEY);
            Map<UUID, Double> result = new HashMap<>();
            for (Map.Entry<String, String> entry : raw.entrySet()) {
                try {
                    result.put(UUID.fromString(entry.getKey()), Double.parseDouble(entry.getValue()));
                } catch (IllegalArgumentException ignored) {
                    // Legacy entry keyed by player name from a previous version, skip it.
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("Error loading Redis balances.", e);
            return Map.of();
        }
    }

    @Override
    public void close() {
        jedisPool.close();
    }
}
