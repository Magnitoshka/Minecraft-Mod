package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import space.magnitoshka.settlements.domain.*;

/** Server-side progression, preflight and immutable upgrade contracts. */
public final class Progression {
    public static Map<String,Integer> requirements(int next) {
        return next==2?Map.of("warehouse",1,"lumber_hut",1,"mine",1,"field",1,"house",2):
            next==3?Map.of("smithy",1,"smeltery",1,"market",1,"barracks",1,"archery_range",1,"barn",1):Map.of();
    }
    public static int population(int next) {return next==2?SettlementConfig.VILLAGE_RESIDENTS.get():SettlementConfig.CITY_RESIDENTS.get();}
    public static Component requirementsText(Settlement s) {
        if(s.level>=3)return Component.translatable("ui.smartsettlements.maxTier");
        var text=Component.translatable("ui.smartsettlements.populationRequirement",s.residents.size(),population(s.level+1));
        for(var entry:requirements(s.level+1).entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            long count=s.buildings.stream().filter(b->b.complete&&b.type.equals(entry.getKey())).count();
            text.append("\n").append(Component.translatable("ui.smartsettlements.buildingRequirement",
                Component.translatable("building.smartsettlements."+entry.getKey()),count,entry.getValue()));
        }
        return text;
    }
    public static boolean eligible(Settlement s) {
        if(s.level>=3||s.residents.size()<population(s.level+1))return false;
        return requirements(s.level+1).entrySet().stream().allMatch(entry->
            s.buildings.stream().filter(b->b.complete&&b.type.equals(entry.getKey())).count()>=entry.getValue());
    }
    private static Construction.Result reject(String code) {return new Construction.Result(code,Component.translatable("message.smartsettlements.upgrade_"+code));}
    public static Construction.Result request(ServerPlayer actor,Settlement s,String id) {
        if(!s.owner.equals(actor.getUUID()))return reject("owner");
        var world=(ServerLevel)actor.level();
        if(!world.dimension().identifier().toString().equals(s.dimension))return reject("dimension");
        Building job;
        boolean createHall=false;
        if(id.equals("hall")) {
            job=s.buildings.stream().filter(b->b.type.equals("town_hall")).findFirst().orElse(null);
            if(job==null) {
                job=new Building(BuildingType.TOWN_HALL,s.x,s.y,s.z,0);job.complete=true;
                job.level=s.level;job.targetLevel=s.level;createHall=true;
            }
        } else job=s.buildings.stream().filter(b->b.id.equals(id)).findFirst().orElse(null);
        if(job==null)return reject("missing");
        if(!job.complete||job.repairing)return reject("busy");
        if(job.level>=3)return reject("max");
        var type=BuildingType.byId(job.type);if(type==null)return reject("missing");
        if(type==BuildingType.TOWN_HALL&&!eligible(s))return new Construction.Result("requirements",requirementsText(s));
        if(type!=BuildingType.TOWN_HALL&&job.level+1>s.level)return reject("settlement_level");
        String jobId=job.id;
        if(s.residents.stream().anyMatch(r->r.cargoJob.equals(jobId)&&r.builderInventory.size()>0))return reject("cargo");
        if(s.buildings.stream().filter(b->!b.complete).count()>=16)return reject("queue");
        int next=job.level+1;
        var old=BuildingTemplates.pieces(type,job.level,job.templateVersion);
        var target=BuildingTemplates.pieces(type,next,1);
        var expected=new HashMap<BlockPos,BlockState>();var origin=new BlockPos(job.x,job.y,job.z);
        for(var piece:old)expected.put(piece.at(origin),piece.state());
        // Validate every destination before reserving anything. Preserve inventoried blocks unchanged.
        for(var piece:target) {
            var at=piece.at(origin);
            if(at.getY()<world.getMinY()||at.getY()>=world.getMaxY()||!Construction.canChange(world,at,s.owner))return reject("site");
            var current=world.getBlockState(at);
            if(BuildingTemplates.matches(current,piece.state()))continue;
            var previous=expected.get(at);
            if(piece.y()<0&&!current.isFaceSturdy(world,at,net.minecraft.core.Direction.UP))return reject("site");
            if(world.getBlockEntity(at)!=null||current.getDestroySpeed(world,at)<0||!world.getFluidState(at).isEmpty()
                ||!(previous!=null&&BuildingTemplates.matches(current,previous)||SitePreparation.cover(current)))return reject("site");
        }
        int wood=SettlementConfig.price(type,next,true),stone=SettlementConfig.price(type,next,false);
        if(!Construction.affordable(s,wood,stone))return reject("materials");
        // Any unspent old credit is returned before opening the next contract.
        for(var entry:job.reserved.entrySet())if(entry.getValue()>0)s.resources.merge(entry.getKey(),entry.getValue(),Integer::sum);
        job.reserved.clear();
        job.upgradeFromLevel=job.level;job.upgradeFromVersion=job.templateVersion;
        job.templateVersion=1;job.targetLevel=next;job.total=target.size();job.progress=0;job.materialProgress=0;
        job.priceWood=wood;job.priceStone=stone;job.stagedWood=job.stagedStone=0;job.logisticsInitialized=true;
        job.workRetryAt=0;job.complete=false;job.started=false;job.status="waiting_builder";job.assignedBuilder="";
        job.preparation.clear();job.columns.clear();job.columnCursor=0;job.prepared=0;job.preparationTotal=0;
        job.foundationRemaining=0;job.prepaidFill=0;job.foundationPrepaid=false;
        Construction.reserve(s,job,wood,stone);
        if(createHall)s.buildings.add(job);
        return new Construction.Result("ok",Component.translatable("message.smartsettlements.upgrade_ok",
            Component.translatable("building.smartsettlements."+job.type),next,wood,stone));
    }
    public static boolean replaceable(ServerLevel world,Building job,BuildingTemplates.Piece piece,BlockState current) {
        if(job.upgradeFromLevel<=0)return false;
        for(var previous:BuildingTemplates.previous(job))if(previous.x()==piece.x()&&previous.y()==piece.y()&&previous.z()==piece.z())
            return BuildingTemplates.matches(current,previous.state());
        return false;
    }
    public static void finish(Settlement s,Building job,WorldState state) {
        job.level=job.targetLevel;job.upgradeFromLevel=0;
        if(job.type.equals("town_hall")) {
            s.level=job.level;
            int desired=Math.max(Construction.radius(s),SettlementConfig.tierRadius(s.level));
            // Retain circular territory: expansion cannot consume any existing settlement's land.
            for(var other:state.settlements.values())if(other!=s&&other.dimension.equals(s.dimension)) {
                int room=(int)Math.floor(Math.hypot((double)s.x-other.x,(double)s.z-other.z)-Construction.radius(other));
                desired=Math.min(desired,Math.max(Construction.radius(s),room));
            }
            s.radius=Math.max(Construction.radius(s),desired);
        }
    }
    public static double speed(Building workplace) {return 1+Math.max(0,workplace.level-1)*SettlementConfig.BUILDING_SPEED.get();}
    private Progression() {}
}
