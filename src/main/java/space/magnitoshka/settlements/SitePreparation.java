package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import space.magnitoshka.settlements.domain.*;

/** Complete preflight; compact persisted runs have no artificial excavation/fill depth limit. */
public final class SitePreparation {
    public record Plan(int y,int steps,int fill,List<SiteColumn> columns,List<SitePoint> access) {}
    public record Result(Plan plan,String problem,BlockPos at) {
        static Result fail(String problem,BlockPos at) {return new Result(null,problem,at);}
    }
    public static Result plan(ServerLevel level,ServerPlayer player,Settlement settlement,BuildingType type,int x,int z) {
        int y=localHeight(level,x,z);
        if(y-2<level.getMinY()||y+5>=level.getMaxY())return Result.fail("height",new BlockPos(x,y,z));
        var entrance=new BlockPos(x,y,z-type.halfDepth-1);
        var access=access(level,settlement,type,x,z,entrance);
        if(access.isEmpty())return Result.fail("access",entrance);
        var columns=new ArrayList<SiteColumn>();
        // Access first, from the hall outward. Each work position is on the preceding finished column.
        for(int i=0;i<access.size();i++) {
            var point=access.get(i);var column=new SiteColumn(point.x(),point.y(),point.z(),Math.max(0,i-1));
            var error=column(level,player,column,false);
            if(error!=null)return error;
            columns.add(column);
        }
        for(int dx=-type.halfWidth;dx<=type.halfWidth;dx++)for(int dz=-type.halfDepth;dz<=type.halfDepth;dz++) {
            var column=new SiteColumn(x+dx,y,z+dz,access.size()-1);
            var error=column(level,player,column,type==BuildingType.FIELD);
            if(error!=null)return error;
            columns.add(column);
        }
        int steps=0,fill=0;
        for(var column:columns) {
            for(var run:column.clear)steps+=run.top()-run.bottom()+1;
            int count=Math.max(0,column.y-column.fillY);steps+=count;fill+=count;
        }
        return new Result(new Plan(y,steps,fill,columns,access),"",entrance);
    }
    private static int localHeight(ServerLevel level,int x,int z) {
        int y=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z);
        // A canopy/trunk is not the chosen ground surface.
        while(y>level.getMinY()+1) {
            var state=level.getBlockState(new BlockPos(x,y-1,z));
            if(!state.is(BlockTags.LOGS)&&!state.is(BlockTags.LEAVES)&&!cover(state))break;
            y--;
        }
        return y;
    }
    private static Result column(ServerLevel level,ServerPlayer player,SiteColumn column,boolean field) {
        var floor=new BlockPos(column.x,column.y-1,column.z);
        if(!level.hasChunkAt(floor))return Result.fail("unloaded",floor);
        int top=Math.max(column.y+4,level.getHeight(Heightmap.Types.WORLD_SURFACE,column.x,column.z)-1);
        if(top>=level.getMaxY())return Result.fail("height",floor.above());
        // Include all original non-air runs; never allocate one edit per depth block.
        boolean logRun=false;
        for(int h=top;h>=column.y;h--) {
            var pos=new BlockPos(column.x,h,column.z);var error=permission(level,player,pos,false);
            if(error!=null)return error;
            var state=level.getBlockState(pos);
            if(!removable(state))return Result.fail("structure",pos);
            if(state.is(BlockTags.LOGS)&&!logRun&&!naturalTreeAt(level,pos))return Result.fail("structure",pos);
            logRun=state.is(BlockTags.LOGS);
            if(!state.isAir()) {
                String block=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                int last=column.clear.size()-1;
                if(last>=0&&column.clear.get(last).bottom()==h+1&&column.clear.get(last).block().equals(block)) {
                    var old=column.clear.get(last);column.clear.set(last,new SiteRun(h,old.top(),block));
                } else column.clear.add(new SiteRun(h,h,block));
            }
        }
        column.clearY=column.clear.isEmpty()?column.y:column.clear.getFirst().top();
        if(level.getBlockState(floor).getDestroySpeed(level,floor)<0)return Result.fail("unbreakable",floor);
        int support=column.y-1;
        if(field&&!stable(level,new BlockPos(column.x,support-1,column.z)))support--;
        while(support>=level.getMinY()) {
            var pos=new BlockPos(column.x,support,column.z);var error=permission(level,player,pos,true);
            if(error!=null)return error;
            var state=level.getBlockState(pos);
            if(stable(level,pos))break;
            if(!fillable(state))return Result.fail("structure",pos);
            support--;
        }
        if(support<level.getMinY())return Result.fail("support",floor);
        // The top foundation also gets preflighted when the field's support starts below it.
        for(int h=support+1;h<column.y;h++) {
            var pos=new BlockPos(column.x,h,column.z);var error=permission(level,player,pos,true);
            if(error!=null)return error;
            var state=level.getBlockState(pos);
            if(!fillable(state)||(state.is(BlockTags.LOGS)&&!naturalTreeAt(level,pos)))return Result.fail("structure",pos);
            String block=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            int last=column.fill.size()-1;
            if(last>=0&&column.fill.get(last).block().equals(block)) {
                var old=column.fill.get(last);column.fill.set(last,new SiteRun(old.bottom(),h,block));
            } else column.fill.add(new SiteRun(h,h,block));
        }
        column.fillY=support+1;
        return null;
    }
    private static Result permission(ServerLevel level,ServerPlayer player,BlockPos pos,boolean foundation) {
        if(!level.getWorldBorder().isWithinBounds(pos)||!level.mayInteract(player,pos))return Result.fail("protected",pos);
        if(level.getBlockEntity(pos)!=null)return Result.fail("structure",pos);
        var state=level.getBlockState(pos);
        if(!state.isAir()&&state.getDestroySpeed(level,pos)<0) {
            // Bedrock may support the foundation, but is never excavated/replaced.
            if(!foundation||!stable(level,pos))return Result.fail("unbreakable",pos);
        }
        if(!level.getFluidState(pos).isEmpty()&&!(foundation&&level.getFluidState(pos).is(FluidTags.WATER)))return Result.fail("liquid",pos);
        return null;
    }
    public static boolean stable(ServerLevel level,BlockPos pos) {
        var state=level.getBlockState(pos);
        return level.getFluidState(pos).isEmpty()&&!state.is(BlockTags.ICE)&&!state.is(BlockTags.SNOW)
            &&!state.is(BlockTags.LOGS)&&!state.is(BlockTags.LEAVES)&&!state.is(Blocks.POWDER_SNOW)
            &&(removable(state)||state.is(Blocks.COBBLESTONE)||state.getDestroySpeed(level,pos)<0)
            &&state.isFaceSturdy(level,pos,Direction.UP);
    }
    /** Shared with founding, previews and template placement; flowers are not necessarily replaceable. */
    public static boolean cover(BlockState state) {
        return state.canBeReplaced()||state.is(BlockTags.FLOWERS)||state.is(BlockTags.REPLACEABLE)
            ||state.getBlock() instanceof BushBlock||state.is(BlockTags.SNOW)||state.is(Blocks.POWDER_SNOW);
    }
    public static boolean removable(BlockState state) {
        if(state.is(BlockTags.LEAVES))return naturalLeaves(state);
        return cover(state)||terrain(state)
            ||state.is(BlockTags.LOGS)||state.is(BlockTags.ICE)||state.is(Blocks.GRAVEL)||state.is(Blocks.MOSS_BLOCK);
    }
    private static boolean terrain(BlockState state) {
        return state.is(BlockTags.SUBSTRATE_OVERWORLD)||state.is(BlockTags.SAND)||state.is(BlockTags.BASE_STONE_OVERWORLD)
            ||state.is(BlockTags.BASE_STONE_NETHER)||state.is(BlockTags.TERRACOTTA)||state.is(BlockTags.ORES)
            ||state.is(Blocks.SANDSTONE)||state.is(Blocks.RED_SANDSTONE)||state.is(Blocks.CLAY)
            ||state.is(Blocks.CALCITE)||state.is(Blocks.TUFF)||state.is(Blocks.DRIPSTONE_BLOCK)
            ||state.is(Blocks.MUD)||state.is(Blocks.MUDDY_MANGROVE_ROOTS)||state.is(Blocks.END_STONE)
            ||state.is(Blocks.SOUL_SAND)||state.is(Blocks.SOUL_SOIL);
    }
    public static boolean fillable(BlockState state) {
        return removable(state)||state.is(Blocks.WATER)||state.is(Blocks.COBBLESTONE);
    }
    private static boolean naturalTreeAt(ServerLevel level,BlockPos at) {
        while(at.getY()+1<level.getMaxY()&&level.getBlockState(at.above()).is(BlockTags.LOGS))at=at.above();
        return naturalTreeColumn(level,at.getX(),at.getZ(),at.getY());
    }
    private static boolean naturalTreeColumn(ServerLevel level,int x,int z,int top) {
        int crown=-1,root=-1;
        for(int h=top;h>=level.getMinY();h--) {
            var state=level.getBlockState(new BlockPos(x,h,z));
            if(!state.is(BlockTags.LOGS)) {
                if(root>=0)return state.is(BlockTags.SUBSTRATE_OVERWORLD)&&hasCrown(level,x,crown,z);
                if(!state.isAir()&&!cover(state)&&!naturalLeaves(state))return true;
                continue;
            }
            String name=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
            if(!name.endsWith("_log")||name.startsWith("stripped_")||!state.hasProperty(RotatedPillarBlock.AXIS)
                ||state.getValue(RotatedPillarBlock.AXIS)!=Direction.Axis.Y)return false;
            if(crown<0)crown=h;root=h;
        }
        return crown<0;
    }
    private static boolean hasCrown(ServerLevel level,int x,int y,int z) {
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=0;dy<=2;dy++) {
            var pos=new BlockPos(x+dx,y+dy,z+dz);
            if(level.hasChunkAt(pos)&&naturalLeaves(level.getBlockState(pos)))return true;
        }
        return false;
    }
    private static boolean naturalLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES)&&state.hasProperty(LeavesBlock.PERSISTENT)&&!state.getValue(LeavesBlock.PERSISTENT);
    }
    /** Geometry search avoids the hall and all ordered building footprints, not uneven natural terrain. */
    private static List<SitePoint> access(ServerLevel level,Settlement s,BuildingType type,int x,int z,BlockPos entrance) {
        var start=new BlockPos(s.x,s.y,s.z-4);
        var queue=new PriorityQueue<BlockPos>(Comparator.comparingDouble(p->p.distSqr(entrance)));
        var previous=new HashMap<BlockPos,BlockPos>();previous.put(start,start);queue.add(start);
        while(!queue.isEmpty()) {
            var p=queue.remove();
            if(p.getX()==entrance.getX()&&p.getZ()==entrance.getZ()) {
                var result=new ArrayList<SitePoint>();
                while(!p.equals(start)) {result.add(new SitePoint(p.getX(),s.y,p.getZ()));p=previous.get(p);}
                result.add(new SitePoint(start.getX(),s.y,start.getZ()));Collections.reverse(result);
                if(!lengthenForGrades(result,level,s,type,x,z,entrance.getY()))return List.of();
                // Existing paths are fixed anchors. Interpolate a walkable slope between anchors.
                var anchors=new TreeMap<Integer,Integer>();anchors.put(0,s.y);anchors.put(result.size()-1,entrance.getY());
                for(int i=0;i<result.size();i++) {
                    var cell=result.get(i);
                    for(var building:s.buildings)for(var point:building.access)
                        if(point.x()==cell.x()&&point.z()==cell.z()) {
                            var previousHeight=anchors.putIfAbsent(i,point.y());
                            if(previousHeight!=null&&previousHeight!=point.y())return List.of();
                        }
                }
                var entries=new ArrayList<>(anchors.entrySet());
                for(int a=1;a<entries.size();a++) {
                    int from=entries.get(a-1).getKey(),to=entries.get(a).getKey();
                    int low=entries.get(a-1).getValue(),high=entries.get(a).getValue();
                    if(Math.abs(high-low)>to-from)return List.of();
                    for(int i=from;i<=to;i++) {
                        var point=result.get(i);int height=low+(int)Math.round((double)(high-low)*(i-from)/(to-from));
                        result.set(i,new SitePoint(point.x(),height,point.z()));
                    }
                }
                return result;
            }
            for(var direction:Direction.Plane.HORIZONTAL) {
                var next=p.relative(direction);
                if(previous.containsKey(next)||!level.hasChunkAt(next)
                    ||Math.hypot((double)next.getX()-s.x,(double)next.getZ()-s.z)>Construction.radius(s))continue;
                if(Math.abs(next.getX()-s.x)<=4&&Math.abs(next.getZ()-s.z)<=3)continue;
                if(Math.abs(next.getX()-x)<=type.halfWidth&&Math.abs(next.getZ()-z)<=type.halfDepth)continue;
                if(s.buildings.stream().anyMatch(b->b.contains(next.getX(),next.getZ(),0)))continue;
                previous.put(next,p);queue.add(next);
            }
        }
        return List.of();
    }
    private static long columnKey(int x,int z) {return ((long)x<<32)|(z&0xffffffffL);}
    /** Add safe two-cell bends when the first route is too short for a walkable staircase. */
    private static boolean lengthenForGrades(List<SitePoint> path,ServerLevel level,Settlement s,BuildingType type,int x,int z,int endY) {
        var existing=new HashMap<Long,Integer>();
        for(var building:s.buildings)for(var point:building.access)existing.put(columnKey(point.x(),point.z()),point.y());
        while(true) {
            int from=0,to=-1,lastY=s.y;
            for(int i=1;i<path.size();i++) {
                var point=path.get(i);var anchor=i==path.size()-1?Integer.valueOf(endY):existing.get(columnKey(point.x(),point.z()));
                if(anchor==null)continue;
                if(Math.abs(anchor-lastY)>i-from) {to=i;break;}
                from=i;lastY=anchor;
            }
            if(to<0)return true;
            var used=new HashSet<Long>();for(var point:path)used.add(columnKey(point.x(),point.z()));
            boolean added=false;
            for(int i=from;i<to&&!added;i++) {
                var a=path.get(i);var b=path.get(i+1);int dx=b.x()-a.x(),dz=b.z()-a.z();
                for(int side:new int[]{1,-1}) {
                    int ox=-dz*side,oz=dx*side;
                    int ax=a.x()+ox,az=a.z()+oz,bx=b.x()+ox,bz=b.z()+oz;
                    if(!bendAllowed(level,s,type,x,z,ax,az,used,existing)||!bendAllowed(level,s,type,x,z,bx,bz,used,existing))continue;
                    path.add(i+1,new SitePoint(ax,s.y,az));path.add(i+2,new SitePoint(bx,s.y,bz));added=true;break;
                }
            }
            if(!added)return false;
        }
    }
    private static boolean bendAllowed(ServerLevel level,Settlement s,BuildingType type,int x,int z,int px,int pz,Set<Long> used,Map<Long,Integer> existing) {
        long key=columnKey(px,pz);var pos=new BlockPos(px,s.y,pz);
        return !used.contains(key)&&!existing.containsKey(key)&&level.hasChunkAt(pos)&&level.getWorldBorder().isWithinBounds(pos)
            &&Math.hypot((double)px-s.x,(double)pz-s.z)<=Construction.radius(s)
            &&!(Math.abs(px-s.x)<=4&&Math.abs(pz-s.z)<=3)
            &&!(Math.abs(px-x)<=type.halfWidth&&Math.abs(pz-z)<=type.halfDepth)
            &&s.buildings.stream().noneMatch(b->b.contains(px,pz,0));
    }
    private SitePreparation() {}
}
