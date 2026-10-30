package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import space.magnitoshka.settlements.domain.*;

/** One bounded work step per second, per materialized citizen. Never runs on the client. */
public final class CitizenWork {
    private int searchCursor,fieldCursor;
    private List<int[]> searchOffsets=List.of();
    private final ArrayDeque<BlockPos> treeLogs=new ArrayDeque<>();
    private BlockPos treeBase;
    private String treeSpecies;
    private String lastProfession="";
    public void tick(CitizenEntity citizen,ServerLevel level,SettlementData data,Settlement s,Resident r) {
        if(!r.profession.equals(lastProfession)) {
            treeLogs.clear();treeBase=null;lastProfession=r.profession;citizen.getNavigation().stop();
            var tool=switch(r.profession) {case "builder","lumberjack"->Items.STONE_AXE;case "miner"->Items.STONE_PICKAXE;case "farmer"->Items.STONE_HOE;default->Items.AIR;};
            citizen.setItemSlot(EquipmentSlot.MAINHAND,tool==Items.AIR?ItemStack.EMPTY:new ItemStack(tool));
        }
        if(r.workEfficiency()<=0) {r.workStatus="starving";citizen.getNavigation().stop();return;}
        switch(r.profession) {
            case "builder" -> build(citizen,level,data,s,r);
            case "lumberjack","miner","farmer" -> produce(citizen,level,data,s,r);
            default -> r.workStatus="idle";
        }
    }
    private void build(CitizenEntity citizen,ServerLevel level,SettlementData data,Settlement s,Resident r) {
        if(BuilderAssignments.reconcile(s))data.setDirty();
        if(r.taskBuilding.isEmpty()&&!r.cargoJob.isEmpty()) {
            var previous=s.buildings.stream().filter(b->b.id.equals(r.cargoJob)).findFirst().orElse(null);
            if(r.builderInventory.size()>0&&!returnToHall(citizen,s,previous,r)) {r.workStatus="fetching";return;}
            BuilderAssignments.unload(s,r);data.setDirty();
        }
        var job=BuilderAssignments.claim(s,r);
        if(job==null) {r.workStatus="idle";return;}
        BuilderLogistics.initialize(job);data.setDirty();
        if(!r.supplyReady||r.fetching) {supplies(citizen,level,data,s,r,job);return;}
        var type=BuildingType.byId(job.type);
        if(type==null) {block(level,data,s,r,job);return;}
        BlockPos origin=new BlockPos(job.x,job.y,job.z);
        var entrance=job.hasStand?new BlockPos(job.standX,job.standY,job.standZ):origin.offset(0,0,-type.halfDepth-1);
        if(job.columnCursor<job.columns.size()) {prepareColumns(citizen,level,data,s,r,job);return;}
        if(!(job.access.isEmpty()?approach(citizen,entrance,r,4):approachAccess(citizen,s,job,job.access.size()-1,r))) {if(r.workStatus.equals("blocked"))block(level,data,s,r,job);return;}
        if(job.prepared<job.preparation.size()) {prepare(citizen,level,data,s,r,job);return;}
        if(!r.supplyStage.equals("base")) {r.fetching=true;supplies(citizen,level,data,s,r,job);return;}
        var pieces=BuildingTemplates.pieces(type);
        int count=Math.max(1,(int)Math.floor(SettlementConfig.BUILD_RATE.get()*r.workEfficiency()));
        boolean changed=false;
        for(int i=0;i<count&&job.progress<pieces.size();i++) {
            var piece=pieces.get(job.progress);var at=piece.at(origin);
            if(!BuilderLogistics.hasBase(r,job)) {r.fetching=true;supplies(citizen,level,data,s,r,job);break;}
            if(!Construction.canChange(level,at,s.owner)) {block(level,data,s,r,job);break;}
            var current=level.getBlockState(at);
            // Resume safely if the previous write was saved before its progress cursor.
            if(!BuildingTemplates.matches(current,piece.state())) {
                if(level.getBlockEntity(at)!=null || current.getDestroySpeed(level,at)<0 || !level.getFluidState(at).isEmpty() || (piece.y()>=0&&!SitePreparation.cover(current))) {
                    block(level,data,s,r,job);break;
                }
                if(piece.y()<0 && (!current.isFaceSturdy(level,at,net.minecraft.core.Direction.UP)
                    ||!level.getFluidState(at).isEmpty())) {block(level,data,s,r,job);break;}
                // Pair halves are followed immediately; no neighbor notifications until both exist.
                if(!level.setBlock(at,piece.state(),piece.state().getBlock() instanceof BedBlock?2:3)) {
                    block(level,data,s,r,job);break;
                }
                if(!job.started) {
                    job.started=true;
                    SettlementNetwork.ownerNotice(level.getServer(),s,true,"action.smartsettlements.construction_started",net.minecraft.network.chat.Component.translatable("building.smartsettlements."+job.type));
                }
            }
            BuilderLogistics.spendBase(r,job);job.progress++;changed=true;r.workStatus="working";job.status="building";
            Construction.award(r,SettlementConfig.BUILDER_XP.get());
        }
        if(job.progress>=pieces.size()) {
            int missing=-1;
            for(int i=0;i<pieces.size();i++) {
                var at=pieces.get(i).at(origin);
                if(!level.hasChunkAt(at)||!BuildingTemplates.matches(level.getBlockState(at),pieces.get(i).state())) {missing=i;break;}
            }
            if(missing>=0) {job.progress=missing;block(level,data,s,r,job);}
            else {
                job.complete=true;job.assignedBuilder="";r.taskBuilding="";job.status="complete";job.preparation.clear();job.columns.clear();changed=true;r.workStatus="idle";
                SettlementNetwork.ownerNotice(level.getServer(),s,true,"action.smartsettlements.construction_complete",net.minecraft.network.chat.Component.translatable("building.smartsettlements."+job.type));
            }
        }
        if(changed) {citizen.swingForAttack(InteractionHand.MAIN_HAND);data.setDirty();}
    }
    private boolean prepareColumns(CitizenEntity citizen,ServerLevel level,SettlementData data,Settlement s,Resident r,Building job) {
        int budget=Math.max(1,(int)Math.floor(SettlementConfig.BUILD_RATE.get()*r.workEfficiency()));
        while(budget>0&&job.columnCursor<job.columns.size()) {
            var column=job.columns.get(job.columnCursor);
            if(column.runIndex>=column.clear.size()&&column.fillY>=column.y) {job.columnCursor++;data.setDirty();continue;}
            if(!approachAccess(citizen,s,job,column.workIndex,r)) {
                if(r.workStatus.equals("blocked"))block(level,data,s,r,job);return false;
            }
            boolean fill=column.runIndex>=column.clear.size();
            var run=fill?null:column.clear.get(column.runIndex);
            var at=new BlockPos(column.x,fill?column.fillY:column.clearY,column.z);
            if(!Construction.canChange(level,at,s.owner)||level.getBlockEntity(at)!=null) {blockAt(level,data,s,r,job,at);return false;}
            var state=level.getBlockState(at);
            boolean done=fill?state.is(Blocks.COBBLESTONE):state.isAir();
            if(!done) {
                while(fill&&column.fillRun<column.fill.size()-1&&column.fillY>column.fill.get(column.fillRun).top())column.fillRun++;
                String expected=fill?column.fill.get(column.fillRun).block():run.block();
                boolean original=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(expected)
                    ||(fill&&state.is(BlockTags.ICE)&&(expected.equals("minecraft:water")||expected.equals("minecraft:air")));
                boolean safe=fill?SitePreparation.fillable(state)&&SitePreparation.stable(level,at.below())
                    &&(original||state.isAir()||state.is(Blocks.WATER))
                    &&(level.getFluidState(at).isEmpty()||level.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER))
                    :SitePreparation.removable(state)&&level.getFluidState(at).isEmpty()
                        &&original;
                if(!safe||state.getDestroySpeed(level,at)<0) {blockAt(level,data,s,r,job,at);return false;}
                if(fill&&!BuilderLogistics.hasFill(r,job)) {r.fetching=true;supplies(citizen,level,data,s,r,job);return false;}
                // Direct replacement prevents ice turning into water, unlike breaking the ice block.
                if(!level.setBlock(at,fill?Blocks.COBBLESTONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3)) {blockAt(level,data,s,r,job,at);return false;}
                if(fill)BuilderLogistics.spendFill(r,job);
                if(!fill&&state.is(BlockTags.LOGS))s.deposit(BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).toString(),1,Construction.capacity(s));
                preparationNotice(level,s,job);
            }
            if(fill) {column.fillY++;job.foundationRemaining=Math.max(0,job.foundationRemaining-1);}
            else if(--column.clearY<run.bottom()) {
                column.runIndex++;
                if(column.runIndex<column.clear.size())column.clearY=column.clear.get(column.runIndex).top();
            }
            job.prepared++;budget--;job.status="leveling";r.workStatus="leveling";data.setDirty();
            Construction.award(r,SettlementConfig.BUILDER_XP.get());citizen.swingForAttack(InteractionHand.MAIN_HAND);
        }
        return job.columnCursor>=job.columns.size();
    }
    private boolean supplies(CitizenEntity citizen,ServerLevel level,SettlementData data,Settlement s,Resident r,Building job) {
        r.fetching=true;
        if(!returnToHall(citizen,s,job,r)) {r.workStatus="fetching";job.status="fetching";return false;}
        var barrel=new BlockPos(s.x+3,s.y,s.z);
        if(!level.getBlockState(barrel).is(Blocks.BARREL)) {
            r.workStatus="materials";job.status="materials";data.setDirty();return false;
        }
        boolean foundation=BuilderLogistics.hasFoundationWork(job);
        int loaded=BuilderLogistics.load(s,r,job,foundation);
        r.supplyStage=foundation?"foundation":"base";
        if(loaded==0&&(BuilderLogistics.hasFoundationWork(job)||!BuilderLogistics.hasBase(r,job))) {
            boolean notify=!job.status.equals("materials");r.workStatus="materials";job.status="materials";data.setDirty();
            if(notify)SettlementNetwork.ownerNotice(level.getServer(),s,false,"action.smartsettlements.builder_materials",
                net.minecraft.network.chat.Component.translatable("building.smartsettlements."+job.type));
            return false;
        }
        r.supplyReady=true;r.fetching=false;r.workStatus="loading";job.status="loading";data.setDirty();
        if(loaded>0)SettlementNetwork.ownerNotice(level.getServer(),s,true,"action.smartsettlements.builder_loaded",r.name,loaded);
        return false;
    }
    private boolean returnToHall(CitizenEntity citizen,Settlement s,Building job,Resident r) {
        var storage=new BlockPos(s.x+2,s.y,s.z);
        if(citizen.distanceToSqr(storage.getX()+.5,storage.getY(),storage.getZ()+.5)<=4)return true;
        boolean inside=Math.abs(citizen.getX()-(s.x+.5))<3.5&&Math.abs(citizen.getZ()-(s.z+.5))<2.5&&Math.abs(citizen.getY()-s.y)<2;
        if(inside)return approach(citizen,storage,r,2);
        if(job!=null&&!job.access.isEmpty()) {
            int closest=0;double distance=Double.MAX_VALUE;
            for(int i=0;i<job.access.size();i++) {
                var p=job.access.get(i);double d=citizen.distanceToSqr(p.x()+.5,p.y(),p.z()+.5);
                if(d<distance) {closest=i;distance=d;}
            }
            var start=job.access.getFirst();
            if(citizen.distanceToSqr(start.x()+.5,start.y(),start.z()+.5)>9) {
                var back=job.access.get(distance>16?closest:Math.max(0,closest-4));
                approach(citizen,new BlockPos(back.x(),back.y(),back.z()),r,1);return false;
            }
        }
        return approach(citizen,storage,r,2);
    }
    private void preparationNotice(ServerLevel level,Settlement s,Building job) {
        if(job.preparationStarted)return;
        job.preparationStarted=true;
        SettlementNetwork.ownerNotice(level.getServer(),s,true,"action.smartsettlements.preparation_started",
            net.minecraft.network.chat.Component.translatable("building.smartsettlements."+job.type));
    }
    private boolean approachAccess(CitizenEntity citizen,Settlement s,Building job,int destination,Resident r) {
        if(job.access.isEmpty())return approach(citizen,new BlockPos(job.standX,job.standY,job.standZ),r,4);
        destination=Math.clamp(destination,0,job.access.size()-1);
        var target=job.access.get(destination);
        if(citizen.distanceToSqr(target.x()+.5,target.y(),target.z()+.5)<=9)return true;
        int closest=0;double distance=Double.MAX_VALUE;
        for(int i=0;i<=destination;i++) {
            var p=job.access.get(i);double current=citizen.distanceToSqr(p.x()+.5,p.y(),p.z()+.5);
            if(current<distance) {closest=i;distance=current;}
        }
        if(distance>16) {
            // A builder returning from a different site follows that site's finished access back home.
            for(var other:s.buildings)if(other!=job&&!other.access.isEmpty()) {
                int limit=other.columns.isEmpty()?other.access.size():Math.min(other.access.size(),other.columnCursor+1);
                for(int i=1;i<limit;i++) {
                    var p=other.access.get(i);
                    if(citizen.distanceToSqr(p.x()+.5,p.y(),p.z()+.5)>9)continue;
                    var back=other.access.get(Math.max(0,i-4));
                    approach(citizen,new BlockPos(back.x(),back.y(),back.z()),r,1);return false;
                }
            }
        }
        // Short waypoints avoid the mob's finite path search range, even on a perfectly flat world.
        var next=job.access.get(distance>16?closest:Math.min(destination,closest+4));
        approach(citizen,new BlockPos(next.x(),next.y(),next.z()),r,1);
        return false;
    }
    private void prepare(CitizenEntity citizen,ServerLevel level,SettlementData data,Settlement s,Resident r,Building job) {
        int count=Math.max(1,(int)Math.floor(SettlementConfig.BUILD_RATE.get()*r.workEfficiency()));
        for(int i=0;i<count&&job.prepared<job.preparation.size();i++) {
            var edit=job.preparation.get(job.prepared);var at=new BlockPos(edit.x,edit.y,edit.z);
            if(!Construction.canChange(level,at,s.owner)||level.getBlockEntity(at)!=null||!level.getFluidState(at).isEmpty()) {
                block(level,data,s,r,job);return;
            }
            var state=level.getBlockState(at);
            boolean alreadyDone=edit.fill?state.is(Blocks.COBBLESTONE):state.isAir();
            if(!alreadyDone) {
                boolean safe=edit.fill?state.canBeReplaced()&&level.getBlockState(at.below()).isFaceSturdy(level,at.below(),net.minecraft.core.Direction.UP):SitePreparation.removable(state)
                    &&BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().equals(edit.expected);
                if(edit.fill&&!BuilderLogistics.hasFill(r,job)) {r.fetching=true;supplies(citizen,level,data,s,r,job);return;}
                if(!safe||!level.setBlock(at,edit.fill?Blocks.COBBLESTONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3)) {
                    block(level,data,s,r,job);return;
                }
                if(edit.fill)BuilderLogistics.spendFill(r,job);
                if(!edit.fill&&state.is(BlockTags.LOGS)) {
                    var key=BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).toString();
                    s.deposit(key,1,Construction.capacity(s));
                }
                if(!job.preparationStarted) {
                    job.preparationStarted=true;
                    SettlementNetwork.ownerNotice(level.getServer(),s,true,"action.smartsettlements.preparation_started",
                        net.minecraft.network.chat.Component.translatable("building.smartsettlements."+job.type));
                }
            }
            job.prepared++;r.workStatus="leveling";job.status="leveling";
            Construction.award(r,SettlementConfig.BUILDER_XP.get());data.setDirty();
            citizen.swingForAttack(InteractionHand.MAIN_HAND);
        }
    }
    private void blockAt(ServerLevel level,SettlementData data,Settlement s,Resident r,Building job,BlockPos at) {
        boolean notify=!job.status.equals("blocked");job.status="blocked";r.workStatus="blocked";data.setDirty();
        if(notify)SettlementNetwork.ownerNotice(level.getServer(),s,false,"action.smartsettlements.construction_blocked_at",
            net.minecraft.network.chat.Component.translatable("building.smartsettlements."+job.type),level.getBlockState(at).getBlock().getName(),
            at.getX(),at.getY(),at.getZ());
    }
    private void block(ServerLevel level,SettlementData data,Settlement s,Resident r,Building job) {
        boolean newlyBlocked=!"blocked".equals(job.status);
        r.workStatus="blocked";job.status="blocked";data.setDirty();
        if(newlyBlocked)SettlementNetwork.ownerNotice(level.getServer(),s,false,"action.smartsettlements.construction_blocked",net.minecraft.network.chat.Component.translatable("building.smartsettlements."+job.type));
    }
    private void produce(CitizenEntity citizen,ServerLevel level,SettlementData data,Settlement s,Resident r) {
        var workplace=s.buildings.stream().filter(b->b.complete&&b.id.equals(r.workplace)).findFirst().orElse(null);
        String required=switch(r.profession){case "lumberjack"->"lumber_hut";case "miner"->"mine";default->"field";};
        if(workplace==null||!workplace.type.equals(required)) {r.workStatus="no_workplace";return;}
        if(s.stored()>=Construction.capacity(s)) {r.workStatus="full";return;}
        int seconds=switch(r.profession){case "lumberjack"->SettlementConfig.LUMBER_SECONDS.get();case "miner"->SettlementConfig.MINE_SECONDS.get();default->SettlementConfig.FARM_SECONDS.get();};
        var entrance=new BlockPos(workplace.x,workplace.y,workplace.z-BuildingType.byId(workplace.type).halfDepth-1);
        if(!r.profession.equals("lumberjack")&&!approach(citizen,entrance,r,4))return;
        r.workTicks++;data.setDirty();
        if(r.workTicks<Math.max(1,(int)Math.ceil(seconds/r.workEfficiency()))) {r.workStatus="working";return;}
        switch(r.profession) {
            case "miner" -> {
                        int roll=citizen.getRandom().nextInt(100);
                String key=roll<70?"minecraft:cobblestone":roll<90?"minecraft:coal":"minecraft:iron_ore";
                if(s.deposit(key,1,Construction.capacity(s))>0) {
                    r.workTicks=0;r.workStatus="working";Construction.award(r,SettlementConfig.WORK_XP.get());
                    citizen.swingForAttack(InteractionHand.MAIN_HAND);data.setDirty();
                } else r.workStatus="full";
            }
            case "lumberjack" -> lumber(citizen,level,data,s,r,workplace);
            case "farmer" -> farm(citizen,level,data,s,r,workplace);
        }
    }
    private void lumber(CitizenEntity citizen,ServerLevel level,SettlementData data,Settlement s,Resident r,Building workplace) {
        if(treeLogs.isEmpty()) {
            int radius=SettlementConfig.WORK_RADIUS.get(),side=radius*2+1;
            if(searchOffsets.size()!=side*side) {
                var offsets=new ArrayList<int[]>();
                for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++)offsets.add(new int[]{dx,dz});
                offsets.sort(Comparator.comparingInt(p->p[0]*p[0]+p[1]*p[1]));searchOffsets=offsets;searchCursor=0;
            }
            // Search nearest trunks first; each step checks at most 96 bounded columns.
            for(int n=0;n<96&&treeLogs.isEmpty();n++) {
                int[] offset=searchOffsets.get(Math.floorMod(searchCursor++,searchOffsets.size()));
                int x=workplace.x+offset[0],z=workplace.z+offset[1];
                if(Math.hypot((double)x-s.x,(double)z-s.z)>Construction.radius(s))continue;
                var column=new BlockPos(x,workplace.y,z);if(!level.hasChunkAt(column))continue;
                int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
                for(int y=Math.max(level.getMinY()+1,top-24);y<=top;y++) {
                    var base=new BlockPos(x,y,z);
                    if(!level.getBlockState(base).is(BlockTags.LOGS)||!level.getBlockState(base.below()).is(BlockTags.SUBSTRATE_OVERWORLD))continue;
                    if(!hasLeaves(level,base))continue;
                    collectTree(level,base,s);break;
                }
            }
            if(treeLogs.isEmpty()) {r.workStatus="no_trees";r.workTicks=0;return;}
        }
        var target=treeLogs.peek();
        if(!level.hasChunkAt(target)) {r.workStatus="blocked";return;}
        // Chop from the ground next to the trunk; only the connected, bounded tree is affected.
        if(!approach(citizen,treeBase,r,3.5))return;
        var state=level.getBlockState(target);
        if(!state.is(BlockTags.LOGS)) {treeLogs.remove();return;}
        if(!Construction.canChange(level,target,s.owner)) {r.workStatus="blocked";treeLogs.clear();return;}
        if(level.destroyBlock(target,false,citizen)) {
            String key=BuiltInRegistries.ITEM.getKey(state.getBlock().asItem()).toString();
            s.deposit(key,1,Construction.capacity(s));treeLogs.remove();r.workTicks=0;
            r.workStatus="working";Construction.award(r,SettlementConfig.WORK_XP.get());
            citizen.swingForAttack(InteractionHand.MAIN_HAND);data.setDirty();
            if(treeLogs.isEmpty())replant(level,s);
        } else r.workStatus="blocked";
    }
    private boolean hasLeaves(ServerLevel level,BlockPos base) {
        for(int dy=2;dy<=24;dy+=2)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
            var p=base.offset(x,dy,z);
            if(level.hasChunkAt(p)&&level.getBlockState(p).is(BlockTags.LEAVES))return true;
        }
        return false;
    }
    private void collectTree(ServerLevel level,BlockPos base,Settlement s) {
        treeBase=base;treeSpecies=BuiltInRegistries.BLOCK.getKey(level.getBlockState(base).getBlock()).toString();
        var queue=new ArrayDeque<BlockPos>();var seen=new HashSet<BlockPos>();var logs=new ArrayList<BlockPos>();queue.add(base);seen.add(base);
        while(!queue.isEmpty()&&logs.size()<64) {
            var p=queue.removeFirst();
            if(!level.hasChunkAt(p)||!BuiltInRegistries.BLOCK.getKey(level.getBlockState(p).getBlock()).toString().equals(treeSpecies))continue;
            logs.add(p);
            for(var direction:net.minecraft.core.Direction.values()) {
                var next=p.relative(direction);
                if(next.getY()<base.getY()||next.getY()>base.getY()+24||Math.abs(next.getX()-base.getX())>4||Math.abs(next.getZ()-base.getZ())>4
                    ||Math.hypot((double)next.getX()-s.x,(double)next.getZ()-s.z)>Construction.radius(s))continue;
                if(seen.add(next))queue.add(next);
            }
        }
        logs.sort(Comparator.<BlockPos>comparingInt(p->p.getY()).reversed());treeLogs.addAll(logs);
    }
    private void replant(ServerLevel level,Settlement s) {
        if(treeBase==null||treeSpecies==null||!level.getBlockState(treeBase).canBeReplaced())return;
        String key=treeSpecies.replace("_log","_sapling");
        if(treeSpecies.endsWith(":mangrove_log"))key="minecraft:mangrove_propagule";
        var sapling=BuiltInRegistries.BLOCK.getValue(Identifier.parse(key));
        if(sapling==null||sapling==Blocks.AIR)return;
        if(!Construction.canChange(level,treeBase,s.owner))return;
        level.setBlock(treeBase,sapling.defaultBlockState(),3);
        if(treeSpecies.endsWith(":dark_oak_log")||treeSpecies.endsWith(":pale_oak_log")) {
            for(int x=0;x<2;x++)for(int z=0;z<2;z++) {
                var p=treeBase.offset(x,0,z);
                if(Construction.canChange(level,p,s.owner)&&Math.hypot((double)p.getX()-s.x,(double)p.getZ()-s.z)<=Construction.radius(s)&&level.getBlockState(p).canBeReplaced()&&level.getBlockState(p.below()).is(BlockTags.SUBSTRATE_OVERWORLD))level.setBlock(p,sapling.defaultBlockState(),3);
            }
        }
    }
    private void farm(CitizenEntity citizen,ServerLevel level,SettlementData data,Settlement s,Resident r,Building field) {
        boolean changed=false;
        for(int i=0;i<4;i++) {
            int slot=Math.floorMod(fieldCursor++,25),x=slot%5-2,z=slot/5-2;
            if(x==0&&z==0)continue;
            var pos=new BlockPos(field.x+x,field.y,field.z+z);
            if(!Construction.canChange(level,pos,s.owner))continue;
            var state=level.getBlockState(pos);
            if(state.isAir()&&level.getBlockState(pos.below()).is(Blocks.FARMLAND)) {level.setBlock(pos,Blocks.WHEAT.defaultBlockState(),3);changed=true;}
            else if(state.is(Blocks.WHEAT)) {
                int age=state.getValue(BlockStateProperties.AGE_7);
                if(age==7) {
                    if(s.stored()>=Construction.capacity(s)) {r.workStatus="full";break;}
                    if(level.setBlock(pos,state.setValue(BlockStateProperties.AGE_7,0),3)) {
                        s.deposit("minecraft:wheat",1,Construction.capacity(s));
                        Construction.award(r,SettlementConfig.WORK_XP.get());changed=true;
                    }
                } else if(level.setBlock(pos,state.setValue(BlockStateProperties.AGE_7,age+1),3))changed=true;
            }
        }
        if(s.resources.getOrDefault("minecraft:wheat",0)>=3) {
            s.withdraw("minecraft:wheat",3);s.deposit("minecraft:bread",1,Construction.capacity(s));changed=true;
        }
        r.workTicks=0;r.workStatus=changed?"working":"blocked";
        if(changed) {citizen.swingForAttack(InteractionHand.MAIN_HAND);data.setDirty();}
    }
    private boolean approach(CitizenEntity citizen,BlockPos target,Resident r,double range) {
        if(citizen.distanceToSqr(target.getX()+.5,target.getY(),target.getZ()+.5)<=range*range)return true;
        if(!citizen.getNavigation().isDone() || !NavigationBudget.take(citizen.level().getServer())) {r.workStatus="walking";return false;}
        boolean moving=citizen.getNavigation().moveTo(target.getX()+.5,target.getY(),target.getZ()+.5,.85);
        r.workStatus=moving?"walking":"blocked";return false;
    }
}
