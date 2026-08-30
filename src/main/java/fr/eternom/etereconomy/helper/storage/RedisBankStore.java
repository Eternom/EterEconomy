package fr.eternom.etereconomy.helper.storage;

import fr.eternom.etereconomy.helper.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Live bank store shared network-wide through Redis: a "banks" set tracks bank names, each
 * bank's owner/balance/members live under their own keys.
 */
public class RedisBankStore implements BankStore {

    private static final Logger logger = LoggerFactory.getLogger(RedisBankStore.class);
    private static final String BANKS_SET_KEY = "banks";

    private final JedisPool jedisPool;

    public RedisBankStore(ConfigManager config) {
        this.jedisPool = new JedisPool(
                new JedisPoolConfig(),
                config.getRedisHost(),
                config.getRedisPort(),
                config.getRedisTimeout(),
                config.getRedisPassword()
        );
    }

    private String balanceKey(String name) {
        return "bank:" + name + ":balance";
    }

    private String ownerKey(String name) {
        return "bank:" + name + ":owner";
    }

    private String membersKey(String name) {
        return "bank:" + name + ":members";
    }

    @Override
    public boolean exists(String name) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.sismember(BANKS_SET_KEY, name);
        } catch (Exception e) {
            logger.error("Error checking bank {}.", name, e);
            return false;
        }
    }

    @Override
    public boolean create(String name, UUID owner, double startingBalance) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd(BANKS_SET_KEY, name);
            jedis.set(ownerKey(name), owner.toString());
            jedis.set(balanceKey(name), String.valueOf(startingBalance));
            return true;
        } catch (Exception e) {
            logger.error("Error creating bank {}.", name, e);
            return false;
        }
    }

    @Override
    public boolean delete(String name) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.srem(BANKS_SET_KEY, name);
            jedis.del(ownerKey(name), balanceKey(name), membersKey(name));
            return true;
        } catch (Exception e) {
            logger.error("Error deleting bank {}.", name, e);
            return false;
        }
    }

    @Override
    public double getBalance(String name) {
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(balanceKey(name));
            return value != null ? Double.parseDouble(value) : 0.0;
        } catch (Exception e) {
            logger.error("Error reading balance of bank {}.", name, e);
            return 0.0;
        }
    }

    @Override
    public boolean setBalance(String name, double balance) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(balanceKey(name), String.valueOf(balance));
            return true;
        } catch (Exception e) {
            logger.error("Error saving balance of bank {}.", name, e);
            return false;
        }
    }

    @Override
    public double adjustBalance(String name, double delta) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.incrByFloat(balanceKey(name), delta);
        } catch (Exception e) {
            logger.error("Error adjusting balance of bank {}.", name, e);
            return getBalance(name);
        }
    }

    @Override
    public UUID getOwner(String name) {
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(ownerKey(name));
            return value != null ? UUID.fromString(value) : null;
        } catch (Exception e) {
            logger.error("Error reading owner of bank {}.", name, e);
            return null;
        }
    }

    @Override
    public Set<UUID> getMembers(String name) {
        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.smembers(membersKey(name)).stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            logger.error("Error reading members of bank {}.", name, e);
            return Set.of();
        }
    }

    @Override
    public boolean addMember(String name, UUID member) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.sadd(membersKey(name), member.toString());
            return true;
        } catch (Exception e) {
            logger.error("Error adding member to bank {}.", name, e);
            return false;
        }
    }

    @Override
    public boolean removeMember(String name, UUID member) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.srem(membersKey(name), member.toString());
            return true;
        } catch (Exception e) {
            logger.error("Error removing member from bank {}.", name, e);
            return false;
        }
    }

    @Override
    public List<String> getBankNames() {
        try (Jedis jedis = jedisPool.getResource()) {
            return new ArrayList<>(jedis.smembers(BANKS_SET_KEY));
        } catch (Exception e) {
            logger.error("Error listing banks.", e);
            return List.of();
        }
    }

    @Override
    public void close() {
        jedisPool.close();
    }
}
