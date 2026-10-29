package space.magnitoshka.settlements.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Settlement {
    public UUID id = UUID.randomUUID();
    public UUID owner;
    public String name;
    public String dimension;
    public int x, y, z;
    public int level = 1;
    public int bedCapacity = 3;
    public long treasury;
    public long lastDay;
    /** Nutrition remaining from already consumed food items. Five points feed one resident/day. */
    public int foodCredit;
    public Map<String, Integer> resources = new HashMap<>();
    public List<Resident> residents = new ArrayList<>();

    public Settlement() {}

    public int stored() {
        long total = resources.values().stream().mapToLong(Integer::longValue).sum();
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, total));
    }

    public int deposit(String key, int amount, int capacity) {
        if (key == null || key.isBlank() || amount <= 0 || capacity <= stored()) return 0;
        int accepted = Math.min(amount, capacity - stored());
        resources.merge(key, accepted, Integer::sum);
        return accepted;
    }

    public int withdraw(String key, int amount) {
        if (key == null || amount <= 0) return 0;
        int removed = Math.min(amount, resources.getOrDefault(key, 0));
        if (removed <= 0) return 0;
        int remaining = resources.get(key) - removed;
        if (remaining == 0) resources.remove(key);
        else resources.put(key, remaining);
        return removed;
    }

    public long hireCost(Rules rules) {
        return (long) rules.hireBase() + (long) rules.hireExtra() * Math.max(0, residents.size() - 10);
    }
}
