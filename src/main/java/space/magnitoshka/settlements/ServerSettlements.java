package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import space.magnitoshka.settlements.domain.Settlement;
import space.magnitoshka.settlements.domain.Resident;

public final class ServerSettlements {
    private static long ticks;
    private static final Random RANDOM = new Random();
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) return;
        var data=SettlementData.get(p.level().getServer());
        if (data.state.initializedPlayers.add(p.getUUID())) {
            data.state.wallets.put(p.getUUID(),(long)SettlementConfig.WALLET.get());
            give(p,new ItemStack(Settlements.BANNER.get()));
            give(p,new ItemStack(Items.STONE_PICKAXE)); give(p,new ItemStack(Items.STONE_AXE));
            give(p,new ItemStack(Items.STONE_SHOVEL)); give(p,new ItemStack(Items.STONE_HOE));
            give(p,new ItemStack(Items.BREAD,SettlementConfig.BREAD.get()));
            p.sendSystemMessage(Component.translatable("message.smartsettlements.welcome"));
            data.setDirty();
        } else if (data.state.settlements.values().stream().noneMatch(s->s.owner.equals(p.getUUID()))
                && !p.getInventory().contains(new ItemStack(Settlements.BANNER.get()))) {
            give(p,new ItemStack(Settlements.BANNER.get()));
        }
    }
    static void give(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack,false,net.minecraft.util.Prediction.SERVER_ONLY);
    }
    public static boolean found(ServerPlayer player, BlockPos pos) {
        var level=(ServerLevel)player.level();
        var data=SettlementData.get(level.getServer());
        // Validate complete footprint before changing either the world or the saved economy.
        if (pos.getY()<level.getMinY()+1 || pos.getY()+6>=level.getMaxY()) return fail(player,"site");
        for (int x=-4;x<=4;x++) for (int z=-3;z<=3;z++) {
            BlockPos floor=pos.offset(x,-1,z);
            if (!level.getWorldBorder().isWithinBounds(floor) || !level.mayInteract(player,floor)
                    || level.getBlockEntity(floor)!=null || !level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP)) return fail(player,"site");
            for (int y=0;y<=5;y++) {
                var target=pos.offset(x,y,z);
                if (!level.getBlockState(target).canBeReplaced() || !level.getFluidState(target).isEmpty()) return fail(player,"site");
            }
        }
        Settlement s;
        try { s=data.state.found(player.getUUID(),level.dimension().identifier().toString(),pos.getX(),pos.getY(),pos.getZ(),
                Math.floorDiv(level.getOverworldClockTime(),24000),SettlementConfig.rules()); }
        catch (IllegalArgumentException ex) { return fail(player,"distance"); }
        buildHall(level,pos);
        for (var r:s.residents) spawn(level,s,r);
        data.setDirty();
        player.sendSystemMessage(Component.translatable("message.smartsettlements.founded",s.name));
        SettlementNetwork.open(player,s.id,null);
        return true;
    }
    static void buildHall(ServerLevel level, BlockPos p) {
        for (int x=-4;x<=4;x++) for (int z=-3;z<=3;z++) {
            level.setBlock(p.offset(x,-1,z),Blocks.COBBLESTONE.defaultBlockState(),3);
            level.setBlock(p.offset(x,4,z),Blocks.OAK_PLANKS.defaultBlockState(),3);
            for (int y=0;y<4;y++) {
                if (Math.abs(x)==4 || Math.abs(z)==3) {
                    if (x==0 && z==-3 && y<2) continue;
                    var block=(y==2 && ((x==0 && z==3)||(Math.abs(x)==4 && z==0)))?Blocks.GLASS_PANE:Blocks.OAK_PLANKS;
                    level.setBlock(p.offset(x,y,z),block.defaultBlockState(),3);
                }
            }
        }
        for (int i=0;i<3;i++) {
            var bed=Blocks.BED.red().defaultBlockState().setValue(BedBlock.FACING,Direction.SOUTH);
            level.setBlock(p.offset(-2+i*2,0,1),bed.setValue(BedBlock.PART,BedPart.FOOT),3);
            level.setBlock(p.offset(-2+i*2,0,2),bed.setValue(BedBlock.PART,BedPart.HEAD),3);
        }
        level.setBlock(p.offset(3,0,0),Blocks.BARREL.defaultBlockState(),3);
        level.setBlock(p.offset(-3,0,0),Blocks.CRAFTING_TABLE.defaultBlockState(),3);
        level.setBlock(p.offset(0,3,0),Blocks.LANTERN.defaultBlockState().setValue(net.minecraft.world.level.block.LanternBlock.HANGING,true),3);
        level.setBlock(p.offset(0,5,0),Blocks.BANNER.blue().defaultBlockState(),3);
    }
    private static void spawn(ServerLevel level, Settlement s, Resident r) {
        var citizen=Settlements.CITIZEN.get().create(level,EntitySpawnReason.TRIGGERED);
        if (citizen==null) throw new IllegalStateException("Citizen creation failed");
        citizen.initialize(s.id,r);
        int index=0;
        for(int slot=0;slot<3;slot++) {
            String candidate=(s.x-2+slot*2)+","+s.y+","+(s.z+2);
            if(level.getBlockState(new BlockPos(s.x-2+slot*2,s.y,s.z+2)).getBlock() instanceof BedBlock && s.residents.stream().noneMatch(v->candidate.equals(v.bed))) {index=slot;break;}
        }
        citizen.setPos(s.x-2+index*2+.5,s.y,s.z-1+.5);
        if (level.addFreshEntity(citizen)) {
            r.spawned=true; r.bed=(s.x-2+index*2)+","+s.y+","+(s.z+2);
        }
    }
    public static boolean interact(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand()!=InteractionHand.MAIN_HAND) return false;
        var block=event.getLevel().getBlockState(event.getPos()).getBlock();
        if (block!=Blocks.BARREL && block!=Blocks.CRAFTING_TABLE) return false;
        for (var s:SettlementData.get(p.level().getServer()).state.settlements.values()) {
            if (!sameDimension(p,s) || event.getPos().distSqr(new BlockPos(s.x,s.y,s.z))>12) continue;
            event.setCancellationResult(InteractionResult.SUCCESS);
            if (s.owner.equals(p.getUUID())) SettlementNetwork.open(p,s.id,null); else fail(p,"owner");
            return true;
        }
        return false;
    }
    public static void tick(TickEvent.ServerTickEvent.Post event) {
        if (++ticks%20!=0) return;
        var server=event.server(); var data=SettlementData.get(server);
        Map<UUID,Integer> loaded = new HashMap<>();
        for (var s:data.state.settlements.values()) {
            var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(s.dimension)));
            if (level==null) continue;
            if (loaded.merge(s.owner,1,Integer::sum)<=SettlementConfig.LOADED.get()) {
                int chunks=(SettlementConfig.RADIUS.get()+15)/16;
                for(int dx=-chunks;dx<=chunks;dx++) for(int dz=-chunks;dz<=chunks;dz++)
                    level.getChunkSource().addTicketWithRadius(Settlements.LOADING.get(),new net.minecraft.world.level.ChunkPos((s.x>>4)+dx,(s.z>>4)+dz),2);
            }
            if(level.hasChunkAt(new BlockPos(s.x-4,s.y,s.z-3)) && level.hasChunkAt(new BlockPos(s.x+4,s.y,s.z+3))) {
                int beds=countBeds(level,s);
                if(s.bedCapacity!=beds) {s.bedCapacity=beds;data.setDirty();}
            }
            long day=Math.floorDiv(level.getOverworldClockTime(),24000);
            // Catch up all elapsed days; no unloaded-world food or hunger exploit.
            while (s.lastDay<day) {
                data.state.newDay(s,SettlementConfig.rules(s.bedCapacity),RANDOM,ServerSettlements::nutrition); s.lastDay++; data.setDirty();
            }
            // Reconcile only a fully loaded hall area. Unloaded citizens are never respawned.
            if (ticks%100==0 && level.hasChunkAt(new BlockPos(s.x-4,s.y,s.z-3)) && level.hasChunkAt(new BlockPos(s.x+4,s.y,s.z+3))) {
                for (var r:s.residents) if (!r.spawned) { spawn(level,s,r); data.setDirty(); }
            }
        }
        for (var p:server.getPlayerList().getPlayers()) SettlementNetwork.refresh(p);
    }
    public static void action(ServerPlayer p, SettlementNetwork.Action request) {
        if (request.action().equals("close")) { SettlementNetwork.close(p.getUUID()); return; }
        var data=SettlementData.get(p.level().getServer()); var world=data.state;
        if (request.action().equals("open")) {
            UUID id=null;
            if (!request.settlement().isEmpty()) {
                try { id=world.owned(p.getUUID(),UUID.fromString(request.settlement())).id; }
                catch (IllegalArgumentException ex) { return; }
            } else {
                id=world.settlements.values().stream().filter(s->s.owner.equals(p.getUUID()))
                    .min(Comparator.comparingDouble(s->sameDimension(p,s)?p.distanceToSqr(s.x,s.y,s.z):Double.MAX_VALUE))
                    .map(s->s.id).orElse(null);
            }
            SettlementNetwork.open(p,id,null); return;
        }
        Settlement s;
        try { s=world.owned(p.getUUID(),UUID.fromString(request.settlement())); }
        catch (IllegalArgumentException ex) { return; }
        boolean ok=false;
        switch (request.action()) {
            case "depositCoins", "withdrawCoins" -> ok=world.transfer(p.getUUID(),s.id,request.amount(),request.action().equals("depositCoins"));
            case "rename" -> ok=world.rename(p.getUUID(),s.id,request.text());
            case "hire" -> {
                if (nearHall(p,s)) {
                    s.bedCapacity=countBeds((ServerLevel)p.level(),s);
                    Resident r=world.hire(p.getUUID(),s.id,SettlementConfig.rules(s.bedCapacity));
                    if (r!=null) { spawn((ServerLevel)p.level(),s,r); ok=true; }
                }
            }
            case "profession" -> {
                try { ok=world.assign(p.getUUID(),s.id,UUID.fromString(request.resident()),request.text()); }
                catch (IllegalArgumentException ex) { return; }
            }
            case "depositItem" -> {
                if (nearHall(p,s)) {
                    ItemStack held=p.getMainHandItem();
                    // Virtual stock accepts plain stackable resources only; never erase NBT/components, durability or containers.
                    if (!held.isEmpty() && held.getMaxStackSize()>1 && held.getComponentsPatch().isEmpty()) {
                        int accepted=s.deposit(BuiltInRegistries.ITEM.getKey(held.getItem()).toString(),held.getCount(),SettlementConfig.CAPACITY.get());
                        held.shrink(accepted); ok=accepted>0;
                    }
                }
            }
            case "withdrawItem" -> {
                if (nearHall(p,s) && request.amount()>0 && request.amount()<=64) {
                    var resourceId=Identifier.tryParse(request.text());
                    var item=resourceId==null?null:BuiltInRegistries.ITEM.getValue(resourceId);
                    if (item!=null && item!=Items.AIR) {
                        int n=s.withdraw(request.text(),(int)request.amount());
                        if (n>0) { give(p,new ItemStack(item,n)); ok=true; }
                    }
                }
            }
            case "banner" -> {
                long count=world.settlements.values().stream().filter(v->v.owner.equals(p.getUUID())).count();
                double price=SettlementConfig.BANNER.get()*Math.pow(SettlementConfig.BANNER_SCALE.get(),Math.max(0,count-1));
                if (nearHall(p,s) && price<Long.MAX_VALUE && s.treasury>=(long)Math.ceil(price)) {
                    s.treasury-=(long)Math.ceil(price); give(p,new ItemStack(Settlements.BANNER.get())); ok=true;
                }
            }
        }
        if (ok) data.setDirty(); else fail(p,"action");
        SettlementNetwork.refresh(p);
    }
    static int countBeds(ServerLevel level, Settlement s) {
        int beds=0;
        for(int i=0;i<3;i++) {
            var foot=level.getBlockState(new BlockPos(s.x-2+i*2,s.y,s.z+1));
            var head=level.getBlockState(new BlockPos(s.x-2+i*2,s.y,s.z+2));
            if(foot.getBlock() instanceof BedBlock && head.getBlock()==foot.getBlock()
                && foot.getValue(BedBlock.PART)==BedPart.FOOT && head.getValue(BedBlock.PART)==BedPart.HEAD
                && foot.getValue(BedBlock.FACING)==Direction.SOUTH && head.getValue(BedBlock.FACING)==Direction.SOUTH) beds++;
        }
        return Math.min(beds,SettlementConfig.BEDS.get());
    }
    public static int nutrition(String key) {
        var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
        var food=item==null?null:item.components().get(net.minecraft.core.component.DataComponents.FOOD);
        return food==null?0:food.nutrition();
    }
    private static boolean sameDimension(ServerPlayer p,Settlement s) { return p.level().dimension().identifier().toString().equals(s.dimension); }
    private static boolean nearHall(ServerPlayer p,Settlement s) { return sameDimension(p,s) && p.distanceToSqr(s.x+.5,s.y,s.z+.5)<=64; }
    private static boolean fail(ServerPlayer p,String key) { p.sendSystemMessage(Component.translatable("message.smartsettlements."+key)); return false; }
    private ServerSettlements() {}
}
