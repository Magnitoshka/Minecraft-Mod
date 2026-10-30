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
        boolean owned=data.state.settlements.values().stream().anyMatch(s->s.owner.equals(p.getUUID()));
        boolean initialized=owned||data.state.initializedPlayers.contains(p.getUUID())||p.getPersistentData().getBooleanOr("SmartSettlementsStarter",false);
        boolean changed=data.state.initializedPlayers.add(p.getUUID());
        p.getPersistentData().putBoolean("SmartSettlementsStarter",true);
        if(!initialized) {
            data.state.wallets.put(p.getUUID(),(long)SettlementConfig.WALLET.get());
            give(p,new ItemStack(Settlements.BANNER.get()));
            give(p,new ItemStack(Items.STONE_PICKAXE));give(p,new ItemStack(Items.STONE_AXE));
            give(p,new ItemStack(Items.STONE_SHOVEL));give(p,new ItemStack(Items.STONE_HOE));
            give(p,new ItemStack(Items.BREAD,SettlementConfig.BREAD.get()));
            p.sendSystemMessage(Component.translatable("message.smartsettlements.welcome"));changed=true;
        }
        if(changed)data.setDirty();
    }
    static void give(ServerPlayer player, ItemStack stack) {
        if (!stack.isEmpty() && !player.getInventory().add(stack)) player.drop(stack,false,net.minecraft.util.Prediction.SERVER_ONLY);
    }
    public static boolean found(ServerPlayer player, BlockPos pos) {
        var level=(ServerLevel)player.level();
        var data=SettlementData.get(level.getServer());
        if(data.state.placementConflict(level.dimension().identifier().toString(),pos.getX(),pos.getZ(),SettlementConfig.rules())!=null)return fail(player,"distance");
        // Validate complete footprint before changing either the world or the saved economy.
        if (pos.getY()<level.getMinY()+1 || pos.getY()+6>=level.getMaxY()) return fail(player,"site");
        for (int x=-4;x<=4;x++) for (int z=-3;z<=3;z++) {
            BlockPos floor=pos.offset(x,-1,z);
            if (!level.getWorldBorder().isWithinBounds(floor) || !level.mayInteract(player,floor)
                    || level.getBlockEntity(floor)!=null || level.getBlockState(floor).getDestroySpeed(level,floor)<0 || !(level.getBlockState(floor).isFaceSturdy(level,floor,Direction.UP)||level.getBlockState(floor).is(net.minecraft.tags.BlockTags.SNOW)||level.getBlockState(floor).is(Blocks.POWDER_SNOW))) return fail(player,"site");
            for (int y=0;y<=5;y++) {
                var target=pos.offset(x,y,z);
                if (!level.mayInteract(player,target)||level.getBlockEntity(target)!=null||!SitePreparation.cover(level.getBlockState(target)) || !level.getFluidState(target).isEmpty()) return fail(player,"site");
            }
        }
        Settlement s;
        try { s=data.state.found(player.getUUID(),level.dimension().identifier().toString(),pos.getX(),pos.getY(),pos.getZ(),
                Math.floorDiv(level.getOverworldClockTime(),24000),SettlementConfig.rules()); }
        catch (IllegalArgumentException ex) { return fail(player,"distance"); }
        // Founding also clears cover from the interior/doorway, not only from future wall blocks.
        for(int x=-4;x<=4;x++)for(int z=-3;z<=3;z++)for(int y=0;y<=5;y++) {
            var at=pos.offset(x,y,z);if(!level.getBlockState(at).isAir())level.setBlock(at,Blocks.AIR.defaultBlockState(),3);
        }
        buildHall(level,pos);s.roofBannerCleaned=true;
        for (var r:s.residents) spawn(level,s,r);
        data.setDirty();
        SettlementNetwork.open(player,s.id,null);
        SettlementNetwork.notice(player,s.id,true,Component.translatable("message.smartsettlements.founded",s.name));
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
    }
    static void spawn(ServerLevel level, Settlement s, Resident r) {
        var citizen=Settlements.CITIZEN.get().create(level,EntitySpawnReason.TRIGGERED);
        if (citizen==null) throw new IllegalStateException("Citizen creation failed");
        citizen.initialize(s.id,r);
        var beds=Construction.beds(level,s);
        var free=beds.stream().filter(bed->s.residents.stream().noneMatch(v->Construction.bedKey(bed).equals(v.bed))).findFirst().orElse(null);
        citizen.setPos(s.x+.5,s.y,s.z-1+.5);
        if(level.addFreshEntity(citizen)) {r.spawned=true;r.bed=free==null?"":Construction.bedKey(free);}
    }
    public static boolean interact(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || event.getHand()!=InteractionHand.MAIN_HAND) return false;
        var block=event.getLevel().getBlockState(event.getPos()).getBlock();
        if (block!=Blocks.BARREL && block!=Blocks.CRAFTING_TABLE) return false;
        for (var s:SettlementData.get(p.level().getServer()).state.settlements.values()) {
            boolean hall=event.getPos().distSqr(new BlockPos(s.x,s.y,s.z))<=12;
            boolean warehouse=s.buildings.stream().anyMatch(b->b.complete&&b.type.equals("warehouse")&&b.contains(event.getPos().getX(),event.getPos().getZ(),0)&&Math.abs(event.getPos().getY()-b.y)<=1);
            if(!sameDimension(p,s)||(!hall&&!warehouse))continue;
            event.setCancellationResult(InteractionResult.SUCCESS);
            if(s.owner.equals(p.getUUID())) {if(block==Blocks.BARREL)WarehouseMenu.open(p,s.id);else SettlementNetwork.open(p,s.id,null);} else fail(p,"owner");
            return true;
        }
        return false;
    }
    public static void tick(TickEvent.ServerTickEvent.Post event) {
        SettlementNetwork.tickTerrain(event.server());
        if (++ticks%20!=0) return;
        var server=event.server(); var data=SettlementData.get(server);
        Map<UUID,Integer> loaded = new HashMap<>();
        for (var s:data.state.settlements.values()) {
            var level=server.getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(s.dimension)));
            if (level==null) continue;
            if(!s.roofBannerCleaned) {
                var flag=new BlockPos(s.x,s.y+5,s.z);
                if(level.hasChunkAt(flag)) {
                    var entity=level.getBlockEntity(flag);
                    boolean generated=level.getBlockState(flag).is(Blocks.BANNER.blue())
                        &&entity instanceof net.minecraft.world.level.block.entity.BannerBlockEntity banner
                        &&banner.getPatterns().layers().isEmpty()&&banner.getCustomName()==null;
                    if(!generated||level.setBlock(flag,Blocks.AIR.defaultBlockState(),3)) {s.roofBannerCleaned=true;data.setDirty();}
                }
            }
            if (loaded.merge(s.owner,1,Integer::sum)<=SettlementConfig.LOADED.get()) {
                int chunks=(Construction.radius(s)+15)/16;
                for(int dx=-chunks;dx<=chunks;dx++) for(int dz=-chunks;dz<=chunks;dz++)
                    level.getChunkSource().addTicketWithRadius(Settlements.LOADING.get(),new net.minecraft.world.level.ChunkPos((s.x>>4)+dx,(s.z>>4)+dz),2);
            }
            if(level.hasChunkAt(new BlockPos(s.x-4,s.y,s.z-3)) && level.hasChunkAt(new BlockPos(s.x+4,s.y,s.z+3))) {
                var beds=Construction.beds(level,s); int count=beds.size();
                if(s.bedCapacity!=count) {s.bedCapacity=count;data.setDirty();}
                var available=new HashSet<String>();for(var bed:beds)available.add(Construction.bedKey(bed));
                var used=new HashSet<String>();
                for(var r:s.residents)if(r.bed!=null&&available.contains(r.bed)&&used.add(r.bed)) {} else {
                    if(r.bed!=null&&!r.bed.isEmpty())data.setDirty();r.bed="";
                }
                available.removeAll(used);
                for(var r:s.residents)if(r.bed.isEmpty()&&!available.isEmpty()) {r.bed=available.iterator().next();available.remove(r.bed);data.setDirty();}
                for(var b:s.buildings)if(!b.complete) {
                    boolean builder=s.residents.stream().anyMatch(r->r.profession.equals("builder")&&r.workEfficiency()>0);
                    if(!builder)b.status="waiting_builder";
                }
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
        for(var p:server.getPlayerList().getPlayers()) {
            if(p.containerMenu instanceof WarehouseMenu menu)SettlementNetwork.refreshWarehouse(p,menu.settlementId);
            else SettlementNetwork.refresh(p);
        }
    }
    public static void action(ServerPlayer p, SettlementNetwork.Action request) {
        if (request.action().equals("close")) { SettlementNetwork.close(p.getUUID()); return; }
        var data=SettlementData.get(p.level().getServer()); var world=data.state;
        if (request.action().equals("open")) {
            UUID id=null;
            if (!request.settlement().isEmpty()) {
                try { id=world.owned(p.getUUID(),UUID.fromString(request.settlement())).id; }
                catch(IllegalArgumentException ex) {fail(p,"owner");return;}
            } else {
                id=world.settlements.values().stream().filter(s->s.owner.equals(p.getUUID()))
                    .min(Comparator.comparingDouble(s->sameDimension(p,s)?p.distanceToSqr(s.x,s.y,s.z):Double.MAX_VALUE))
                    .map(s->s.id).orElse(null);
            }
            SettlementNetwork.open(p,id,null); return;
        }
        Settlement s;
        try { s=world.owned(p.getUUID(),UUID.fromString(request.settlement())); }
        catch(IllegalArgumentException ex) {fail(p,"owner");return;}
        boolean ok=false;Component success=null;
        switch (request.action()) {
            case "warehouse" -> {if(nearStorage(p,s)) {
                WarehouseMenu.open(p,s.id);
                if(p.containerMenu instanceof WarehouseMenu)SettlementNetwork.notice(p,s.id,true,Component.translatable("warehouse.smartsettlements.opened"));
                return;
            }}
            case "build" -> {
                int dx=(int)(request.amount()>>32),dz=(int)request.amount();
                var result=Construction.request(p,s,request.text(),dx,dz);
                ok=result.ok();
                var message=result.message();
                if(ok)success=message;else SettlementNetwork.notice(p,s.id,false,message);
            }
            case "depositCoins", "withdrawCoins" -> {
                ok=world.transfer(p.getUUID(),s.id,request.amount(),request.action().equals("depositCoins"));
                if(ok)success=Component.translatable("action.smartsettlements."+request.action(),request.amount());
            }
            case "rename" -> {ok=world.rename(p.getUUID(),s.id,request.text());if(ok)success=Component.translatable("action.smartsettlements.rename",s.name);}
            case "hire" -> {
                if (nearHall(p,s)) {
                    s.bedCapacity=countBeds((ServerLevel)p.level(),s);
                    long treasuryBefore=s.treasury;
                    Resident r=world.hire(p.getUUID(),s.id,SettlementConfig.rules(s.bedCapacity));
                    if(r!=null) {
                        spawn((ServerLevel)p.level(),s,r);ok=r.spawned;
                        if(ok)success=Component.translatable("action.smartsettlements.hire",r.name);
                        else {s.residents.remove(r);s.treasury=treasuryBefore;}
                    }
                }
            }
            case "profession" -> {
                try { ok=world.assign(p.getUUID(),s.id,UUID.fromString(request.resident()),request.text()); }
                catch(IllegalArgumentException ex) {fail(p,"action");return;}
                if(ok)success=Component.translatable("action.smartsettlements.profession",Component.translatable("profession.smartsettlements."+request.text()));
            }
            case "depositItem" -> {
                if (nearStorage(p,s)) {
                    ItemStack held=p.getMainHandItem();
                    // Virtual stock accepts plain stackable resources only; never erase NBT/components, durability or containers.
                    if(WarehouseMenu.accepts(held)) {
                        int accepted=s.deposit(BuiltInRegistries.ITEM.getKey(held.getItem()).toString(),held.getCount(),Construction.capacity(s));
                        held.shrink(accepted);ok=accepted>0;
                        if(ok)success=Component.translatable("warehouse.smartsettlements.deposited",accepted);
                    }
                }
            }
            case "withdrawItem" -> {
                if (nearStorage(p,s) && request.amount()>0 && request.amount()<=64) {
                    var resourceId=Identifier.tryParse(request.text());
                    var item=resourceId==null?null:BuiltInRegistries.ITEM.getValue(resourceId);
                    if (item!=null && item!=Items.AIR) {
                        int n=s.withdraw(request.text(),(int)request.amount());
                        if(n>0) {give(p,new ItemStack(item,n));ok=true;success=Component.translatable("warehouse.smartsettlements.withdrawn",n);}
                    }
                }
            }
            case "banner" -> {
                long count=world.settlements.values().stream().filter(v->v.owner.equals(p.getUUID())).count();
                double price=SettlementConfig.BANNER.get()*Math.pow(SettlementConfig.BANNER_SCALE.get(),Math.max(0,count-1));
                if (nearHall(p,s) && price<Long.MAX_VALUE && s.treasury>=(long)Math.ceil(price)) {
                    s.treasury-=(long)Math.ceil(price);give(p,new ItemStack(Settlements.BANNER.get()));ok=true;
                    success=Component.translatable("action.smartsettlements.banner",(long)Math.ceil(price));
                }
            }
        }
        if(ok) {
            data.setDirty();
            if(success!=null)SettlementNetwork.notice(p,s.id,true,success);
        } else if(!request.action().equals("build"))fail(p,"action");
        if(p.containerMenu instanceof WarehouseMenu)SettlementNetwork.refreshWarehouse(p,s.id);else SettlementNetwork.refresh(p);
    }
    static int countBeds(ServerLevel level, Settlement s) {return Construction.beds(level,s).size();}
    public static int nutrition(String key) {
        var item=BuiltInRegistries.ITEM.getValue(Identifier.parse(key));
        var food=item==null?null:item.components().get(net.minecraft.core.component.DataComponents.FOOD);
        return food==null?0:food.nutrition();
    }
    private static boolean sameDimension(ServerPlayer p,Settlement s) { return p.level().dimension().identifier().toString().equals(s.dimension); }
    static boolean nearStorage(ServerPlayer p,Settlement s) {
        return nearHall(p,s)||(sameDimension(p,s)&&s.buildings.stream().anyMatch(b->b.complete&&b.type.equals("warehouse")&&p.distanceToSqr(b.x+.5,b.y,b.z+.5)<=64));
    }
    private static boolean nearHall(ServerPlayer p,Settlement s) { return sameDimension(p,s) && p.distanceToSqr(s.x+.5,s.y,s.z+.5)<=64; }
    private static boolean fail(ServerPlayer p,String key) {SettlementNetwork.notice(p,null,false,Component.translatable("message.smartsettlements."+key));return false;}
    private ServerSettlements() {}
}
