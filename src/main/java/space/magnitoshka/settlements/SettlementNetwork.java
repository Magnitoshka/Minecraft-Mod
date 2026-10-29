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
    private static final SimpleChannel CHANNEL = ChannelBuilder.named(Settlements.MODID + ":main").networkProtocolVersion(1).simpleChannel();
    private static final Map<UUID, UUID> VIEWERS = new HashMap<>();
    public static Consumer<Snapshot> clientReceiver = snapshot -> {};
    public record Action(String action, String settlement, String resident, String text, long amount) {}
    public record Summary(String id, String name) {}
    public record Snapshot(Settlement settlement, List<Summary> settlements, long wallet,
                           Map<String, double[]> positions, String selectedResident, boolean open, int radius, int capacity, int beds, double foodDays) {}
    public static void init() {
        CHANNEL.messageBuilder(Action.class).direction(PacketFlow.SERVERBOUND)
            .encoder((m,b) -> { b.writeUtf(m.action,32); b.writeUtf(m.settlement,36); b.writeUtf(m.resident,36); b.writeUtf(m.text,64); b.writeLong(m.amount); })
            .decoder(b -> new Action(b.readUtf(32),b.readUtf(36),b.readUtf(36),b.readUtf(64),b.readLong()))
            .consumerMainThread((m,c) -> { if (c.getSender() != null) ServerSettlements.action(c.getSender(),m); }).add();
        CHANNEL.messageBuilder(Snapshot.class).direction(PacketFlow.CLIENTBOUND)
            .encoder((m,b) -> b.writeUtf(SettlementData.GSON.toJson(m),262144))
            .decoder(b -> SettlementData.GSON.fromJson(b.readUtf(262144),Snapshot.class))
            .consumerMainThread((m,c) -> clientReceiver.accept(m)).add();
    }
    public static void request(String action, String settlement, String resident, String text, long amount) {
        CHANNEL.send(new Action(action,settlement,resident,text,amount),PacketDistributor.SERVER.noArg());
    }
    public static void open(ServerPlayer player, UUID settlement, UUID resident) {
        VIEWERS.put(player.getUUID(),settlement);
        send(player,settlement,resident == null ? "" : resident.toString(),true);
    }
    public static void close(UUID player) { VIEWERS.remove(player); }
    public static void refresh(ServerPlayer player) {
        if (VIEWERS.containsKey(player.getUUID())) send(player,VIEWERS.get(player.getUUID()),"",false);
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
            SettlementConfig.RADIUS.get(),SettlementConfig.CAPACITY.get(),settlement==null?0:settlement.bedCapacity,foodDays(settlement)),PacketDistributor.PLAYER.with(player));
    }
}
