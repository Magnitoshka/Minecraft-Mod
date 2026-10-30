package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.Heightmap;
import space.magnitoshka.settlements.domain.*;

public final class Construction {
    public static String request(ServerPlayer player,Settlement s,String typeId,int dx,int dz) {
        var type=BuildingType.byId(typeId);
        if(type==null)return "invalid";
        var level=(ServerLevel)player.level();
        if(!level.dimension().identifier().toString().equals(s.dimension))return "dimension";
        if(Math.abs((long)dx)>radius(s)||Math.abs((long)dz)>radius(s))return "territory";
        int x=s.x+dx,z=s.z+dz;
        if(Math.hypot(Math.abs((double)dx)+type.halfWidth,Math.abs((double)dz)+type.halfDepth)>radius(s))return "territory";
        if(s.buildings.stream().filter(b->!b.complete).count()>=16)return "queue_full";
        if(Math.abs(dx)<=4+type.halfWidth+1&&Math.abs(dz)<=3+type.halfDepth+1)return "collision";
        for(var b:s.buildings) {
            var other=BuildingType.byId(b.type);
            if(other!=null&&Math.abs((long)x-b.x)<=type.halfWidth+other.halfWidth+1&&Math.abs((long)z-b.z)<=type.halfDepth+other.halfDepth+1)return "collision";
        }
        if(!level.hasChunkAt(new BlockPos(x-type.halfWidth,s.y,z-type.halfDepth))||!level.hasChunkAt(new BlockPos(x+type.halfWidth,s.y,z+type.halfDepth)))return "unloaded";
        var plan=SitePreparation.plan(level,player,type,x,z);
        if(plan==null)return "site";
        int y=plan.y();
        if(!level.hasChunkAt(new BlockPos(x,y,z-type.halfDepth-1)))return "unloaded";
        var entrance=new BlockPos(x,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z-type.halfDepth-1),z-type.halfDepth-1);
        if(Math.abs(entrance.getY()-y)>1||!walkable(level,entrance)||!reachable(level,new BlockPos(s.x,s.y,s.z-4),entrance,s))return "path";
        if(wood(s)<type.woodCost||s.resources.getOrDefault("minecraft:cobblestone",0)<type.stoneCost+plan.fill())return "materials";
        // Reserve the entire construction price atomically after every placement check passed.
        int remaining=type.woodCost;
        for(var key:s.resources.keySet().stream().sorted().toList())if(isWood(key)&&remaining>0)remaining-=s.withdraw(key,remaining);
        s.withdraw("minecraft:cobblestone",type.stoneCost+plan.fill());
        var building=new Building(type,x,y,z,BuildingTemplates.pieces(type).size());
        building.preparation=plan.edits();building.preparationTotal=plan.edits().size();building.hasStand=true;
        building.standX=entrance.getX();building.standY=entrance.getY();building.standZ=entrance.getZ();
        s.buildings.add(building);
        return "ok";
    }
    private static boolean isWood(String key) {
        var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
        return item!=null&&item.getDefaultInstance().is(ItemTags.LOGS);
    }
    private static long wood(Settlement s) {return s.resources.entrySet().stream().filter(e->isWood(e.getKey())).mapToLong(Map.Entry::getValue).sum();}
    private static boolean walkable(ServerLevel level,BlockPos feet) {
        return level.hasChunkAt(feet)&&level.getBlockState(feet.below()).isFaceSturdy(level,feet.below(),Direction.UP)
            &&level.getFluidState(feet).isEmpty()&&level.getBlockState(feet).getCollisionShape(level,feet).isEmpty()
            &&level.getBlockState(feet.above()).getCollisionShape(level,feet.above()).isEmpty();
    }
    private static boolean reachable(ServerLevel level,BlockPos from,BlockPos to,Settlement s) {
        if(!walkable(level,from))return false;
        var queue=new PriorityQueue<BlockPos>(Comparator.comparingDouble(p->p.distSqr(to)));
        var seen=new HashSet<BlockPos>();queue.add(from);seen.add(from);
        while(!queue.isEmpty()&&seen.size()<4096) {
            var p=queue.remove();if(p.equals(to))return true;
            for(var direction:Direction.Plane.HORIZONTAL)for(int dy:new int[]{0,1,-1}) {
                var next=p.relative(direction).offset(0,dy,0);
                if(Math.hypot((double)next.getX()-s.x,(double)next.getZ()-s.z)>radius(s)+1||seen.contains(next))continue;
                if(walkable(level,next)) {seen.add(next);queue.add(next);break;}
            }
        }
        return false;
    }
    public static List<BlockPos> beds(ServerLevel level,Settlement s) {
        var beds=new ArrayList<BlockPos>();
        for(int i=0;i<SettlementConfig.BEDS.get();i++)addBed(level,beds,new BlockPos(s.x-2+i*2,s.y,s.z+2));
        for(var b:s.buildings)if(b.complete&&b.type.equals("house"))for(int x:new int[]{-1,1})addBed(level,beds,new BlockPos(b.x+x,b.y,b.z+1));
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
        long count=s.buildings.stream().filter(b->b.complete&&b.type.equals("warehouse")).count();
        return (int)Math.min(Integer.MAX_VALUE,SettlementConfig.CAPACITY.get()+count*SettlementConfig.WAREHOUSE_BONUS.get());
    }
    public static void award(Resident r,int xp) {
        r.experience=(int)Math.min(Integer.MAX_VALUE,(long)r.experience+xp);
        r.level=r.experience>=1500?5:r.experience>=700?4:r.experience>=300?3:r.experience>=100?2:1;
    }
    private Construction() {}
}
