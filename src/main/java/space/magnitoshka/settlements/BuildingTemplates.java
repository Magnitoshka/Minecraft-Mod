package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import space.magnitoshka.settlements.domain.BuildingType;
import space.magnitoshka.settlements.domain.Building;

/** Ordered, deterministic templates. Progress cursors never depend on random state. */
public final class BuildingTemplates {
    public record Piece(int x,int y,int z,BlockState state) {
        public BlockPos at(BlockPos origin) {return origin.offset(x,y,z);}
    }
    private static final Map<BuildingType,List<Piece>> CACHE=new EnumMap<>(BuildingType.class);
    static {for(var type:BuildingType.values())CACHE.put(type,create(type));}
    public static List<Piece> pieces(BuildingType type) {return CACHE.get(type);}
    private record Key(BuildingType type,int tier,int version) {}
    private static final Map<Key,List<Piece>> TIERS=new HashMap<>();
    public static List<Piece> pieces(Building building) {
        return pieces(BuildingType.byId(building.type),building.targetLevel,building.templateVersion);
    }
    public static List<Piece> previous(Building building) {
        return pieces(BuildingType.byId(building.type),building.upgradeFromLevel,building.upgradeFromVersion);
    }
    public static List<Piece> pieces(BuildingType type,int tier,int version) {
        return TIERS.computeIfAbsent(new Key(type,Math.clamp(tier,1,3),version),key->createTier(key.type,key.tier,key.version));
    }
    private static List<Piece> createTier(BuildingType type,int tier,int version) {
        if(type==BuildingType.TOWN_HALL)return hall(tier);
        if(tier==1&&version==0)return pieces(type);
        var out=new LinkedHashMap<BlockPos,Piece>();
        for(var piece:pieces(type))out.put(new BlockPos(piece.x,piece.y,piece.z),piece);
        if(type==BuildingType.HOUSE) {
            // New houses follow 1/2/4 beds; legacy tier-one houses retain their existing two beds.
            if(tier==1) {out.remove(new BlockPos(1,0,0));out.remove(new BlockPos(1,0,1));}
            if(tier==3) {
                for(int y=3;y<=6;y++)for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++) {
                    var state=(Math.abs(x)==2||Math.abs(z)==2)?Blocks.STONE_BRICKS.defaultBlockState():Blocks.AIR.defaultBlockState();
                    if(y==3)state=Blocks.SPRUCE_PLANKS.defaultBlockState();
                    put(out,x,y,z,state);
                }
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)put(out,x,7,z,Blocks.DARK_OAK_PLANKS.defaultBlockState());
                for(int y=0;y<=4;y++)put(out,0,y,1,Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,Direction.NORTH));
                var bed=Blocks.BED.brown().defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
                for(int x:new int[]{-1,1}) {put(out,x,4,0,bed.setValue(BedBlock.PART,BedPart.FOOT));put(out,x,4,1,bed.setValue(BedBlock.PART,BedPart.HEAD));}
            }
        }
        if(tier>=2)for(var entry:new ArrayList<>(out.entrySet())) {
            var piece=entry.getValue();var state=piece.state;
            if(state.is(Blocks.OAK_PLANKS))state=(tier==2?Blocks.STONE_BRICKS:Blocks.DEEPSLATE_BRICKS).defaultBlockState();
            else if(piece.y==3&&state.is(Blocks.SPRUCE_PLANKS)&&!(type==BuildingType.HOUSE&&tier==3))
                state=(tier==2?Blocks.DARK_OAK_PLANKS:Blocks.POLISHED_DEEPSLATE).defaultBlockState();
            out.put(entry.getKey(),new Piece(piece.x,piece.y,piece.z,state));
        }
        if(type==BuildingType.BARRACKS) {
            var bed=Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);int count=0;
            for(int z:new int[]{-1,1})for(int x:new int[]{-3,-2,-1,1,2,3}) {
                if(count++>=tier*4)continue;
                put(out,x,0,z,bed.setValue(BedBlock.PART,BedPart.FOOT));put(out,x,0,z+1,bed.setValue(BedBlock.PART,BedPart.HEAD));
            }
        }
        return List.copyOf(out.values());
    }
    private static void put(Map<BlockPos,Piece> out,int x,int y,int z,BlockState state) {out.put(new BlockPos(x,y,z),new Piece(x,y,z,state));}
    public static List<Piece> hall(int tier) {
        var out=new ArrayList<Piece>();
        for(int x=-4;x<=4;x++)for(int z=-3;z<=3;z++) {
            add(out,x,-1,z,Blocks.COBBLESTONE.defaultBlockState());
            add(out,x,4,z,(tier==1?Blocks.OAK_PLANKS:tier==2?Blocks.SPRUCE_PLANKS:Blocks.DARK_OAK_PLANKS).defaultBlockState());
            for(int y=0;y<4;y++)if(Math.abs(x)==4||Math.abs(z)==3) {
                if(x==0&&z==-3&&y<2)continue;
                var block=y==2&&((x==0&&z==3)||(Math.abs(x)==4&&z==0))?Blocks.GLASS_PANE:
                    tier==1?Blocks.OAK_PLANKS:tier==2?Blocks.STONE_BRICKS:Blocks.DEEPSLATE_BRICKS;
                add(out,x,y,z,block.defaultBlockState());
            }
        }
        var bed=Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
        for(int x:new int[]{-2,0,2}) {add(out,x,0,1,bed.setValue(BedBlock.PART,BedPart.FOOT));add(out,x,0,2,bed.setValue(BedBlock.PART,BedPart.HEAD));}
        add(out,3,0,0,Blocks.BARREL.defaultBlockState());add(out,-3,0,0,Blocks.CRAFTING_TABLE.defaultBlockState());
        add(out,0,3,0,Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING,true));
        if(tier==3)for(int x=-4;x<=4;x++)for(int z=-3;z<=3;z++)if((Math.abs(x)==4||Math.abs(z)==3)&&(x+z)%2==0)add(out,x,5,z,Blocks.STONE_BRICK_WALL.defaultBlockState());
        return List.copyOf(out);
    }
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
            case LUMBER_HUT -> {add(out,1,0,1,Blocks.CRAFTING_TABLE.defaultBlockState());add(out,-1,0,1,Blocks.BARREL.defaultBlockState());}
            case MINE -> add(out,1,0,1,Blocks.FURNACE.defaultBlockState());
            case SMELTERY -> {add(out,-1,0,1,Blocks.BLAST_FURNACE.defaultBlockState());add(out,1,0,1,Blocks.FURNACE.defaultBlockState());}
            case SMITHY -> {add(out,-1,0,1,Blocks.ANVIL.defaultBlockState());add(out,1,0,1,Blocks.SMITHING_TABLE.defaultBlockState());}
            case BARN -> {add(out,-1,0,1,Blocks.HAY_BLOCK.defaultBlockState());add(out,1,0,1,Blocks.CAULDRON.defaultBlockState());}
            case MARKET -> {add(out,-1,0,1,Blocks.BARREL.defaultBlockState());add(out,1,0,1,Blocks.OAK_FENCE.defaultBlockState());}
            case TRADING_POST -> {add(out,-1,0,1,Blocks.CARTOGRAPHY_TABLE.defaultBlockState());add(out,1,0,1,Blocks.BARREL.defaultBlockState());}
            case ARCHERY_RANGE -> {add(out,-2,0,1,Blocks.TARGET.defaultBlockState());add(out,2,0,1,Blocks.TARGET.defaultBlockState());}
            case MAGE_TOWER -> add(out,0,0,1,Blocks.ENCHANTING_TABLE.defaultBlockState());
            case CHURCH -> {add(out,0,0,1,Blocks.ENCHANTING_TABLE.defaultBlockState());add(out,-2,0,1,Blocks.BOOKSHELF.defaultBlockState());add(out,2,0,1,Blocks.BOOKSHELF.defaultBlockState());}
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
