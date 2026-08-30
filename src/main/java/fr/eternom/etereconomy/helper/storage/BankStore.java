package fr.eternom.etereconomy.helper.storage;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Where shared bank accounts (Vault's bank extension of the Economy API) are read from and
 * written to. A bank has a name (its key), one owner, zero or more members, and a balance.
 */
public interface BankStore {

    boolean exists(String name);

    /** @return whether the write actually succeeded (failures are logged internally). */
    boolean create(String name, UUID owner, double startingBalance);

    boolean delete(String name);

    double getBalance(String name);

    boolean setBalance(String name, double balance);

    /** Atomically adds {@code delta} to an existing bank's balance and returns the result. */
    double adjustBalance(String name, double delta);

    UUID getOwner(String name);

    Set<UUID> getMembers(String name);

    boolean addMember(String name, UUID member);

    boolean removeMember(String name, UUID member);

    List<String> getBankNames();

    default void close() {
    }
}
