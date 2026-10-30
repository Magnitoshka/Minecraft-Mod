package space.magnitoshka.settlements;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import space.magnitoshka.settlements.domain.Resident;

public final class CitizenEntity extends PathfinderMob {
    private UUID settlementId;
    private final CitizenWork work = new CitizenWork();
    private boolean nightRoutine;
    private final CitizenTraffic traffic=new CitizenTraffic();
    private final CitizenNavigation routes=new CitizenNavigation(this);
    public CitizenNavigation routes() {return routes;}
    private boolean talking;
    private ItemStack displayHand=ItemStack.EMPTY;
    private boolean legacyHand,deathLootHandled;
    @Override public void setItemSlot(EquipmentSlot slot,ItemStack stack) {
        if(slot==EquipmentSlot.MAINHAND) {displayHand=ItemStack.EMPTY;legacyHand=false;}
        super.setItemSlot(slot,stack);
    }
    public boolean isDisplayHand() {
        var hand=getMainHandItem();
        return !displayHand.isEmpty()&&hand.getCount()==displayHand.getCount()&&ItemStack.isSameItemSameComponents(hand,displayHand);
    }
    public void showWorkHand(ItemStack stack) {
        // Work/cargo displays must not replace genuine equipment supplied by another system.
        if(!getMainHandItem().isEmpty()&&!isDisplayHand())return;
        setItemSlot(EquipmentSlot.MAINHAND,stack);displayHand=stack.copy();
    }
    public void recognizeLegacyHand(Resident resident) {
        if(!legacyHand)return;
        legacyHand=false;var hand=getMainHandItem();
        if(hand.isEmpty()||!hand.getComponentsPatch().isEmpty())return;
        // Old saves did not mark displays, and profession may have changed before the next work tick.
        var tools=java.util.List.of(Items.STONE_AXE,Items.IRON_AXE,Items.DIAMOND_AXE,
            Items.STONE_PICKAXE,Items.IRON_PICKAXE,Items.DIAMOND_PICKAXE,Items.STONE_HOE,Items.IRON_HOE,Items.DIAMOND_HOE);
        if(hand.getCount()==1&&tools.stream().anyMatch(hand::is)||hand.is(ItemTags.LOGS)||hand.is(ItemTags.SAPLINGS))displayHand=hand.copy();
    }
    public boolean isTalking() {return talking;}
    @Override public void push(net.minecraft.world.entity.Entity entity) {
        if(!(entity instanceof CitizenEntity))super.push(entity);
    }
    @Override public boolean isPushable() {return !talking&&super.isPushable();}
    public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) { super(type,level); setPersistenceRequired(); }
    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,48);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(5,new LookAtPlayerGoal(this,Player.class,8));
        goalSelector.addGoal(6,new RandomLookAroundGoal(this));
    }
    public void initialize(UUID settlement, Resident resident) {
        settlementId=settlement; setUUID(resident.id); setCustomName(Component.literal(resident.name)); setCustomNameVisible(true);
    }
    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
        if(settlementId!=null && source.getEntity() instanceof Player attacker) {
            var s=SettlementData.get(level.getServer()).state.settlements.get(settlementId);
            if(s!=null && s.owner.equals(attacker.getUUID()))return false;
        }
        return super.hurtServer(level,source,damage);
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || settlementId==null) return;
        talking=SettlementNetwork.talking(this);
        if(talking) {routes.cancel();setDeltaMovement(0,getDeltaMovement().y,0);if(isSleeping())stopSleeping();}
        routes.tick();
        boolean yielding=!talking&&traffic.tick(this,server);
        if(tickCount%20!=0)return;
        var data=SettlementData.get(server.getServer());
        var s=data.state.settlements.get(settlementId);
        if (s==null) { discard(); return; }
        var r=s.residents.stream().filter(v->v.id.equals(getUUID())).findFirst().orElse(null);
        if (r==null) { discard(); return; }
        recognizeLegacyHand(r);
        var follow=getAttribute(Attributes.FOLLOW_RANGE);
        if(follow!=null&&follow.getBaseValue()<48)follow.setBaseValue(48);
        if(talking||yielding) {r.workStatus=talking?"talking":"yielding";updateActivity(s,r);return;}
        BlockPos bed=null;
        if(r.bed!=null&&!r.bed.isEmpty()) {
            String[] xyz=r.bed.split(",");
            try {if(xyz.length==3)bed=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));}
            catch(NumberFormatException ignored) {}
        }
        boolean hasBed=bed!=null && server.hasChunkAt(bed) && server.getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.BedBlock;
        int mood=Math.max(0,100-r.hungerDays*50-(hasBed?0:25));
        if(r.mood!=mood) { r.mood=mood; data.setDirty(); }
        long time=Math.floorMod(server.getOverworldClockTime(),24000);
        boolean night=time>=12500&&time<23500;
        if(night!=nightRoutine) {routes.cancel();nightRoutine=night;}
        if(night) {
            r.workStatus="resting";
            if(!hasBed) {if(isSleeping())stopSleeping();routes.cancel();updateActivity(s,r);return;}
            if(!isSleeping()) {
                if(distanceToSqr(bed.getX()+.5,bed.getY()+.5,bed.getZ()+.5)<4) {routes.cancel();startSleeping(bed);}
                else if(getNavigation().isDone())routes.start(bed,1,.8,false);
            }
        } else {
            if(isSleeping()) {stopSleeping();routes.cancel();}
            work.tick(this,server,data,s,r);
            if(r.profession.equals("unemployed")&&getNavigation().isDone()&&tickCount%100==0) {
                int dx=getRandom().nextInt(9)-4,dz=getRandom().nextInt(9)-4;
                routes.start(new BlockPos(s.x+dx,s.y,s.z+dz),1,.6,false);
            }
        }
        updateActivity(s,r);
    }
    private void updateActivity(space.magnitoshka.settlements.domain.Settlement s,Resident r) {
        String status=r.workStatus==null||r.workStatus.isEmpty()?"idle":r.workStatus;
        Component activity=Component.translatable("ui.smartsettlements.status."+status);
        if(r.profession.equals("builder")) {
            if(status.equals("walking"))activity=Component.translatable("activity.smartsettlements.walking_builder");
            if(status.equals("working")) {
                var job=s.buildings.stream().filter(b->b.id.equals(r.taskBuilding)).findFirst().orElse(null);
                if(job!=null)activity=Component.translatable("activity.smartsettlements.building",Component.translatable("activity.smartsettlements.target."+job.type));
            }
        }
        var name=Component.literal(r.name).append(" · ").append(activity.copy().withStyle(net.minecraft.ChatFormatting.ITALIC));
        if(!name.equals(getCustomName()))setCustomName(name);
    }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && settlementId!=null) {
            var s=SettlementData.get(sp.level().getServer()).state.settlements.get(settlementId);
            if (s!=null && s.owner.equals(sp.getUUID())) {routes.cancel();SettlementNetwork.open(sp,s.id,getUUID());}
            else sp.sendSystemMessage(Component.translatable("message.smartsettlements.owner"));
        }
        return InteractionResult.SUCCESS;
    }
    @Override protected void dropCustomDeathLoot(ServerLevel level,DamageSource source,boolean playerKilled) {
        // Mob's implementation randomly drops/damages equipment; owned citizen equipment is handled below.
    }
    @Override protected void dropEquipment(ServerLevel level) {
        if(deathLootHandled)return;
        deathLootHandled=true;
        var data=SettlementData.get(level.getServer());
        var settlement=settlementId==null?null:data.state.settlements.get(settlementId);
        var resident=settlement==null?null:settlement.residents.stream().filter(r->r.id.equals(getUUID())).findFirst().orElse(null);
        CitizenDrops.drop(this,level,settlement,resident);data.setDirty();
    }
    @Override public void die(DamageSource cause) {
        super.die(cause);
        // Forge can cancel LivingDeath. No state/cargo changes occur until vanilla accepted the death.
        if(!dead||!(level() instanceof ServerLevel server))return;
        var data=SettlementData.get(server.getServer());
        var settlement=settlementId==null?null:data.state.settlements.get(settlementId);
        if(!deathLootHandled) {
            // A killer may suppress/defer vanilla death loot; do not force a second drop pipeline.
            deathLootHandled=true;
            var resident=settlement==null?null:settlement.residents.stream().filter(r->r.id.equals(getUUID())).findFirst().orElse(null);
            CitizenDrops.suppressed(this,server,settlement,resident);data.setDirty();
        }
        if(settlement!=null) {
            settlement.residents.stream().filter(r->r.id.equals(getUUID())).findFirst()
                .ifPresent(r->space.magnitoshka.settlements.domain.BuilderAssignments.detach(settlement,r));
            if(settlement.residents.removeIf(r->r.id.equals(getUUID())))data.setDirty();
        }
        routes.cancel();
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (settlementId!=null) out.putString("Settlement",settlementId.toString());
        out.putBoolean("DisplayHandKnown",!legacyHand);out.store("DisplayHand",ItemStack.OPTIONAL_CODEC,displayHand);
        out.putBoolean("DeathLootHandled",deathLootHandled);
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        String id=in.getStringOr("Settlement","");
        if (!id.isEmpty()) settlementId=UUID.fromString(id);
        legacyHand=!in.getBooleanOr("DisplayHandKnown",false);
        displayHand=in.read("DisplayHand",ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
        deathLootHandled=in.getBooleanOr("DeathLootHandled",false);
    }
}
