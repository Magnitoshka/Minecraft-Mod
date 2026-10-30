package space.magnitoshka.settlements.domain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.random.RandomGenerator;
import java.util.function.ToIntFunction;

/** Authoritative world-wide settlement and wallet state; call only from server thread. */
public final class WorldState {
    public final Map<UUID, Settlement> settlements = new HashMap<>();
    public final Map<UUID, Long> wallets = new HashMap<>();
    public final Set<UUID> initializedPlayers = new HashSet<>();
    private static final String[] PREFIXES = {"Ash", "Stone", "Elder", "Oak", "Silver", "Raven", "Green", "Winter", "Iron", "Willow", "Dawn", "Grey", "River", "Bright", "Moss", "Pine"};
    private static final String[] SUFFIXES = {"ford", "dale", "mere", "hollow", "gard", "wick", "holt", "fell", "barrow", "haven", "wood", "brook"};
    private static final String[] EPITHETS = {"", "Old ", "High ", "West ", "East ", "North ", "South ", "New "};
    private static final String[] NAMES = {"Alden", "Rowan", "Elara", "Bryn", "Cedric", "Mira", "Torin", "Lyra", "Gareth", "Nora", "Finley", "Freya"};

    public Settlement found(UUID owner, String dimension, int x, int y, int z, long day, Rules rules) {
        if (owner == null || dimension == null || dimension.isBlank()) throw new IllegalArgumentException("Missing owner or dimension");
        if(placementConflict(dimension,x,z,rules)!=null)throw new IllegalArgumentException("Settlement territory is too close");
        var s = new Settlement();
        s.owner = owner;
        s.dimension = dimension;
        s.x = x;
        s.y = y;
        s.z = z;
        s.lastDay = day;
        s.radius=rules.radius();
        s.name = nextName();
        s.deposit("minecraft:oak_log", rules.starterWood(), rules.capacity());
        s.deposit("minecraft:cobblestone", rules.starterStone(), rules.capacity());
        s.deposit("minecraft:bread", rules.starterFood(), rules.capacity());
        for (int i = 0; i < rules.initialResidents(); i++) s.residents.add(newResident());
        settlements.put(s.id, s);
        return s;
    }

    public Settlement placementConflict(String dimension,int x,int z,Rules rules) {
        for(var other:settlements.values()) {
            int radius=other.radius>0?other.radius:rules.radius();
            double distance=Math.hypot((double)x-other.x,(double)z-other.z);
            if(dimension.equals(other.dimension)&&distance<rules.radius()+(double)radius+rules.gap())return other;
        }
        return null;
    }
    public Settlement owned(UUID actor, UUID id) {
        var s = accessible(actor, id);
        if (s == null) throw new IllegalArgumentException("Unknown settlement or not the owner");
        return s;
    }

    private Settlement accessible(UUID actor, UUID id) {
        var s = settlements.get(id);
        return s != null && actor != null && actor.equals(s.owner) ? s : null;
    }

    public boolean transfer(UUID actor, UUID settlementId, long amount, boolean toTreasury) {
        var s = accessible(actor, settlementId);
        if (s == null || amount <= 0) return false;
        long wallet = wallets.getOrDefault(actor, 0L);
        long source = toTreasury ? wallet : s.treasury;
        long destination = toTreasury ? s.treasury : wallet;
        if (source < amount || destination < 0 || amount > Long.MAX_VALUE - destination) return false;
        wallets.put(actor, toTreasury ? wallet - amount : wallet + amount);
        s.treasury = toTreasury ? s.treasury + amount : s.treasury - amount;
        return true;
    }

