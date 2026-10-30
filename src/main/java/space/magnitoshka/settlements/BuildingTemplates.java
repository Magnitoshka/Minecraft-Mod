package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import space.magnitoshka.settlements.domain.BuildingType;

/** Ordered, deterministic templates. Progress cursors never depend on random state. */
public final class BuildingTemplates {
    public record Piece(int x,int y,int z,BlockState state) {
        public BlockPos at(BlockPos origin) {return origin.offset(x,y,z);}
    }
    private static final Map<BuildingType,List<Piece>> CACHE=new EnumMap<>(BuildingType.class);
    static {for(var type:BuildingType.values())CACHE.put(type,create(type));}
    public static List<Piece> pieces(BuildingType type) {return CACHE.get(type);}
    private static List<Piece> create(BuildingType type) {
        var out=new ArrayList<Piece>();
        int w=type.halfWidth,d=type.halfDepth;
        if(type==BuildingType.FIELD) {
            for(int x=-w;x<=w;x++)for(int z=-d;z<=d;z++)
                add(out,x,-1,z,(x==0&&z==0?Blocks.WATER:Blocks.FARMLAND).defaultBlockState());
            for(int x=-w;x<=w;x++)for(int z=-d;z<=d;z++)
                if(x!=0||z!=0)add(out,x,0,z,Blocks.WHEAT.defaultBlockState());
            return List.copyOf(out);
        }
        for(int x=-w;x<=w;x++)for(int z=-d;z<=d;z++)add(out,x,-1,z,Blocks.COBBLESTONE.defaultBlockState());
        for(int y=0;y<3;y++)for(int x=-w;x<=w;x++)for(int z=-d;z<=d;z++) {
            if(Math.abs(x)!=w&&Math.abs(z)!=d)continue;
            if(x==0&&z==-d&&y<2)continue;
            boolean corner=Math.abs(x)==w&&Math.abs(z)==d;
            var block=corner?Blocks.OAK_LOG:(type==BuildingType.MINE?Blocks.COBBLESTONE:Blocks.OAK_PLANKS);
            if(y==1&&z==d&&x==0)block=Blocks.GLASS;
            add(out,x,y,z,block.defaultBlockState());
        }
        for(int x=-w;x<=w;x++)for(int z=-d;z<=d;z++)add(out,x,3,z,Blocks.SPRUCE_PLANKS.defaultBlockState());
        add(out,0,2,0,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        switch(type) {
            case HOUSE -> {
                var bed=Blocks.BED.brown().defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
                for(int x:new int[]{-1,1}) {
                    add(out,x,0,0,bed.setValue(BedBlock.PART,BedPart.FOOT));
                    add(out,x,0,1,bed.setValue(BedBlock.PART,BedPart.HEAD));
                }
            }
            case WAREHOUSE -> {add(out,-2,0,1,Blocks.BARREL.defaultBlockState());add(out,2,0,1,Blocks.BARREL.defaultBlockState());}
            case LUMBER_HUT -> add(out,1,0,1,Blocks.CRAFTING_TABLE.defaultBlockState());
            case MINE -> add(out,1,0,1,Blocks.FURNACE.defaultBlockState());
            default -> {}
        }
        return List.copyOf(out);
    }
    public static boolean matches(BlockState actual,BlockState expected) {
        if(actual.getBlock()!=expected.getBlock())return false;
        return !(expected.getBlock() instanceof BedBlock)||(actual.getValue(BedBlock.PART)==expected.getValue(BedBlock.PART)&&actual.getValue(BedBlock.FACING)==expected.getValue(BedBlock.FACING));
    }
    private static void add(List<Piece> out,int x,int y,int z,BlockState state) {out.add(new Piece(x,y,z,state));}
    private BuildingTemplates() {}
}
