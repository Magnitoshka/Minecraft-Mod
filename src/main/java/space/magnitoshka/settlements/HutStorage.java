package space.magnitoshka.settlements;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import space.magnitoshka.settlements.domain.*;

/** A real barrel; its vanilla block entity owns the inventory and persistence. */
public final class HutStorage {
    public static BlockPos position(Building hut) {return new BlockPos(hut.x-1,hut.y,hut.z+1);}
    public static Container get(ServerLevel level,Building hut) {
        var at=position(hut);
        return level.hasChunkAt(at)&&level.getBlockEntity(at) instanceof Container inventory?inventory:null;
    }
    public static void initialize(ServerLevel level,SettlementData data,Settlement s,Building hut) {
        if(!hut.complete||!hut.type.equals("lumber_hut")||hut.starterSaplings)return;
        var at=position(hut);if(!level.hasChunkAt(at))return;
        if(get(level,hut)==null) {
            if(!SitePreparation.cover(level.getBlockState(at))||!Construction.canChange(level,at,s.owner))return;
            if(!level.setBlock(at,Blocks.BARREL.defaultBlockState(),3))return;
        }
        var inventory=get(level,hut);if(inventory==null)return;
        var entity=level.getBlockEntity(at);if(entity==null)return;
        // Store distribution progress with the physical inventory so a delayed world-data save cannot repeat a gift.
        var marker=entity.components().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
            net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        hut.starterSaplingsRemaining=marker.getIntOr("SmartSettlementsStarterSaplings",hut.starterSaplingsRemaining<0?10:hut.starterSaplingsRemaining);
        var starter=new ItemStack(Items.OAK_SAPLING,10);
        starter.setCount(hut.starterSaplingsRemaining);
        int moved=insert(inventory,starter);hut.starterSaplingsRemaining-=moved;
        marker.putInt("SmartSettlementsStarterSaplings",hut.starterSaplingsRemaining);
        entity.setComponents(net.minecraft.core.component.DataComponentMap.builder().addAll(entity.components())
            .set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(marker)).build());
        entity.setChanged();
        hut.starterSaplings=hut.starterSaplingsRemaining==0;data.setDirty();
    }
    public static int count(Container inventory,net.minecraft.world.item.Item item) {
        if(inventory==null)return 0;
        int total=0;for(int i=0;i<inventory.getContainerSize();i++) {
            var stack=inventory.getItem(i);if(stack.is(item))total+=stack.getCount();
        }
        return total;
    }
    public static int room(Container inventory,ItemStack stack) {
        if(inventory==null)return 0;
        int room=0;for(int i=0;i<inventory.getContainerSize();i++) {
            var current=inventory.getItem(i);int limit=Math.min(stack.getMaxStackSize(),inventory.getMaxStackSize());
            if(current.isEmpty())room+=limit;
            else if(ItemStack.isSameItemSameComponents(current,stack))room+=Math.max(0,limit-current.getCount());
        }
        return room;
    }
    public static int insert(Container inventory,ItemStack stack) {
        if(inventory==null||stack.isEmpty())return 0;
        int remaining=stack.getCount();
        for(int pass=0;pass<2;pass++)for(int i=0;i<inventory.getContainerSize()&&remaining>0;i++) {
            var current=inventory.getItem(i);int limit=Math.min(stack.getMaxStackSize(),inventory.getMaxStackSize());
            if(pass==0&&!current.isEmpty()&&ItemStack.isSameItemSameComponents(current,stack)) {
                int moved=Math.min(remaining,Math.max(0,limit-current.getCount()));current.grow(moved);remaining-=moved;
            } else if(pass==1&&current.isEmpty()) {
                int moved=Math.min(remaining,limit);inventory.setItem(i,stack.copyWithCount(moved));remaining-=moved;
            }
        }
        if(remaining<stack.getCount())inventory.setChanged();return stack.getCount()-remaining;
    }
    public static boolean transportable(ItemStack stack) {return !stack.isEmpty()&&(stack.is(ItemTags.LOGS)||stack.is(ItemTags.SAPLINGS))&&WarehouseMenu.accepts(stack);}
    public static int collect(Container inventory,Resident r) {
        int total=0;
        for(int i=0;i<inventory.getContainerSize();i++) {
            var stack=inventory.getItem(i);if(!transportable(stack))continue;
            int moved=r.builderInventory.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),"courier",stack.getCount());
            if(moved>0) {stack.shrink(moved);total+=moved;}
        }
        if(total>0)inventory.setChanged();return total;
    }
    private HutStorage() {}
}
