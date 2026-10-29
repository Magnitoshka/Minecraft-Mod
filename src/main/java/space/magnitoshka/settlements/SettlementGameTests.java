package space.magnitoshka.settlements;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.gametest.GameTest;
import space.magnitoshka.settlements.domain.WorldState;
import space.magnitoshka.settlements.domain.Settlement;
import space.magnitoshka.settlements.domain.Resident;

/** Runtime acceptance tests gathered from the mod instance by Forge in development runs. */
public class SettlementGameTests {
    @GameTest(maxTicks = 60)
    public void citizenRegistryAndAuthoritySurviveTicking(GameTestHelper helper) {
        var level = helper.getLevel();
        var data = SettlementData.get(level.getServer());
        var owner = UUID.randomUUID();
        var pos = helper.absolutePos(new BlockPos(1, 1, 1));
        var settlement = data.state.found(owner, level.dimension().identifier().toString(),
            pos.getX(), pos.getY(), pos.getZ(), Math.floorDiv(level.getOverworldClockTime(), 24000),
            SettlementConfig.rules());
        helper.assertTrue(!settlement.residents.isEmpty(), "Default settlement must have residents");
        var resident = settlement.residents.getFirst();
        var citizen = Settlements.CITIZEN.get().create(level, EntitySpawnReason.TRIGGERED);
        if (citizen == null) {
            data.state.settlements.remove(settlement.id);
            throw helper.assertionException("Citizen registry factory returned null");
        }
        try {
            citizen.initialize(settlement.id, resident);
            citizen.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + .5);
            // Keep this test about registration and authority rather than pathfinding/falling.
            citizen.setNoAi(true);
            citizen.setNoGravity(true);
            helper.assertTrue(citizen.getType() == Settlements.CITIZEN.get(), "Citizen type must be registered");
            helper.assertTrue(citizen.getUUID().equals(resident.id), "Entity UUID must match SavedData resident identity");
            helper.assertTrue(citizen.getMaxHealth() == 20f, "Entity attributes must be attached before creation");
            helper.assertTrue(level.addFreshEntity(citizen), "Citizen must enter the server level");
            resident.spawned = true;
            data.setDirty();
        } catch (Throwable failure) {
            citizen.discard();
            data.state.settlements.remove(settlement.id);
            data.setDirty();
            throw failure;
        }
        helper.runAfterDelay(25, () -> {
            try {
                helper.assertTrue(citizen.tickCount >= 20, "Citizen must receive server ticks");
                helper.assertTrue(citizen.isAlive() && !citizen.isRemoved(), "Bound resident must survive reconciliation");
                helper.assertTrue(level.getEntity(resident.id) == citizen, "Level UUID lookup must preserve entity identity");
                helper.assertTrue(citizen.getName().getString().contains(resident.name), "Resident name must reach entity view");
                helper.succeed();
            } finally {
                citizen.discard();
                data.state.settlements.remove(settlement.id);
                data.setDirty();
            }
        });
    }

    @GameTest
    public void savedEconomyUsesRegisteredNutritionAndRoundTrips(GameTestHelper helper) {
        var level = helper.getLevel();
        var data = SettlementData.get(level.getServer());
        var owner = UUID.randomUUID();
        // Far from the citizen test's territory; this test does not materialize its settlement.
        var pos = helper.absolutePos(new BlockPos(400, 1, 0));
        var settlement = data.state.found(owner, level.dimension().identifier().toString(),
            pos.getX(), pos.getY(), pos.getZ(), Math.floorDiv(level.getOverworldClockTime(), 24000),
            SettlementConfig.rules());
        try {
            helper.assertTrue(SettlementData.get(level.getServer()) == data, "World must share one authoritative SavedData instance");
            helper.assertTrue(SettlementConfig.rules(0).beds() == 0
                && SettlementConfig.rules(0).initialResidents() == 0, "Zero physical beds must allow no initial residents");
            helper.assertTrue(SettlementConfig.rules(1).initialResidents() <= 1,
                "Initial residents must be clamped to available beds");
            helper.assertTrue(Settlements.BANNER.get().getDefaultInstance().getCount() == 1, "Founder banner must resolve through item registry");
            helper.assertTrue(ServerSettlements.nutrition("minecraft:carrot") == 3, "Registered carrot nutrition must be available on server");
            helper.assertTrue(ServerSettlements.nutrition("minecraft:cobblestone") == 0, "Stone must never feed residents");
            data.state.wallets.put(owner, 50L);
            helper.assertTrue(data.state.transfer(owner, settlement.id, 30, true), "Wallet deposit must succeed");
            helper.assertTrue(!data.state.transfer(UUID.randomUUID(), settlement.id, 1, false), "Other owners must not withdraw coins");
            settlement.resources.clear();
            settlement.resources.put("minecraft:carrot", 10);
            settlement.resources.put("minecraft:cobblestone", 4);
            data.state.newDay(settlement, SettlementConfig.rules(), new Random(1), ServerSettlements::nutrition);
            helper.assertTrue(settlement.residents.stream().allMatch(r -> r.hungerDays == 0), "Carrots must feed every resident");
            helper.assertTrue(settlement.resources.getOrDefault("minecraft:carrot", 0) < 10, "Real food must leave virtual warehouse");
            helper.assertTrue(settlement.resources.get("minecraft:cobblestone") == 4, "Non-food stock must remain intact");
            var restored = SettlementData.GSON.fromJson(SettlementData.GSON.toJson(data.state), WorldState.class);
            var copy = restored.owned(owner, settlement.id);
            helper.assertTrue(copy.id.equals(settlement.id), "Settlement ID must survive serialized SavedData state");
            helper.assertTrue(copy.residents.getFirst().id.equals(settlement.residents.getFirst().id), "Resident ID must survive reload");
            helper.assertTrue(copy.treasury == 30 && restored.wallets.get(owner) == 20L, "Both accounts must survive reload");
            helper.assertTrue(copy.resources.equals(settlement.resources), "Warehouse stock must survive reload");

            var legacy = SettlementData.TYPE.codec().parse(NbtOps.INSTANCE,
                StringTag.valueOf(SettlementData.GSON.toJson(data.state))).getOrThrow();
            helper.assertTrue(legacy.state.owned(owner, settlement.id).id.equals(settlement.id),
                "Early string-based NBT saves must remain readable");
            // Keep this large fixture local: it must never reach the world's SavedData storage.
            var archive = new SettlementData();
            var large = new Settlement();
            large.owner = UUID.randomUUID();
            large.name = "Archive";
            large.dimension = "minecraft:overworld";
            for (int i = 0; i < 600; i++) large.residents.add(new Resident("Resident " + i));
            archive.state.settlements.put(large.id, large);
            archive.state.wallets.put(large.owner, 42L);
            helper.assertTrue(SettlementData.GSON.toJson(archive.state).getBytes(StandardCharsets.UTF_8).length > 65535,
                "Persistence fixture must exceed the old NBT string limit");
            var encoded = SettlementData.TYPE.codec().encodeStart(NbtOps.INSTANCE, archive).getOrThrow();
            helper.assertTrue(encoded instanceof CompoundTag, "New saves must use structured NBT");
            try {
                var bytes = new ByteArrayOutputStream();
                NbtIo.writeCompressed((CompoundTag) encoded, bytes);
                var reread = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
                var decoded = SettlementData.TYPE.codec().parse(NbtOps.INSTANCE, reread).getOrThrow();
                var persisted = decoded.state.owned(large.owner, large.id);
                helper.assertTrue(persisted.residents.size() == 600, "All residents must survive compressed NBT round-trip");
                helper.assertTrue(persisted.residents.getFirst().id.equals(large.residents.getFirst().id),
                    "Large-save NBT must preserve resident identity");
                helper.assertTrue(decoded.state.wallets.get(large.owner) == 42L,
                    "Large-save NBT must preserve wallet balances");
            } catch (IOException failure) {
                throw new RuntimeException("Structured settlement NBT must support saves larger than 64 KiB", failure);
            }
            helper.succeed();
        } finally {
            data.state.settlements.remove(settlement.id);
            data.state.wallets.remove(owner);
            data.setDirty();
        }
    }

    @GameTest
    public void hallContainsThreeCompleteBedsAndWarehouse(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(30, 2, 30));
        Map<BlockPos, BlockState> previous = new HashMap<>();
        for (int x = -4; x <= 4; x++) for (int y = -1; y <= 5; y++) for (int z = -3; z <= 3; z++) {
            var target = pos.offset(x, y, z);
            previous.put(target, level.getBlockState(target));
            level.setBlock(target, Blocks.AIR.defaultBlockState(), 2);
        }
        try {
            var settlement = new Settlement();
            settlement.x = pos.getX();
            settlement.y = pos.getY();
            settlement.z = pos.getZ();
            ServerSettlements.buildHall(level, pos);
            helper.assertTrue(ServerSettlements.countBeds(level, settlement) == 3,
                "Three complete hall beds must provide three recruitment places");
            for (int i = 0; i < 3; i++) {
                var foot = level.getBlockState(pos.offset(-2 + i * 2, 0, 1));
                var head = level.getBlockState(pos.offset(-2 + i * 2, 0, 2));
                helper.assertTrue(foot.is(Blocks.BED.red()) && foot.getValue(BedBlock.PART) == BedPart.FOOT,
                    "Hall must contain bed foot " + i);
                helper.assertTrue(head.is(Blocks.BED.red()) && head.getValue(BedBlock.PART) == BedPart.HEAD,
                    "Hall must contain matching bed head " + i);
            }
            helper.assertTrue(level.getBlockState(pos.offset(3, 0, 0)).is(Blocks.BARREL), "Hall must expose warehouse barrel");
            helper.assertTrue(level.getBlockState(pos.offset(-3, 0, 0)).is(Blocks.CRAFTING_TABLE), "Hall must expose recruitment table");
            helper.assertTrue(level.getBlockState(pos.offset(0, 0, -3)).isAir()
                && level.getBlockState(pos.offset(0, 1, -3)).isAir(), "Hall entrance must be two blocks high");
            helper.assertTrue(level.getBlockState(pos.offset(0, -1, -3)).is(Blocks.COBBLESTONE), "Entrance must have a solid floor");
            level.setBlock(pos.offset(-2, 0, 2), Blocks.AIR.defaultBlockState(), 3);
            helper.assertTrue(ServerSettlements.countBeds(level, settlement) == 2,
                "A bed with a missing head must not count toward recruitment capacity");
            for (int i = 1; i < 3; i++) {
                level.setBlock(pos.offset(-2 + i * 2, 0, 2), Blocks.AIR.defaultBlockState(), 3);
            }
            helper.assertTrue(ServerSettlements.countBeds(level, settlement) == 0,
                "Removing every bed must leave no recruitment places");
            helper.succeed();
        } finally {
            previous.forEach((target, state) -> level.setBlock(target, state, 2));
        }
    }
}
