package space.magnitoshka.settlements;

import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Real player/input slots; accepted plain stacks are atomically moved into virtual stock. */
public final class WarehouseMenu extends AbstractContainerMenu {
    public static final int INPUTS=9;
    public final UUID settlementId;
    private final ServerPlayer actor;
    private final SimpleContainer input=new SimpleContainer(INPUTS);
    private boolean draining,closed;
    private int lastPendingHash;
    public WarehouseMenu(int id,Inventory inventory,FriendlyByteBuf extra) {this(id,inventory,UUID.fromString(extra.readUtf(36)));}
    public WarehouseMenu(int id,Inventory inventory,UUID settlement) {
        super(Settlements.WAREHOUSE_MENU.get(),id);settlementId=settlement;
        actor=inventory.player instanceof ServerPlayer p?p:null;
        for(int row=0;row<3;row++)for(int col=0;col<3;col++)addSlot(new Slot(input,row*3+col,12+col*18,36+row*18){
            @Override public boolean mayPlace(ItemStack stack) {return accepts(stack);}
        });
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(inventory,col+row*9+9,12+col*18,123+row*18));
        for(int col=0;col<9;col++)addSlot(new Slot(inventory,col,12+col*18,183));
    }
    public static void open(ServerPlayer player,UUID settlement) {
        var s=SettlementData.get(player.level().getServer()).state.owned(player.getUUID(),settlement);
        if(!ServerSettlements.nearStorage(player,s))return;
        SettlementNetwork.close(player.getUUID());
        player.openMenu(new SimpleMenuProvider((id,inventory,p)->new WarehouseMenu(id,inventory,settlement),
            Component.translatable("warehouse.smartsettlements.title",s.name)),buffer->buffer.writeUtf(settlement.toString(),36));
        SettlementNetwork.refreshWarehouse(player,settlement);
    }
    public static boolean accepts(ItemStack stack) {return !stack.isEmpty()&&stack.getMaxStackSize()>1&&stack.getComponentsPatch().isEmpty();}
    @Override public boolean stillValid(Player player) {
        if(actor==null)return true;
        var s=SettlementData.get(actor.level().getServer()).state.settlements.get(settlementId);
        return !closed&&s!=null&&s.owner.equals(player.getUUID())&&ServerSettlements.nearStorage(actor,s);
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(index<0||index>=slots.size()||!stillValid(player))return ItemStack.EMPTY;
        var slot=slots.get(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var stack=slot.getItem();var original=stack.copy();
        if(index<INPUTS) {
            if(!moveItemStackTo(stack,INPUTS,slots.size(),true)) {
                if(actor!=null)SettlementNetwork.notice(actor,settlementId,false,Component.translatable("warehouse.smartsettlements.inventory_full"));
                return ItemStack.EMPTY;
            }
        } else {
            if(!accepts(stack)) {if(actor!=null)SettlementNetwork.notice(actor,settlementId,false,Component.translatable("warehouse.smartsettlements.unsupported"));return ItemStack.EMPTY;}
            if(!moveItemStackTo(stack,0,INPUTS,false)) {
                if(actor!=null)SettlementNetwork.notice(actor,settlementId,false,Component.translatable("warehouse.smartsettlements.input_full"));
                return ItemStack.EMPTY;
            }
        }
        if(stack.isEmpty())slot.setByPlayer(ItemStack.EMPTY);else slot.setChanged();
        slot.onTake(player,stack);return original;
    }
    @Override public void clicked(int index,int button,net.minecraft.world.inventory.ContainerInput type,Player player) {
        if(actor!=null&&index>=0&&index<INPUTS&&type==net.minecraft.world.inventory.ContainerInput.PICKUP&&!getCarried().isEmpty()&&!accepts(getCarried())) {
            SettlementNetwork.notice(actor,settlementId,false,Component.translatable("warehouse.smartsettlements.unsupported"));return;
        }
        super.clicked(index,button,type,player);
    }
    @Override public void broadcastChanges() {drain();super.broadcastChanges();}
    private void drain() {
        if(actor==null||closed||draining||!stillValid(actor))return;
        draining=true;
        try {
            var data=SettlementData.get(actor.level().getServer());var s=data.state.owned(actor.getUUID(),settlementId);
            int total=0;
            for(int i=0;i<INPUTS;i++) {
                var stack=input.getItem(i);if(!accepts(stack))continue;
                int accepted=s.deposit(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),stack.getCount(),Construction.capacity(s));
                if(accepted>0) {stack.shrink(accepted);if(stack.isEmpty())input.setItem(i,ItemStack.EMPTY);else input.setChanged();total+=accepted;}
            }
            int pending=0,hash=1;
            for(int i=0;i<INPUTS;i++) {
                var stack=input.getItem(i);pending+=stack.getCount();
                hash=31*hash+stack.getCount();if(!stack.isEmpty())hash=31*hash+BuiltInRegistries.ITEM.getKey(stack.getItem()).hashCode();
            }
            if(total>0) {
                data.setDirty();SettlementNetwork.refreshWarehouse(actor,settlementId);
                SettlementNetwork.notice(actor,settlementId,pending==0,Component.translatable(pending==0?"warehouse.smartsettlements.deposited":"warehouse.smartsettlements.partial",total,pending));
            } else if(pending>0&&hash!=lastPendingHash)SettlementNetwork.notice(actor,settlementId,false,Component.translatable("warehouse.smartsettlements.full",pending));
            lastPendingHash=hash;
        } finally {draining=false;}
    }
    @Override public void removed(Player player) {
        if(actor!=null&&!closed) {drain();closed=true;clearContainer(player,input);}
        super.removed(player);
    }
}
