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
    private static final class ServerCache {
        final LinkedHashMap<UUID,Cached> ready=new LinkedHashMap<>(16,.75f,true);
        final LinkedHashMap<UUID,Job> pending=new LinkedHashMap<>();
    }
    private static final class Job {
        final UUID id;final String dimension;
        final int radius,y,x0,z0,step,size;
        final int[] colors;final short[] heights;final byte[] flags;
        int cursor;
        Job(Settlement settlement,int radius) {
            id=settlement.id;dimension=settlement.dimension;this.radius=radius;y=settlement.y;
            int extent=radius+12;
            step=Math.max(1,(2*extent+MAX_SIZE-2)/(MAX_SIZE-1));
            int half=(extent+step-1)/step;size=2*half+1;
            x0=settlement.x-half*step;z0=settlement.z-half*step;
            colors=new int[size*size];heights=new short[size*size];flags=new byte[size*size];
            Arrays.fill(heights,Short.MIN_VALUE);
        }
        boolean done() {return cursor==colors.length;}
        Survey finish() {
            for(int z=0;z<size;z++)for(int x=0;x<size;x++) {
                int i=z*size+x;if(flags[i]==0)continue;
                int north=z>0&&(flags[i-size]&15)!=UNKNOWN?heights[i-size]:heights[i];
                int west=x>0&&(flags[i-1]&15)!=UNKNOWN?heights[i-1]:heights[i];
                float amount=(float)Math.clamp(.95+(heights[i]-north)*.065/step+(heights[i]-west)*.04/step,.60,1.17);
                if((flags[i]&15)==WATER)amount=.96f;
                colors[i]=shade(colors[i],amount);
            }
            return new Survey(id.toString(),x0,z0,step,size,colors,heights,flags);
        }
    }
    private static final Map<MinecraftServer,ServerCache> CACHE=new WeakHashMap<>();
    /** A cheap lookup/enqueue only. This method never samples the world. */
    public static Survey get(MinecraftServer server,Settlement settlement,int radius) {
        var cache=CACHE.computeIfAbsent(server,key->new ServerCache());
        var old=cache.ready.get(settlement.id);long now=server.getTickCount();
        if(old!=null&&old.radius==radius&&now-old.tick<REFRESH_TICKS)return old.survey;
        var pending=cache.pending.get(settlement.id);
        if(pending==null||pending.radius!=radius) {
            if(cache.pending.size()>=64&&!cache.pending.containsKey(settlement.id))return old==null?null:old.survey;
            cache.pending.put(settlement.id,new Job(settlement,radius));
        }
        return old!=null&&old.radius==radius?old.survey:null;
    }
    /** One shared budget, round-robin across open maps. No background thread touches live chunks. */
    public static void tick(MinecraftServer server,Set<UUID> visible) {
        var cache=CACHE.get(server);if(cache==null)return;
        cache.pending.keySet().removeIf(id->!visible.contains(id));
        int remaining=SettlementConfig.TERRAIN_BUDGET.get();
        while(remaining>0&&!cache.pending.isEmpty()) {
            var entry=cache.pending.entrySet().iterator().next();
            var job=entry.getValue();cache.pending.remove(entry.getKey());
            var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(job.dimension)));
            if(level==null)continue;
            int count=Math.min(64,Math.min(remaining,job.colors.length-job.cursor));
            for(int i=0;i<count;i++)sampleCell(level,job,job.cursor++);
            remaining-=count;
            if(job.done()) {
                var survey=job.finish();var old=cache.ready.get(job.id);
                if(old!=null&&same(old.survey,survey))survey=old.survey;
                cache.ready.put(job.id,new Cached(server.getTickCount(),job.radius,survey));
                while(cache.ready.size()>64)cache.ready.remove(cache.ready.keySet().iterator().next());
            } else cache.pending.put(job.id,job);
        }
    }
    private static boolean same(Survey a,Survey b) {
        return a.originX==b.originX&&a.originZ==b.originZ&&a.step==b.step&&a.size==b.size
            &&Arrays.equals(a.colors,b.colors)&&Arrays.equals(a.heights,b.heights)&&Arrays.equals(a.flags,b.flags);
    }
    private static void sampleCell(ServerLevel level,Job job,int i) {
            int wx=job.x0+(i%job.size)*job.step,wz=job.z0+(i/job.size)*job.step;
            var column=new BlockPos(wx,job.y,wz);
            if(!level.hasChunkAt(column)||!level.getWorldBorder().isWithinBounds(column))return;
            int surface=level.getHeight(Heightmap.Types.WORLD_SURFACE,wx,wz)-1;
            int ground=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,wx,wz)-1;
            if(surface<level.getMinY()||ground<level.getMinY()||surface>=level.getMaxY())return;
            var pos=new BlockPos(wx,surface,wz);var state=level.getBlockState(pos);
            var color=state.getMapColor(level,pos);int rgb=color.col;
            int kind=OTHER;
            if(level.getFluidState(pos).is(FluidTags.WATER)) {
                kind=WATER;rgb=level.getBiome(pos).value().getWaterColor();
            } else if(level.getFluidState(pos).is(FluidTags.LAVA)) {kind=LAVA;rgb=0xDA6026;}
            else if(state.is(BlockTags.SAND)||state.is(Blocks.SANDSTONE)||state.is(Blocks.RED_SANDSTONE)) {kind=SAND;}
            else if(state.is(net.minecraft.tags.BlockTags.SNOW)||state.is(net.minecraft.tags.BlockTags.ICE)||state.is(Blocks.POWDER_SNOW)) {kind=SNOW;}
            else if(state.is(BlockTags.LEAVES)||state.is(BlockTags.LOGS)) {kind=FOREST;rgb=level.getBiome(pos).value().getFoliageColor();}
            else if(state.is(Blocks.GRASS_BLOCK)||state.is(Blocks.MOSS_BLOCK)||color==MapColor.GRASS||color==MapColor.PLANT) {
                kind=GRASS;rgb=level.getBiome(pos).value().getGrassColor(wx,wz);
            } else if(state.is(BlockTags.BASE_STONE_OVERWORLD)||state.is(Blocks.COBBLESTONE)||state.is(Blocks.GRAVEL)||state.is(Blocks.DEEPSLATE)) {kind=STONE;}
            else if(state.is(BlockTags.PLANKS)||level.getBlockEntity(pos)!=null) {kind=STRUCTURE;}
            var floor=new BlockPos(wx,ground,wz);var floorState=level.getBlockState(floor);
            boolean clear=level.getBlockEntity(floor)==null&&floorState.isFaceSturdy(level,floor,Direction.UP)&&level.getFluidState(floor).isEmpty();
            for(int y=1;y<=5&&clear;y++) {
                var at=floor.above(y);
                clear=at.getY()<level.getMaxY()&&SitePreparation.cover(level.getBlockState(at))&&level.getFluidState(at).isEmpty();
            }
            job.heights[i]=(short)Math.clamp(ground+1,Short.MIN_VALUE+1,Short.MAX_VALUE);
            job.flags[i]=(byte)(kind|(clear?CLEAR:0));
            job.colors[i]=0xFF000000|(rgb==0?0xB5A47B:rgb);
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
