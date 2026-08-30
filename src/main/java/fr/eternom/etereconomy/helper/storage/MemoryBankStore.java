package fr.eternom.etereconomy.helper.storage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live bank store backed by a plain in-memory map. Used when storage.type = LOCAL; banks are
 * lost on restart unless a durable {@link BankStore} (YAML/MySQL/...) keeps them in sync (see
 * {@code BankManager}'s startup load and the backup module's periodic flush).
 */
public class MemoryBankStore implements BankStore {

    private static final class Bank {
        private volatile UUID owner;
        private volatile double balance;
        private final Set<UUID> members = ConcurrentHashMap.newKeySet();

        private Bank(UUID owner, double balance) {
            this.owner = owner;
            this.balance = balance;
        }
    }

    private final Map<String, Bank> banks = new ConcurrentHashMap<>();

    @Override
    public boolean exists(String name) {
        return banks.containsKey(name);
    }

    @Override
    public boolean create(String name, UUID owner, double startingBalance) {
        banks.put(name, new Bank(owner, startingBalance));
        return true;
    }

    @Override
    public boolean delete(String name) {
        return banks.remove(name) != null;
    }

    @Override
    public double getBalance(String name) {
        Bank bank = banks.get(name);
        return bank != null ? bank.balance : 0.0;
    }

    @Override
    public boolean setBalance(String name, double balance) {
        Bank bank = banks.get(name);
        if (bank == null) {
            return false;
        }
        synchronized (bank) {
            bank.balance = balance;
        }
        return true;
    }

    @Override
    public double adjustBalance(String name, double delta) {
        Bank bank = banks.get(name);
        if (bank == null) {
            return 0.0;
        }
        synchronized (bank) {
            bank.balance += delta;
            return bank.balance;
        }
    }

    @Override
    public UUID getOwner(String name) {
        Bank bank = banks.get(name);
        return bank != null ? bank.owner : null;
    }

    @Override
    public Set<UUID> getMembers(String name) {
        Bank bank = banks.get(name);
        return bank != null ? new HashSet<>(bank.members) : Set.of();
    }

    @Override
    public boolean addMember(String name, UUID member) {
        Bank bank = banks.get(name);
        if (bank == null) {
            return false;
        }
        bank.members.add(member);
        return true;
    }

    @Override
    public boolean removeMember(String name, UUID member) {
        Bank bank = banks.get(name);
        if (bank == null) {
            return false;
        }
        bank.members.remove(member);
        return true;
    }

    @Override
    public List<String> getBankNames() {
        return new ArrayList<>(banks.keySet());
    }
}
