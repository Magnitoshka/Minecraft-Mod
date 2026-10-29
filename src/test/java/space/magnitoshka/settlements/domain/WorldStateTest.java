package space.magnitoshka.settlements.domain;

import org.junit.jupiter.api.Test;
import java.util.Random;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class WorldStateTest {
    private final WorldState world = new WorldState();
    private final UUID owner = UUID.randomUUID();
    private final Rules rules = Rules.defaults();

    private Settlement found(int x) {
        return world.found(owner, "minecraft:overworld", x, 70, 0, 0, rules);
    }

    @Test void foundingCreatesIndependentResidentsAndStarterResources() {
        var settlement = found(0);
        assertEquals(3, settlement.residents.size());
        assertEquals(3, settlement.residents.stream().map(r -> r.id).distinct().count());
        assertTrue(settlement.residents.stream().allMatch(r -> r.profession.equals("unemployed")));
        assertEquals(48, settlement.resources.get("minecraft:bread"));
        assertEquals(owner, settlement.owner);
        assertEquals(1, settlement.level);
    }

    @Test void territoryChecksCircleSeparationAndAllowsOtherDimensions() {
        found(0);
        assertThrows(IllegalArgumentException.class, () -> found(159));
        assertNotNull(found(160));
        assertNotNull(world.found(owner, "minecraft:the_nether", 0, 70, 0, 0, rules));
    }

    @Test void placementUsesLongDistancesWithoutIntegerOverflow() {
        found(Integer.MIN_VALUE);
        assertNotNull(found(Integer.MAX_VALUE));
    }

    @Test void generatedNamesAreUniqueAndReadable() {
        for (int i = 0; i < 500; i++) found(i * 160);
        assertEquals(500, world.settlements.values().stream().map(s -> s.name).distinct().count());
        assertTrue(world.settlements.values().stream().allMatch(s -> s.name.matches("[A-Za-z ]+")));
    }

    @Test void storageLimitsTotalCapacityAndNeverUnderflows() {
        var s = found(0);
        s.resources.clear();
        assertEquals(90, s.deposit("minecraft:oak_log", 90, 100));
        assertEquals(10, s.deposit("minecraft:bread", 20, 100));
        assertEquals(0, s.deposit("minecraft:bread", -5, 100));
        assertEquals(100, s.stored());
        assertEquals(10, s.withdraw("minecraft:bread", 50));
        assertEquals(0, s.withdraw("minecraft:bread", 10));
        assertEquals(0, s.withdraw("minecraft:oak_log", -1));
        assertEquals(90, s.stored());
    }

    @Test void transfersAreAtomicAndOwnerChecked() {
        var s = found(0);
        world.wallets.put(owner, 50L);
        assertFalse(world.transfer(UUID.randomUUID(), s.id, 10, true));
        assertFalse(world.transfer(owner, s.id, 51, true));
        assertFalse(world.transfer(owner, s.id, -10, true));
        assertFalse(world.transfer(owner, s.id, 0, true));
        assertTrue(world.transfer(owner, s.id, 40, true));
        assertEquals(10L, world.wallets.get(owner));
        assertEquals(40, s.treasury);
        assertTrue(world.transfer(owner, s.id, 15, false));
        assertEquals(25L, world.wallets.get(owner));
        assertEquals(25, s.treasury);
    }

    @Test void destinationOverflowDoesNotLoseCoins() {
        var s = found(0);
        world.wallets.put(owner, 50L);
        s.treasury = Long.MAX_VALUE;
        assertFalse(world.transfer(owner, s.id, 1, true));
        assertEquals(50L, world.wallets.get(owner));
        assertEquals(Long.MAX_VALUE, s.treasury);
        world.wallets.put(owner, Long.MAX_VALUE);
        s.treasury = 10;
        assertFalse(world.transfer(owner, s.id, 1, false));
        assertEquals(10, s.treasury);
    }

    @Test void ownershipLookupRejectsOthersAndUnknownIds() {
        var s = found(0);
        assertSame(s, world.owned(owner, s.id));
        assertThrows(IllegalArgumentException.class, () -> world.owned(UUID.randomUUID(), s.id));
        assertThrows(IllegalArgumentException.class, () -> world.owned(owner, UUID.randomUUID()));
    }

    @Test void renameRejectsDuplicatesAndInvalidNames() {
        var first = found(0);
        var second = found(160);
        assertFalse(world.rename(UUID.randomUUID(), first.id, "Stolen"));
        assertFalse(world.rename(owner, first.id, " "));
        assertFalse(world.rename(owner, first.id, "x".repeat(33)));
        assertFalse(world.rename(owner, first.id, second.name.toLowerCase()));
        assertFalse(world.rename(owner, first.id, "bad\nname"));
        assertTrue(world.rename(owner, first.id, "  New Haven  "));
        assertEquals("New Haven", first.name);
    }

    @Test void hireRequiresBedAndMoneyAndChargesExactlyOnce() {
        var s = found(0);
        s.treasury = 100;
        assertNull(world.hire(owner, s.id, rules));
        s.residents.removeFirst();
        s.treasury = 29;
        assertNull(world.hire(owner, s.id, rules));
        s.treasury = 30;
        assertNull(world.hire(UUID.randomUUID(), s.id, rules));
        assertNotNull(world.hire(owner, s.id, rules));
        assertEquals(0, s.treasury);
        assertEquals(3, s.residents.size());
    }

    @Test void onlyStageOneProfessionsAreAvailableAndChangesResetExperience() {
        var s = found(0);
        var r = s.residents.getFirst();
        r.level = 3;
        r.experience = 301;
        assertFalse(world.assign(UUID.randomUUID(), s.id, r.id, "builder"));
        assertFalse(world.assign(owner, s.id, r.id, "miner"));
        assertTrue(world.assign(owner, s.id, r.id, "builder"));
        assertEquals("builder", r.profession);
        assertEquals(1, r.level);
        assertEquals(0, r.experience);
        r.experience = 20;
        assertTrue(world.assign(owner, s.id, r.id, "builder"));
        assertEquals(20, r.experience);
    }

    @Test void starvationSlowsThenStopsWorkThenResidentsLeaveOnThirdDay() {
        var s = found(0);
        s.resources.clear();
        var r = s.residents.getFirst();
        world.newDay(s, rules, new Random(1));
        assertEquals(1, r.hungerDays);
        assertEquals(.5, r.workEfficiency());
        world.newDay(s, rules, new Random(1));
        assertEquals(2, r.hungerDays);
        assertEquals(0, r.workEfficiency());
        assertEquals(3, s.residents.size());
        world.newDay(s, rules, new Random(1));
        assertTrue(s.residents.isEmpty());
    }

    @Test void feedingConsumesFoodAndResetsStarvation() {
        var s = found(0);
        s.residents.getFirst().hungerDays = 2;
        world.newDay(s, rules, new Random(1));
        assertEquals(45, s.resources.get("minecraft:bread"));
        assertTrue(s.residents.stream().allMatch(r -> r.hungerDays == 0));
        assertTrue(s.residents.stream().allMatch(r -> r.mood > 60));
    }

    @Test void immigrationRequiresBedFoodMoodAndSuccessfulRoll() {
        var s = found(0);
        s.residents.removeFirst();
        var success = new Random() { @Override public int nextInt(int bound) { return 0; } };
        world.newDay(s, rules, success);
        assertEquals(3, s.residents.size());
        world.newDay(s, rules, success);
        assertEquals(3, s.residents.size());
        s.residents.removeFirst();
        s.resources.clear();
        s.resources.put("minecraft:bread", 2);
        world.newDay(s, rules, success);
        assertEquals(2, s.residents.size());
    }
    @Test void variedFoodUsesNutritionAndCarriesPartialCreditBetweenDays() {
        var s = found(0);
        s.residents.subList(1, s.residents.size()).clear();
        s.resources.clear();
        s.resources.put("minecraft:carrot", 2);
        s.resources.put("minecraft:potato", 2);
        s.resources.put("minecraft:cobblestone", 10);
        java.util.function.ToIntFunction<String> nutrition = key -> switch (key) {
            case "minecraft:carrot" -> 3;
            case "minecraft:potato" -> 1;
            default -> 0;
        };
        var failure = new Random() { @Override public int nextInt(int bound) { return 99; } };
        world.newDay(s, rules, failure, nutrition);
        assertEquals(1, s.foodCredit);
        assertEquals(0, s.residents.getFirst().hungerDays);
        assertEquals(2, s.resources.get("minecraft:potato"));
        world.newDay(s, rules, failure, nutrition);
        assertEquals(3, s.foodCredit);
        assertEquals(1, s.residents.getFirst().hungerDays);
        assertFalse(s.resources.containsKey("minecraft:potato"));
        s.resources.put("minecraft:carrot", 1);
        world.newDay(s, rules, failure, nutrition);
        assertEquals(1, s.foodCredit);
        assertEquals(0, s.residents.getFirst().hungerDays);
        assertEquals(10, s.resources.get("minecraft:cobblestone"));
    }

    @Test void nutritiousFoodEnablesImmigrationButFailedRollDoesNot() {
        var s = found(0);
        s.residents.removeFirst();
        s.resources.clear();
        s.resources.put("minecraft:carrot", 20);
        java.util.function.ToIntFunction<String> nutrition = key -> key.equals("minecraft:carrot") ? 3 : 0;
        var failure = new Random() { @Override public int nextInt(int bound) { return 99; } };
        world.newDay(s, rules, failure, nutrition);
        assertEquals(2, s.residents.size());
        s.resources.put("minecraft:carrot", 20);
        var success = new Random() { @Override public int nextInt(int bound) { return 0; } };
        world.newDay(s, rules, success, nutrition);
        assertEquals(3, s.residents.size());
    }

    @Test void gsonRoundTripPreservesIdentityAndAdapterFields() {
        var s = found(0);
        s.foodCredit = 4;
        var r = s.residents.getFirst();
        r.spawned = true;
        r.bed = "10,70,5";
        world.wallets.put(owner, 42L);
        world.initializedPlayers.add(owner);
        var gson = new com.google.gson.Gson();
        var restored = gson.fromJson(gson.toJson(world), WorldState.class);
        var copy = restored.owned(owner, s.id);
        assertEquals(s.id, copy.id);
        assertEquals(r.id, copy.residents.getFirst().id);
        assertEquals(4, copy.foodCredit);
        assertTrue(copy.residents.getFirst().spawned);
        assertEquals("10,70,5", copy.residents.getFirst().bed);
        assertEquals(42L, restored.wallets.get(owner));
        assertTrue(restored.initializedPlayers.contains(owner));
        assertEquals(s.resources, copy.resources);
    }
}