    public boolean rename(UUID actor, UUID settlementId, String name) {
        var s = accessible(actor, settlementId);
        if (s == null || name == null) return false;
        name = name.strip();
        if (name.isEmpty() || name.length() > 32 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || c == '\u00a7')) return false;
        String wanted = name;
        if (settlements.values().stream().anyMatch(other -> !other.id.equals(settlementId) && wanted.equalsIgnoreCase(other.name))) return false;
        s.name = name;
        return true;
    }

    public Resident hire(UUID actor, UUID settlementId, Rules rules) {
        var s = accessible(actor, settlementId);
        if (s == null || s.residents.size() >= rules.beds()) return null;
        long cost = s.hireCost(rules);
        if (s.treasury < cost) return null;
        var resident = newResident();
        s.treasury -= cost;
        s.residents.add(resident);
        return resident;
    }

    public boolean assign(UUID actor, UUID settlementId, UUID residentId, String profession) {
        var s = accessible(actor, settlementId);
        if (s == null || !Set.of("builder","unemployed","lumberjack","miner","farmer","courier").contains(profession)) return false;
        for (var r : s.residents) {
            if (r.id.equals(residentId)) {
                String required=switch(profession) {case "lumberjack"->"lumber_hut";case "miner"->"mine";case "farmer"->"field";case "courier"->"warehouse";default->"";};
                String workplace="";
                if(!required.isEmpty()) {
                    workplace=s.buildings.stream().filter(b->b.complete && b.type.equals(required))
                        .filter(b->s.residents.stream().noneMatch(other->!other.id.equals(r.id) && b.id.equals(other.workplace)))
                        .map(b->b.id).findFirst().orElse(null);
                    if(workplace==null)return false;
                }
                r.workplace=workplace;
                if (!r.profession.equals(profession)) {
                    BuilderAssignments.release(s,r);
                    r.workTicks=0;r.carriedWheat=0;r.routeDeparted=false;r.workStatus="idle";
                    r.profession = profession;
                    r.level = 1;
                    r.experience = 0;
                }
                return true;
            }
        }
        return false;
    }

    public void newDay(Settlement s, Rules rules, RandomGenerator random) {
        newDay(s, rules, random, key -> "minecraft:bread".equals(key) ? 5 : 0);
    }

    public void newDay(Settlement s, Rules rules, RandomGenerator random, ToIntFunction<String> nutrition) {
        long dailyNutrition = (long) rules.foodPerResident() * 5;
        int bedIndex = 0;
        var iterator = s.residents.iterator();
        while (iterator.hasNext()) {
            var resident = iterator.next();
            long credit = Math.max(0, s.foodCredit);
            // Snapshot keys: withdraw can remove the last item of a resource from the map.
            for (var key : s.resources.keySet().stream().sorted().toList()) {
                if (credit >= dailyNutrition) break;
                int points = nutrition.applyAsInt(key);
                if (points <= 0) continue;
                long neededItems = (dailyNutrition - credit + points - 1) / points;
                int consumed = s.withdraw(key, (int) Math.min(Integer.MAX_VALUE, neededItems));
                credit += (long) consumed * points;
            }
            if (credit >= dailyNutrition) {
                credit -= dailyNutrition;
                resident.hungerDays = 0;
            } else resident.hungerDays++;
            s.foodCredit = (int) Math.min(Integer.MAX_VALUE, credit);
            if (resident.hungerDays >= 3) {
                BuilderAssignments.retire(s,resident);
                iterator.remove();
                continue;
            }
            resident.mood = Math.max(0, 100 - resident.hungerDays * 50 - (bedIndex++ >= rules.beds() ? 25 : 0));
        }
        double mood = s.residents.stream().mapToInt(r -> r.mood).average().orElse(100);
        long foodNeeded = (long) (s.residents.size() + 1) * dailyNutrition * rules.immigrantFoodDays();
        long foodAvailable = Math.max(0, s.foodCredit);
        for (var entry : s.resources.entrySet()) {
            foodAvailable += (long) Math.max(0, entry.getValue()) * Math.max(0, nutrition.applyAsInt(entry.getKey()));
        }
        if (s.residents.size() < rules.beds() && mood > rules.moodThreshold()
                && foodAvailable >= foodNeeded
                && random.nextInt(100) < rules.immigrationPercent()) {
            s.residents.add(newResident());
        }
    }

    private Resident newResident() {
        int index = Math.floorMod(UUID.randomUUID().hashCode(), NAMES.length);
        return new Resident(NAMES[index]);
    }

    private String nextName() {
        for (long i = 0; ; i++) {
            long n = i;
            String base = PREFIXES[(int) (n % PREFIXES.length)];
            n /= PREFIXES.length;
            base += SUFFIXES[(int) (n % SUFFIXES.length)];
            n /= SUFFIXES.length;
            String candidate = EPITHETS[(int) (n % EPITHETS.length)] + base;
            n /= EPITHETS.length;
            // Unlimited unique combinations, still English-looking and without numeric suffixes.
            while (n > 0) {
                candidate = PREFIXES[(int) (n % PREFIXES.length)].toLowerCase(Locale.ROOT) + candidate;
                n /= PREFIXES.length;
            }
            String wanted = candidate;
            if (settlements.values().stream().noneMatch(s -> wanted.equalsIgnoreCase(s.name))) return candidate;
        }
    }
}
