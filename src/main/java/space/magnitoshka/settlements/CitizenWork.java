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
    private int searchCursor,fieldCursor,buildRetryCursor;
    private List<int[]> searchOffsets=List.of();
    private final ArrayDeque<BlockPos> treeLogs=new ArrayDeque<>();
    private BlockPos treeBase;
    private String treeSpecies;
    private String lastProfession="";
    public void tick(CitizenEntity citizen,ServerLevel level,SettlementData data,Settlement s,Resident r) {
        if(!r.profession.equals(lastProfession)) {
            treeLogs.clear();treeBase=null;r.taskBuilding="";lastProfession=r.profession;citizen.getNavigation().stop();
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
        var pending=s.buildings.stream().filter(b->!b.complete).toList();
        var blocked=pending.stream().filter(b->"blocked".equals(b.status)).toList();
        var active=pending.stream().filter(b->!"blocked".equals(b.status)).findFirst().orElse(null);
        var job=active;
        if(!blocked.isEmpty()&&(active==null||citizen.tickCount%100==0))job=blocked.get(Math.floorMod(buildRetryCursor++,blocked.size()));
        if(job==null) {r.workStatus="idle";r.taskBuilding="";return;}
        r.taskBuilding=job.id;
        var type=BuildingType.byId(job.type);
        if(type==null) {block(level,data,s,r,job);return;}
        BlockPos origin=new BlockPos(job.x,job.y,job.z);
        var entrance=origin.offset(0,0,-type.halfDepth-1);
        if(!approach(citizen,entrance,r,4)) {if(r.workStatus.equals("blocked"))block(level,data,s,r,job);return;}
        var pieces=BuildingTemplates.pieces(type);
        int count=Math.max(1,(int)Math.floor(SettlementConfig.BUILD_RATE.get()*r.workEfficiency()));
        boolean changed=false;
        for(int i=0;i<count&&job.progress<pieces.size();i++) {
            var piece=pieces.get(job.progress);var at=piece.at(origin);
            if(!Construction.canChange(level,at,s.owner)) {block(level,data,s,r,job);break;}
            var current=level.getBlockState(at);
            // Resume safely if the previous write was saved before its progress cursor.
            if(!BuildingTemplates.matches(current,piece.state())) {
                if(level.getBlockEntity(at)!=null || (piece.y()>=0&&!current.canBeReplaced())) {
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
            job.progress++;changed=true;r.workStatus="working";job.status="building";
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
                job.complete=true;job.status="complete";changed=true;r.workStatus="idle";
                SettlementNetwork.ownerNotice(level.getServer(),s,true,"action.smartsettlements.construction_complete",net.minecraft.network.chat.Component.translatable("building.smartsettlements."+job.type));
            }
        }
        if(changed) {citizen.swingForAttack(InteractionHand.MAIN_HAND);data.setDirty();}
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
                    if(!level.getBlockState(base).is(BlockTags.LOGS)||!level.getBlockState(base.below()).is(BlockTags.DIRT))continue;
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
                if(Construction.canChange(level,p,s.owner)&&Math.hypot((double)p.getX()-s.x,(double)p.getZ()-s.z)<=Construction.radius(s)&&level.getBlockState(p).canBeReplaced()&&level.getBlockState(p.below()).is(BlockTags.DIRT))level.setBlock(p,sapling.defaultBlockState(),3);
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
