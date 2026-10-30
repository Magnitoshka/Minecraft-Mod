package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import space.magnitoshka.settlements.domain.BuildingType;
import space.magnitoshka.settlements.domain.SiteEdit;

/** Bounded earthworks inside the footprint. The whole plan is validated before reserving materials. */
public final class SitePreparation {
    public static final int MAX_CUT=4,MAX_FILL=3,MAX_TREE=16;
    public record Plan(int y,int fill,List<SiteEdit> edits) {}
    public static Plan plan(ServerLevel level,ServerPlayer player,BuildingType type,int x,int z) {
        var heights=new ArrayList<Integer>();
        for(int dx=-type.halfWidth;dx<=type.halfWidth;dx++)for(int dz=-type.halfDepth;dz<=type.halfDepth;dz++) {
            int ground=ground(level,x+dx,z+dz);
            if(ground==Integer.MIN_VALUE||!naturalTreeColumn(level,x+dx,z+dz,ground))return null;
            heights.add(ground+1);
        }
        heights.sort(Integer::compareTo);int y=heights.get(heights.size()/2);
        if(y-2<=level.getMinY()||y+MAX_TREE>=level.getMaxY())return null;
        var edits=new ArrayList<SiteEdit>();int fill=0;
        for(int dx=-type.halfWidth;dx<=type.halfWidth;dx++)for(int dz=-type.halfDepth;dz<=type.halfDepth;dz++) {
            int wx=x+dx,wz=z+dz,ground=ground(level,wx,wz);
            if(ground+1-y>MAX_CUT||y-ground-1>MAX_FILL)return null;
            var support=new BlockPos(wx,Math.min(ground,y-1),wz);
            if(!permitted(level,player,support)||!foundation(level.getBlockState(support))||!level.getBlockState(support).isFaceSturdy(level,support,Direction.UP))return null;
            if(type==BuildingType.FIELD&&!level.getBlockState(support.below()).isFaceSturdy(level,support.below(),Direction.UP))return null;
            int top=Math.max(y+4,level.getHeight(Heightmap.Types.WORLD_SURFACE,wx,wz)-1);
            if(top>=y+MAX_TREE)return null;
            for(int h=top;h>=Math.min(y,ground+1);h--) {
                var pos=new BlockPos(wx,h,wz);var state=level.getBlockState(pos);
                if(!permitted(level,player,pos)||!removable(state))return null;
                if(!state.isAir())edits.add(new SiteEdit(wx,h,wz,false,BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
            }
            for(int h=ground+1;h<y;h++) {
                var pos=new BlockPos(wx,h,wz);
                if(!permitted(level,player,pos)||!removable(level.getBlockState(pos)))return null;
                edits.add(new SiteEdit(wx,h,wz,true,""));fill++;
            }
        }
        return new Plan(y,fill,edits);
    }
    private static int ground(ServerLevel level,int x,int z) {
        var pos=new BlockPos(x,level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)-1,z);
        for(int i=0;i<=MAX_TREE&&pos.getY()>level.getMinY();i++,pos=pos.below()) {
            var state=level.getBlockState(pos);
            if(!state.is(BlockTags.LOGS)&&!state.is(BlockTags.LEAVES)&&!state.canBeReplaced())return pos.getY();
        }
        return Integer.MIN_VALUE;
    }
    private static boolean permitted(ServerLevel level,ServerPlayer player,BlockPos pos) {
        return level.hasChunkAt(pos)&&level.getWorldBorder().isWithinBounds(pos)&&level.mayInteract(player,pos)
            &&level.getBlockEntity(pos)==null&&level.getFluidState(pos).isEmpty();
    }
    private static boolean naturalTreeColumn(ServerLevel level,int x,int z,int ground) {
        int top=-1;
        for(int h=ground+1;h<=ground+MAX_TREE;h++) {
            var state=level.getBlockState(new BlockPos(x,h,z));
            if(!state.is(BlockTags.LOGS))continue;
            if(!BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().endsWith("_log")
                ||BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().startsWith("stripped_")
                ||!state.hasProperty(RotatedPillarBlock.AXIS)||state.getValue(RotatedPillarBlock.AXIS)!=Direction.Axis.Y)return false;
            var below=level.getBlockState(new BlockPos(x,h-1,z));
            if(h==ground+1?!below.is(BlockTags.DIRT):!below.is(BlockTags.LOGS))return false;
            top=h;
        }
        if(top<0)return true;
        // Generated crowns have non-persistent leaves; a bare log wall is not a tree.
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)for(int dy=0;dy<=2;dy++) {
            var pos=new BlockPos(x+dx,top+dy,z+dz);
            if(level.hasChunkAt(pos)&&naturalLeaves(level.getBlockState(pos)))return true;
        }
        return false;
    }
    private static boolean naturalLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES)&&state.hasProperty(LeavesBlock.PERSISTENT)&&!state.getValue(LeavesBlock.PERSISTENT);
    }
    private static boolean foundation(BlockState state) {
        return (removable(state)&&!state.is(BlockTags.LOGS)&&!state.is(BlockTags.LEAVES))||state.is(BlockTags.BASE_STONE_OVERWORLD);
    }
    public static boolean removable(BlockState state) {
        if(state.is(BlockTags.LEAVES))return naturalLeaves(state);
        return state.canBeReplaced()||state.is(BlockTags.DIRT)||state.is(BlockTags.SAND)||state.is(BlockTags.LOGS)
            ||state.is(Blocks.GRAVEL)||state.is(Blocks.SNOW_BLOCK)||state.is(Blocks.MOSS_BLOCK);
    }
    private SitePreparation() {}
}
