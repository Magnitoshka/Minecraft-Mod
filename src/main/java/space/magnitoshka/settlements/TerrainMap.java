package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import space.magnitoshka.settlements.domain.Settlement;

/** Short-lived, loaded-chunk-only survey. Sampling never loads or generates map chunks. */
public final class TerrainMap {
    public static final int UNKNOWN=0,WATER=1,SAND=2,GRASS=3,STONE=4,SNOW=5,FOREST=6,STRUCTURE=7,LAVA=8,OTHER=9;
    public static final int CLEAR=16,MAX_SIZE=161,REFRESH_TICKS=100;
    public record Survey(String settlement,int originX,int originZ,int step,int size,int[] colors,short[] heights,byte[] flags) {
        public Survey {
            if(size<1||size>MAX_SIZE||step<1||step>16||colors.length!=size*size||heights.length!=size*size||flags.length!=size*size)
                throw new IllegalArgumentException("Invalid terrain survey dimensions");
        }
        public int index(int x,int z) {
            int dx=x-originX,dz=z-originZ;
            if(dx<0||dz<0||dx>=size*step||dz>=size*step)return -1;
            return (dz/step)*size+dx/step;
        }
        public int kind(int index) {return index<0?UNKNOWN:flags[index]&15;}
        public boolean clear(int index) {return index>=0&&(flags[index]&CLEAR)!=0;}
    }
    private record Cached(long tick,int radius,Survey survey) {}
    private static final Map<MinecraftServer,LinkedHashMap<UUID,Cached>> CACHE=new WeakHashMap<>();
    public static Survey get(MinecraftServer server,Settlement settlement,int radius) {
        var worlds=CACHE.computeIfAbsent(server,key->new LinkedHashMap<>(16,.75f,true));
        long tick=server.getTickCount();var old=worlds.get(settlement.id);
        if(old!=null&&old.radius==radius&&tick-old.tick<REFRESH_TICKS)return old.survey;
        var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(settlement.dimension)));
        if(level==null)return null;
        var survey=sample(level,settlement,radius);
        worlds.put(settlement.id,new Cached(tick,radius,survey));
        while(worlds.size()>64)worlds.remove(worlds.keySet().iterator().next());
        return survey;
    }
    private static Survey sample(ServerLevel level,Settlement s,int radius) {
        int extent=radius+12;
        int step=Math.max(1,(2*extent+MAX_SIZE-2)/(MAX_SIZE-1));
        int half=(extent+step-1)/step,size=2*half+1;
        int x0=s.x-half*step,z0=s.z-half*step;
        int[] colors=new int[size*size];short[] heights=new short[size*size];byte[] flags=new byte[size*size];
        Arrays.fill(heights,Short.MIN_VALUE);
        for(int z=0;z<size;z++)for(int x=0;x<size;x++) {
            int i=z*size+x,wx=x0+x*step,wz=z0+z*step;
            var column=new BlockPos(wx,s.y,wz);
            if(!level.hasChunkAt(column)||!level.getWorldBorder().isWithinBounds(column))continue;
            int surface=level.getHeight(Heightmap.Types.WORLD_SURFACE,wx,wz)-1;
            int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,wx,wz)-1;
            if(surface<level.getMinY()||ground<level.getMinY()||surface>=level.getMaxY())continue;
            var pos=new BlockPos(wx,surface,wz);var state=level.getBlockState(pos);
            var color=state.getMapColor(level,pos);int rgb=color.col;
            int kind=OTHER;
            if(level.getFluidState(pos).is(FluidTags.WATER)) {
                kind=WATER;rgb=level.getBiome(pos).value().getWaterColor();
            } else if(level.getFluidState(pos).is(FluidTags.LAVA)) {kind=LAVA;rgb=0xDA6026;}
            else if(state.is(BlockTags.SAND)||state.is(Blocks.SANDSTONE)||state.is(Blocks.RED_SANDSTONE)) {kind=SAND;}
            else if(state.is(Blocks.SNOW)||state.is(Blocks.SNOW_BLOCK)||state.is(Blocks.POWDER_SNOW)) {kind=SNOW;}
            else if(state.is(BlockTags.LEAVES)||state.is(BlockTags.LOGS)) {kind=FOREST;rgb=level.getBiome(pos).value().getFoliageColor();}
            else if(state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.MOSS_BLOCK)||color==MapColor.GRASS||color==MapColor.PLANT) {
                kind=GRASS;rgb=level.getBiome(pos).value().getGrassColor(wx,wz);
            } else if(state.is(BlockTags.BASE_STONE_OVERWORLD)||state.is(Blocks.COBBLESTONE)||state.is(Blocks.GRAVEL)||state.is(Blocks.DEEPSLATE)) {kind=STONE;}
            else if(state.is(BlockTags.PLANKS)||level.getBlockEntity(pos)!=null) {kind=STRUCTURE;}
            var floor=new BlockPos(wx,ground,wz);var floorState=level.getBlockState(floor);
            boolean clear=level.getBlockEntity(floor)==null&&floorState.isFaceSturdy(level,floor,Direction.UP)&&level.getFluidState(floor).isEmpty();
            for(int y=1;y<=5&&clear;y++) {
                var at=floor.above(y);
                clear=at.getY()<level.getMaxY()&&level.getBlockState(at).canBeReplaced()&&level.getFluidState(at).isEmpty();
            }
            heights[i]=(short)Math.clamp(ground+1,Short.MIN_VALUE+1,Short.MAX_VALUE);
            flags[i]=(byte)(kind|(clear?CLEAR:0));
            colors[i]=0xFF000000|(rgb==0?0xB5A47B:rgb);
        }
        // North-west light makes slopes readable while material colors still identify beaches/water.
        for(int z=0;z<size;z++)for(int x=0;x<size;x++) {
            int i=z*size+x;if(flags[i]==0)continue;
            int north=z>0&&(flags[i-size]&15)!=UNKNOWN?heights[i-size]:heights[i];
            int west=x>0&&(flags[i-1]&15)!=UNKNOWN?heights[i-1]:heights[i];
            float shade=(float)Math.clamp(.95+(heights[i]-north)*.065/step+(heights[i]-west)*.04/step,.60,1.17);
            if((flags[i]&15)==WATER)shade=.96f;
            colors[i]=shade(colors[i],shade);
        }
        return new Survey(s.id.toString(),x0,z0,step,size,colors,heights,flags);
    }
    private static int shade(int argb,float amount) {
        int r=Math.min(255,(int)(((argb>>16)&255)*amount)),g=Math.min(255,(int)(((argb>>8)&255)*amount)),b=Math.min(255,(int)((argb&255)*amount));
        return 0xFF000000|r<<16|g<<8|b;
    }
    public static void encode(Survey survey,FriendlyByteBuf buffer) {
        buffer.writeUtf(survey.settlement,36);buffer.writeInt(survey.originX);buffer.writeInt(survey.originZ);
        buffer.writeVarInt(survey.step);buffer.writeVarInt(survey.size);
        for(int i=0;i<survey.colors.length;i++) {buffer.writeInt(survey.colors[i]);buffer.writeShort(survey.heights[i]);buffer.writeByte(survey.flags[i]);}
    }
    public static Survey decode(FriendlyByteBuf buffer) {
        String id=buffer.readUtf(36);int x=buffer.readInt(),z=buffer.readInt(),step=buffer.readVarInt(),size=buffer.readVarInt();
        if(size<1||size>MAX_SIZE||step<1||step>16)throw new IllegalArgumentException("Terrain packet exceeds bounds");
        int cells=size*size;
        if(buffer.readableBytes()<cells*7)throw new IllegalArgumentException("Truncated terrain packet");
        int[] colors=new int[cells];short[] heights=new short[cells];byte[] flags=new byte[cells];
        for(int i=0;i<cells;i++) {colors[i]=buffer.readInt();heights[i]=buffer.readShort();flags[i]=buffer.readByte();}
        return new Survey(id,x,z,step,size,colors,heights,flags);
    }
    private TerrainMap() {}
}
