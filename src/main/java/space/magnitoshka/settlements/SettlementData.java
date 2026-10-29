package space.magnitoshka.settlements;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.serialization.Codec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import space.magnitoshka.settlements.domain.WorldState;

public final class SettlementData extends SavedData {
    public static final Gson GSON = new GsonBuilder().create();
    static final SavedDataType<SettlementData> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath(Settlements.MODID, "settlements"), SettlementData::new,
        Codec.PASSTHROUGH.xmap(dynamic -> {
            var json=dynamic.convert(com.mojang.serialization.JsonOps.INSTANCE).getValue();
            // Read early development saves too; new saves use structured NBT without a 64 KiB string limit.
            return new SettlementData(json.isJsonPrimitive() ? GSON.fromJson(json.getAsString(),WorldState.class)
                : GSON.fromJson(json,WorldState.class));
        }, data -> new com.mojang.serialization.Dynamic<>(com.mojang.serialization.JsonOps.INSTANCE,GSON.toJsonTree(data.state))), null);
    public final WorldState state;
    public SettlementData() { this(new WorldState()); }
    private SettlementData(WorldState state) { this.state = state; }
    public static SettlementData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }
}
