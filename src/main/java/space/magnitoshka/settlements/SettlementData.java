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
    private static final com.google.gson.TypeAdapter<Boolean> BOOLEAN_NBT=new com.google.gson.TypeAdapter<>() {
        @Override public void write(com.google.gson.stream.JsonWriter out,Boolean value)throws java.io.IOException {out.value(value);}
        @Override public Boolean read(com.google.gson.stream.JsonReader in)throws java.io.IOException {
            return switch(in.peek()) {
                case BOOLEAN -> in.nextBoolean();
                case NUMBER -> {int value=in.nextInt();if(value!=0&&value!=1)throw new java.io.IOException("Invalid NBT boolean: "+value);yield value==1;}
                case STRING -> Boolean.parseBoolean(in.nextString());
                case NULL -> {in.nextNull();yield null;}
                default -> throw new java.io.IOException("Expected boolean or NBT byte");
            };
        }
    };
    public static final Gson GSON=new GsonBuilder().registerTypeAdapter(boolean.class,BOOLEAN_NBT)
        .registerTypeAdapter(Boolean.class,BOOLEAN_NBT).create();
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
    private SettlementData(WorldState state) {
        if(state==null)throw new IllegalArgumentException("Empty settlement save");
        this.state=state;
        for(var s:state.settlements.values())if(s.radius<=0) {s.radius=SettlementConfig.RADIUS.get();setDirty();}
    }
    public static SettlementData get(MinecraftServer server) {
        var storage=server.overworld().getDataStorage();
        var existing=storage.get(TYPE);
        if(existing!=null)return existing;
        // SavedDataStorage otherwise silently substitutes a new state when a codec cannot read an existing file.
        var path=server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("dimensions/minecraft/overworld/data/smartsettlements/settlements.dat");
        if(java.nio.file.Files.exists(path))throw new IllegalStateException("Cannot read Smart Settlements save; refusing to replace existing territories: "+path);
        return storage.computeIfAbsent(TYPE);
    }
}
