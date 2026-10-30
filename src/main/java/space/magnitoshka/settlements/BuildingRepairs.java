package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import space.magnitoshka.settlements.domain.*;

/** Loaded-only, globally budgeted maintenance. Completed tiers/contracts are never reopened. */
public final class BuildingRepairs {
    private static final class Scan {
        Iterator<Settlement> settlements=Collections.emptyIterator();
        Settlement current;
        int building,piece;
    }
    private static final Map<MinecraftServer,Scan> SCANS=new WeakHashMap<>();
    public static boolean ensureHall(Settlement s) {
        if(s.buildings.stream().anyMatch(b->b.type.equals("town_hall")))return false;
        var hall=new Building(BuildingType.TOWN_HALL,s.x,s.y,s.z,0);
        hall.level=s.level;hall.targetLevel=s.level;hall.complete=true;hall.logisticsInitialized=true;
        s.buildings.add(hall);return true;
    }
    public static List<BuildingTemplates.Piece> pieces(Building job) {
        return BuildingTemplates.pieces(BuildingType.byId(job.type),job.level,job.templateVersion);
    }
    /** A bed is one unit: both halves are checked and placed together. */
    public static int canonical(List<BuildingTemplates.Piece> pieces,int index) {
        var piece=pieces.get(index);
        if(piece.state().getBlock() instanceof BedBlock&&piece.state().getValue(BedBlock.PART)==BedPart.HEAD) {
            var foot=new BlockPos(piece.x(),piece.y(),piece.z()).relative(piece.state().getValue(BedBlock.FACING).getOpposite());
            for(int i=0;i<pieces.size();i++) {var other=pieces.get(i);if(other.x()==foot.getX()&&other.y()==foot.getY()&&other.z()==foot.getZ())return i;}
        }
        return index;
    }
    public static List<BuildingTemplates.Piece> unit(Building job,int index) {
        var pieces=pieces(job);if(index<0||index>=pieces.size())return List.of();
        var first=pieces.get(canonical(pieces,index));
        if(!(first.state().getBlock() instanceof BedBlock))return List.of(first);
        var head=new BlockPos(first.x(),first.y(),first.z()).relative(first.state().getValue(BedBlock.FACING));
        for(var other:pieces)if(other.x()==head.getX()&&other.y()==head.getY()&&other.z()==head.getZ())return List.of(first,other);
        return List.of();
    }
    public static boolean repairable(BuildingTemplates.Piece piece) {
        // Air is a free interior, and crops belong to farmers; never erase additions or reset growth.
        return !piece.state().isAir()&&!piece.state().is(Blocks.WHEAT);
    }
    public static boolean needsRepair(ServerLevel level,Building job,List<BuildingTemplates.Piece> unit) {
        if(unit.isEmpty()||!repairable(unit.getFirst()))return false;
        boolean missing=false;var origin=new BlockPos(job.x,job.y,job.z);
        for(var piece:unit) {
            var at=piece.at(origin);if(!level.hasChunkAt(at))return false;
            var current=level.getBlockState(at);
            if(BuildingTemplates.matches(current,piece.state()))continue;
            // Nonempty replacements and liquids are player/world changes, not permission to demolish.
            if(!current.isAir()||!level.getFluidState(at).isEmpty()||level.getBlockEntity(at)!=null)return false;
            missing=true;
        }
        return missing;
    }
    public static void tick(MinecraftServer server) {
        if(!SettlementConfig.AUTO_REPAIR.get())return;
        var data=SettlementData.get(server);if(data.state.settlements.isEmpty())return;
        var scan=SCANS.computeIfAbsent(server,key->new Scan());
        // Discovery, skipped unloaded buildings and individual template checks all consume the same budget.
        for(int n=0;n<SettlementConfig.REPAIR_SCAN.get();n++) {
            if(scan.current==null) {
                try {
                    if(!scan.settlements.hasNext())scan.settlements=data.state.settlements.values().iterator();
                    if(!scan.settlements.hasNext())return;
                    scan.current=scan.settlements.next();scan.building=scan.piece=0;
                } catch(ConcurrentModificationException changed) {scan.settlements=Collections.emptyIterator();}
                continue;
            }
            var settlement=scan.current;
            if(data.state.settlements.get(settlement.id)!=settlement||scan.building>=settlement.buildings.size()) {scan.current=null;continue;}
            var job=settlement.buildings.get(scan.building);
            var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(settlement.dimension)));
            if(!job.complete||BuildingType.byId(job.type)==null||level==null||!level.hasChunkAt(new BlockPos(job.x,job.y,job.z))) {
                scan.building++;scan.piece=0;continue;
            }
            var pieces=pieces(job);
            if(scan.piece>=pieces.size()) {scan.piece=0;scan.building++;continue;}
            int index=scan.piece++;if(!repairable(pieces.get(index)))continue;
            index=canonical(pieces,index);
            if(job.repairUnits.contains(index)||!needsRepair(level,job,unit(job,index)))continue;
            if(!job.repairing) {job.repairTotal=0;job.repairUnits.clear();job.status="needs_repair";}
            job.repairing=true;job.repairUnits.add(index);job.repairUnits.sort(Integer::compareTo);job.repairTotal++;data.setDirty();
        }
    }
    public static boolean wood(List<BuildingTemplates.Piece> unit) {
        var state=unit.getFirst().state();var block=state.getBlock();
        return state.is(BlockTags.LOGS)||state.is(BlockTags.PLANKS)||state.is(BlockTags.WOODEN_FENCES)
            ||block instanceof BedBlock||block instanceof LadderBlock||block instanceof BarrelBlock
            ||state.is(Blocks.CRAFTING_TABLE)||state.is(Blocks.BOOKSHELF)||state.is(Blocks.HAY_BLOCK)
            ||state.is(Blocks.CARTOGRAPHY_TABLE)||state.is(Blocks.SMITHING_TABLE)||state.is(Blocks.TARGET);
    }
    public static int cost(List<BuildingTemplates.Piece> unit) {return wood(unit)?SettlementConfig.REPAIR_WOOD.get():SettlementConfig.REPAIR_STONE.get();}
    public static int staged(Building job,int index) {return job.repairCredit.getOrDefault(index,0);}
    public static void stage(Resident r,Building job,int index,List<BuildingTemplates.Piece> unit) {
        int count=r.builderInventory.take("repair",wood(unit)?Construction::isWood:key->key.equals("minecraft:cobblestone"),Math.max(0,cost(unit)-staged(job,index)));
        if(count>0)job.repairCredit.merge(index,count,Integer::sum);
    }
    public static int load(Settlement s,Resident r,Building job,int index) {
        BuilderAssignments.unload(s,r);r.cargoJob=job.id;
        var current=unit(job,index);boolean wood=wood(current);int needed=0;
        for(var pending:job.repairUnits) {
            var unit=unit(job,pending);
            if(!unit.isEmpty()&&wood(unit)==wood)needed+=Math.max(0,cost(unit)-staged(job,pending));
        }
        if(!wood) {
            int moved=r.builderInventory.add("minecraft:cobblestone","repair",Math.min(needed,s.resources.getOrDefault("minecraft:cobblestone",0)));
            s.withdraw("minecraft:cobblestone",moved);
        } else for(var key:s.resources.keySet().stream().sorted().toList())if(needed>0&&Construction.isWood(key)) {
            int moved=r.builderInventory.add(key,"repair",Math.min(needed,s.resources.getOrDefault(key,0)));s.withdraw(key,moved);needed-=moved;
        }
        return r.builderInventory.size();
    }
    public static void finish(Building job,Resident resident) {
        job.repairing=false;job.repairUnits.clear();job.repairTotal=0;job.workRetryAt=0;job.repairCredit.clear();job.assignedBuilder="";job.status="complete";
        resident.taskBuilding="";resident.fetching=false;resident.supplyReady=false;resident.workStatus="idle";
    }
    private BuildingRepairs() {}
}
