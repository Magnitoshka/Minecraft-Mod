package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import space.magnitoshka.settlements.domain.*;

public final class Construction {
    public record Result(String code,Component message) {
        public boolean ok() {return code.equals("ok");}
    }
    private static Result reject(String code) {return new Result(code,Component.translatable("message.smartsettlements.build_"+code));}
    public static Result request(ServerPlayer player,Settlement s,String typeId,int dx,int dz) {
        var type=BuildingType.byId(typeId);
        if(type==null||type==BuildingType.TOWN_HALL)return reject("invalid");
        if(s.level<type.requiredSettlementLevel)return reject("locked");
        var level=(ServerLevel)player.level();
        if(!level.dimension().identifier().toString().equals(s.dimension))return reject("dimension");
        if(Math.abs((long)dx)>radius(s)||Math.abs((long)dz)>radius(s))return reject("territory");
        int x=s.x+dx,z=s.z+dz;
        if(Math.hypot(Math.abs((double)dx)+type.halfWidth,Math.abs((double)dz)+type.halfDepth)>radius(s))return reject("territory");
        if(s.buildings.stream().filter(b->!b.complete).count()>=16)return reject("queue_full");
        if(Math.abs(dx)<=4+type.halfWidth+1&&Math.abs(dz)<=3+type.halfDepth+1)return reject("collision");
        for(var b:s.buildings) {
            var other=BuildingType.byId(b.type);
            if(other!=null&&Math.abs((long)x-b.x)<=type.halfWidth+other.halfWidth+1&&Math.abs((long)z-b.z)<=type.halfDepth+other.halfDepth+1)return reject("collision");
        }
        if(!level.hasChunkAt(new BlockPos(x-type.halfWidth,s.y,z-type.halfDepth))||!level.hasChunkAt(new BlockPos(x+type.halfWidth,s.y,z+type.halfDepth)))return reject("unloaded");
        // Future building footprints may not cut off a saved access route.
        for(var b:s.buildings)for(var point:b.access)
            if(Math.abs(point.x()-x)<=type.halfWidth&&Math.abs(point.z()-z)<=type.halfDepth)return reject("collision");
        var result=SitePreparation.plan(level,player,s,type,x,z);
        if(result.plan()==null) {
            var at=result.at();
            var block=level.hasChunkAt(at)?level.getBlockState(at).getBlock().getName():Component.literal("?");
            var message=Component.translatable("message.smartsettlements.site_detail",
                Component.translatable("message.smartsettlements.site_"+result.problem()),block,at.getX(),at.getY(),at.getZ());
            return new Result("site",message);
        }
        var plan=result.plan();int y=plan.y();
        int woodPrice=SettlementConfig.price(type,1,true),stonePrice=SettlementConfig.price(type,1,false);
        if(!affordable(s,woodPrice,stonePrice))return reject("materials");
        // Reserve base building materials only; depth must not require a warehouse-sized prepayment.
        var building=new Building(type,x,y,z,BuildingTemplates.pieces(type,1,1).size());
        building.templateVersion=1;building.priceWood=woodPrice;building.priceStone=stonePrice;
        reserve(s,building,woodPrice,stonePrice);
        building.logisticsInitialized=true;
        building.columns=plan.columns();building.access=plan.access();building.preparationTotal=plan.steps();
        building.foundationRemaining=plan.fill();building.foundationPrepaid=false;building.hasStand=true;
        var entrance=plan.access().getLast();
        building.standX=entrance.x();building.standY=entrance.y();building.standZ=entrance.z();
        s.buildings.add(building);
        return new Result("ok",Component.translatable("message.smartsettlements.build_ok",
            Component.translatable("building.smartsettlements."+type.id),plan.fill()));
    }
    public static boolean affordable(Settlement s,int wood,int stone) {
        return wood(s)>=wood&&s.resources.getOrDefault("minecraft:cobblestone",0)>=stone;
    }
    public static void reserve(Settlement s,Building building,int wood,int stone) {
        int remaining=wood;
        for(var key:s.resources.keySet().stream().sorted().toList())if(isWood(key)&&remaining>0) {
            int moved=s.withdraw(key,remaining);if(moved>0)building.reserved.put(key,moved);remaining-=moved;
        }
        building.reserved.put("minecraft:cobblestone",s.withdraw("minecraft:cobblestone",stone));
    }
    public static boolean isWood(String key) {
        var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
        return item!=null&&item.getDefaultInstance().is(ItemTags.LOGS);
    }
    private static long wood(Settlement s) {return s.resources.entrySet().stream().filter(e->isWood(e.getKey())).mapToLong(Map.Entry::getValue).sum();}
    public static List<BlockPos> beds(ServerLevel level,Settlement s) {
        var beds=new ArrayList<BlockPos>();
        for(int i=0;i<SettlementConfig.BEDS.get();i++)addBed(level,beds,new BlockPos(s.x-2+i*2,s.y,s.z+2));
        for(var b:s.buildings)if(b.type.equals("house")&&(b.complete||b.upgradeFromLevel>0)) {
            for(var piece:BuildingTemplates.pieces(BuildingType.HOUSE,b.level,b.upgradeFromLevel>0?b.upgradeFromVersion:b.templateVersion))
                if(piece.state().getBlock() instanceof BedBlock&&piece.state().getValue(BedBlock.PART)==BedPart.HEAD)
                    addBed(level,beds,piece.at(new BlockPos(b.x,b.y,b.z)));
        }
        return beds;
    }
    private static void addBed(ServerLevel level,List<BlockPos> beds,BlockPos head) {
        if(!level.hasChunkAt(head))return;
        var state=level.getBlockState(head);
        if(!(state.getBlock() instanceof BedBlock)||state.getValue(BedBlock.PART)!=BedPart.HEAD)return;
        var foot=head.relative(state.getValue(BedBlock.FACING).getOpposite());
        var footState=level.getBlockState(foot);
        if(footState.getBlock()==state.getBlock()&&footState.getValue(BedBlock.PART)==BedPart.FOOT&&footState.getValue(BedBlock.FACING)==state.getValue(BedBlock.FACING))beds.add(head);
    }
    public static boolean canChange(ServerLevel level,BlockPos pos,UUID owner) {
        if(!level.hasChunkAt(pos)||!level.getWorldBorder().isWithinBounds(pos))return false;
        var server=level.getServer();var player=server.getPlayerList().getPlayer(owner);
        if(player!=null)return level.mayInteract(player,pos);
        // Offline NPC work never receives the owner's operator exemption inside protected spawn.
        var spawn=level.getRespawnData();
        int radius=server instanceof net.minecraft.server.dedicated.DedicatedServer dedicated?dedicated.spawnProtectionRadius():0;
        return server.getPlayerList().getOps().isEmpty()||radius<=0
            ||level.dimension()!=spawn.dimension()
            ||Math.max(Math.abs((long)pos.getX()-spawn.pos().getX()),Math.abs((long)pos.getZ()-spawn.pos().getZ()))>radius;
    }
    public static String bedKey(BlockPos p) {return p.getX()+","+p.getY()+","+p.getZ();}
    public static int radius(Settlement s) {return s.radius>0?s.radius:SettlementConfig.RADIUS.get();}
    public static int capacity(Settlement s) {
        long bonus=s.buildings.stream().filter(b->(b.complete||b.upgradeFromLevel>0)&&b.type.equals("warehouse")).mapToLong(b->SettlementConfig.tierCapacity(b.level)).sum();
        return (int)Math.min(Integer.MAX_VALUE,SettlementConfig.CAPACITY.get()+bonus);
    }
    public static void award(Resident r,int xp) {
        r.experience=(int)Math.min(Integer.MAX_VALUE,(long)r.experience+xp);
        r.level=r.experience>=1500?5:r.experience>=700?4:r.experience>=300?3:r.experience>=100?2:1;
    }
    private Construction() {}
}
