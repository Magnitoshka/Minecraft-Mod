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
    private boolean talking;
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
        if(talking) {getNavigation().stop();setDeltaMovement(0,getDeltaMovement().y,0);if(isSleeping())stopSleeping();}
        boolean yielding=!talking&&traffic.tick(this,server);
        if(tickCount%20!=0)return;
        var data=SettlementData.get(server.getServer());
        var s=data.state.settlements.get(settlementId);
        if (s==null) { discard(); return; }
        var r=s.residents.stream().filter(v->v.id.equals(getUUID())).findFirst().orElse(null);
        if (r==null) { discard(); return; }
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
        if(night!=nightRoutine) {getNavigation().stop();nightRoutine=night;}
        if(night) {
            r.workStatus="resting";
            if(!hasBed) {if(isSleeping())stopSleeping();getNavigation().stop();updateActivity(s,r);return;}
            if(!isSleeping()) {
                if(distanceToSqr(bed.getX()+.5,bed.getY()+.5,bed.getZ()+.5)<4) {getNavigation().stop();startSleeping(bed);}
                else if(getNavigation().isDone()&&NavigationBudget.take(server.getServer()))getNavigation().moveTo(bed.getX()+.5,bed.getY(),bed.getZ()+.5,.8);
            }
        } else {
            if(isSleeping()) {stopSleeping();getNavigation().stop();}
            work.tick(this,server,data,s,r);
            if(r.profession.equals("unemployed")&&getNavigation().isDone()&&tickCount%100==0&&NavigationBudget.take(server.getServer())) {
                int dx=getRandom().nextInt(9)-4,dz=getRandom().nextInt(9)-4;
                getNavigation().moveTo(s.x+dx+.5,s.y,s.z+dz+.5,.6);
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
            if (s!=null && s.owner.equals(sp.getUUID())) {getNavigation().stop();SettlementNetwork.open(sp,s.id,getUUID());}
            else sp.sendSystemMessage(Component.translatable("message.smartsettlements.owner"));
        }
        return InteractionResult.SUCCESS;
    }
    @Override public void die(DamageSource cause) {
        if (level() instanceof ServerLevel server && settlementId!=null) {
            var data=SettlementData.get(server.getServer());
            var s=data.state.settlements.get(settlementId);
            if (s!=null) { s.residents.stream().filter(r->r.id.equals(getUUID())).findFirst().ifPresent(r->space.magnitoshka.settlements.domain.BuilderAssignments.retire(s,r));s.residents.removeIf(r->r.id.equals(getUUID())); data.setDirty(); }
        }
        super.die(cause);
    }
    @Override protected void addAdditionalSaveData(ValueOutput out) {
        super.addAdditionalSaveData(out);
        if (settlementId!=null) out.putString("Settlement",settlementId.toString());
    }
    @Override protected void readAdditionalSaveData(ValueInput in) {
        super.readAdditionalSaveData(in);
        String id=in.getStringOr("Settlement","");
        if (!id.isEmpty()) settlementId=UUID.fromString(id);
    }
}
