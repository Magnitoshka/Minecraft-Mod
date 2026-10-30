package space.magnitoshka.settlements;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
import net.minecraftforge.network.PacketDistributor;
import space.magnitoshka.settlements.domain.Settlement;

public final class SettlementNetwork {
    private static final SimpleChannel CHANNEL = ChannelBuilder.named(Settlements.MODID + ":main").networkProtocolVersion(7).simpleChannel();
    // Detailed edit plans belong to the save, not the once-per-second UI snapshot.
    private static final com.google.gson.Gson SNAPSHOT_JSON=SettlementData.GSON.newBuilder()
        .addSerializationExclusionStrategy(new com.google.gson.ExclusionStrategy() {
            public boolean shouldSkipField(com.google.gson.FieldAttributes field) {
                return field.getDeclaringClass()==Settlement.class&&field.getName().equals("departedCargo")
                    ||field.getDeclaringClass()==space.magnitoshka.settlements.domain.Building.class&&java.util.Set.of("preparation","columns","access","reserved").contains(field.getName());
            }
            public boolean shouldSkipClass(Class<?> type) {return false;}
        }).create();
    private static final Map<UUID, UUID> VIEWERS = new HashMap<>();
    private static final Map<UUID, UUID> CONVERSATIONS = new HashMap<>();
    private static final Map<UUID,TerrainMap.Survey> LAST_TERRAIN=new HashMap<>();
    public static Consumer<TerrainMap.Survey> terrainReceiver=survey->{};
    public static Consumer<Snapshot> clientReceiver = snapshot -> {};
    public record Notice(String settlement,boolean success,net.minecraft.network.chat.Component message) {}
    public static Consumer<Notice> noticeReceiver=notice->{};
    public record Action(String action, String settlement, String resident, String text, long amount) {}
    public record Summary(String id, String name) {}
    public record Snapshot(Settlement settlement, List<Summary> settlements, long wallet,
                           Map<String, double[]> positions, String selectedResident, boolean open, int radius, int capacity, int beds, double foodDays, Map<String,int[]> buildingPrices, int nextPopulation) {}
    public static void init() {
        CHANNEL.messageBuilder(Action.class).direction(PacketFlow.SERVERBOUND)
            .encoder((m,b) -> { b.writeUtf(m.action,32); b.writeUtf(m.settlement,36); b.writeUtf(m.resident,36); b.writeUtf(m.text,64); b.writeLong(m.amount); })
            .decoder(b -> new Action(b.readUtf(32),b.readUtf(36),b.readUtf(36),b.readUtf(64),b.readLong()))
            .consumerMainThread((m,c) -> { if (c.getSender() != null) ServerSettlements.action(c.getSender(),m); }).add();
        CHANNEL.messageBuilder(Snapshot.class).direction(PacketFlow.CLIENTBOUND)
            .encoder((m,b) -> b.writeUtf(SNAPSHOT_JSON.toJson(m),262144))
            .decoder(b -> SettlementData.GSON.fromJson(b.readUtf(262144),Snapshot.class))
            .consumerMainThread((m,c) -> clientReceiver.accept(m)).add();
        CHANNEL.messageBuilder(TerrainMap.Survey.class).direction(PacketFlow.CLIENTBOUND)
            .encoder(TerrainMap::encode).decoder(TerrainMap::decode)
            .consumerMainThread((m,c)->terrainReceiver.accept(m)).add();
        CHANNEL.messageBuilder(Notice.class).direction(PacketFlow.CLIENTBOUND)
            .encoder((m,b)->{
                b.writeUtf(m.settlement,36);b.writeBoolean(m.success);
                var json=net.minecraft.network.chat.ComponentSerialization.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,m.message).getOrThrow();
                b.writeUtf(json.toString(),8192);
            })
            .decoder(b->{
                String settlement=b.readUtf(36);boolean success=b.readBoolean();
                var json=com.google.gson.JsonParser.parseString(b.readUtf(8192));
                var message=net.minecraft.network.chat.ComponentSerialization.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,json).getOrThrow();
                return new Notice(settlement,success,message);
            })
            .consumerMainThread((m,c)->noticeReceiver.accept(m)).add();
    }
    public static void notice(ServerPlayer player,UUID settlement,boolean success,net.minecraft.network.chat.Component message) {
        player.sendSystemMessage(message);
        CHANNEL.send(new Notice(settlement==null?"":settlement.toString(),success,message),PacketDistributor.PLAYER.with(player));
    }
    public static void ownerNotice(net.minecraft.server.MinecraftServer server,Settlement settlement,boolean success,String key,Object...args) {
        var owner=server.getPlayerList().getPlayer(settlement.owner);
        if(owner!=null)notice(owner,settlement.id,success,net.minecraft.network.chat.Component.translatable(key,args));
    }
    public static void request(String action, String settlement, String resident, String text, long amount) {
        CHANNEL.send(new Action(action,settlement,resident,text,amount),PacketDistributor.SERVER.noArg());
    }
    public static void open(ServerPlayer player, UUID settlement, UUID resident) {
        CONVERSATIONS.remove(player.getUUID());
        if(resident!=null)CONVERSATIONS.put(player.getUUID(),resident);
        VIEWERS.put(player.getUUID(),settlement);
        send(player,settlement,resident == null ? "" : resident.toString(),true);
    }
    public static void close(UUID player) { VIEWERS.remove(player); CONVERSATIONS.remove(player); LAST_TERRAIN.remove(player); }
    public static void endDialogue(UUID player) {CONVERSATIONS.remove(player);}
    public static boolean talking(CitizenEntity citizen) {
        var server=citizen.level().getServer();
        CONVERSATIONS.entrySet().removeIf(entry->{
            var player=server.getPlayerList().getPlayer(entry.getKey());
            if(player==null)return true;
            if(!entry.getValue().equals(citizen.getUUID()))return false;
            return player.level()!=citizen.level()||player.distanceToSqr(citizen)>64||!player.isAlive();
        });
        return CONVERSATIONS.containsValue(citizen.getUUID());
    }
    public static void refreshWarehouse(ServerPlayer player,UUID id) {send(player,id,"",false);}
    public static void refresh(ServerPlayer player) {
        if (VIEWERS.containsKey(player.getUUID())) send(player,VIEWERS.get(player.getUUID()),"",false);
    }
    public static void tickTerrain(net.minecraft.server.MinecraftServer server) {
        var visible=new HashSet<UUID>();
        for(var entry:VIEWERS.entrySet()) {
            var player=server.getPlayerList().getPlayer(entry.getKey());
            if(entry.getValue()!=null&&player!=null&&!(player.containerMenu instanceof WarehouseMenu))visible.add(entry.getValue());
        }
        TerrainMap.tick(server,visible);
    }
    private static Map<String,int[]> prices() {
        var result=new LinkedHashMap<String,int[]>();
        for(var type:space.magnitoshka.settlements.domain.BuildingType.values())for(int tier=1;tier<=3;tier++)
            result.put(type.id+":"+tier,new int[]{SettlementConfig.price(type,tier,true),SettlementConfig.price(type,tier,false)});
        return result;
    }
    private static double foodDays(Settlement s) {
        if(s==null || s.residents.isEmpty())return 0;
        long food=s.foodCredit;
        for(var entry:s.resources.entrySet()) food+=(long)entry.getValue()*ServerSettlements.nutrition(entry.getKey());
        return (double)food/(s.residents.size()*SettlementConfig.DAILY_FOOD.get()*5L);
    }
    private static void send(ServerPlayer player, UUID id, String resident, boolean open) {
        var state=SettlementData.get(player.level().getServer()).state;
        var settlement=state.settlements.get(id);
        if (settlement!=null && !settlement.owner.equals(player.getUUID())) return;
        var summaries=state.settlements.values().stream().filter(s->s.owner.equals(player.getUUID()))
            .map(s->new Summary(s.id.toString(),s.name)).toList();
        Map<String,double[]> positions=new HashMap<>();
        if (settlement!=null) for (var r:settlement.residents) {
            for (var level:player.level().getServer().getAllLevels()) {
                var entity=level.getEntity(r.id);
                if (entity!=null) { positions.put(r.id.toString(),new double[]{entity.getX(),entity.getZ()}); break; }
            }
        }
        CHANNEL.send(new Snapshot(settlement,summaries,state.wallets.getOrDefault(player.getUUID(),0L),positions,resident,open,
            settlement==null?SettlementConfig.RADIUS.get():Construction.radius(settlement),settlement==null?SettlementConfig.CAPACITY.get():Construction.capacity(settlement),settlement==null?0:settlement.bedCapacity,foodDays(settlement),prices(),settlement==null?0:Progression.population(settlement.level+1)),PacketDistributor.PLAYER.with(player));
        if(settlement!=null&&!(player.containerMenu instanceof WarehouseMenu)) {
            var terrain=TerrainMap.get(player.level().getServer(),settlement,Construction.radius(settlement));
            if(terrain!=null&&(open||terrain!=LAST_TERRAIN.get(player.getUUID()))) {
                CHANNEL.send(terrain,PacketDistributor.PLAYER.with(player));LAST_TERRAIN.put(player.getUUID(),terrain);
            }
        }
    }
}
