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
    public CitizenEntity(EntityType<? extends CitizenEntity> type, Level level) { super(type,level); setPersistenceRequired(); }
    public static AttributeSupplier.Builder attributes() {
        return createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.25).add(Attributes.FOLLOW_RANGE,24);
    }
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(5,new LookAtPlayerGoal(this,Player.class,8));
        goalSelector.addGoal(6,new RandomLookAroundGoal(this));
    }
    public void initialize(UUID settlement, Resident resident) {
        settlementId=settlement; setUUID(resident.id); setCustomName(Component.literal(resident.name)); setCustomNameVisible(true);
    }
    @Override public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel server) || settlementId==null || tickCount%20!=0) return;
        var data=SettlementData.get(server.getServer());
        var s=data.state.settlements.get(settlementId);
        if (s==null) { discard(); return; }
        var r=s.residents.stream().filter(v->v.id.equals(getUUID())).findFirst().orElse(null);
        if (r==null) { discard(); return; }
        setCustomName(Component.literal(r.name).append(Component.literal(" · ")).append(Component.translatable("profession.smartsettlements."+r.profession)));
        int index=s.residents.indexOf(r);
        BlockPos bed=new BlockPos(s.x-2+index*2,s.y,s.z+1);
        if (!r.bed.isEmpty()) {
            String[] xyz=r.bed.split(",");
            if(xyz.length==3)bed=new BlockPos(Integer.parseInt(xyz[0]),Integer.parseInt(xyz[1]),Integer.parseInt(xyz[2]));
        }
        boolean hasBed=server.getBlockState(bed).getBlock() instanceof net.minecraft.world.level.block.BedBlock;
        int mood=Math.max(0,100-r.hungerDays*50-(hasBed?0:25));
        if(r.mood!=mood) { r.mood=mood; data.setDirty(); }
        long time=Math.floorMod(server.getOverworldClockTime(),24000);
        if (time>=12500 && time<23500 && hasBed) {
            if (!isSleeping()) {
                if (distanceToSqr(bed.getX()+.5,bed.getY()+.5,bed.getZ()+.5)<4) startSleeping(bed);
                else getNavigation().moveTo(bed.getX()+.5,bed.getY(),bed.getZ()+.5,.8);
            }
        } else {
            if (isSleeping()) stopSleeping();
            if (getNavigation().isDone() && tickCount%100==0) {
                int dx=getRandom().nextInt(9)-4, dz=getRandom().nextInt(9)-4;
                getNavigation().moveTo(s.x+dx+.5,s.y,s.z+dz+.5,.6);
            }
        }
    }
    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp && settlementId!=null) {
            var s=SettlementData.get(sp.level().getServer()).state.settlements.get(settlementId);
            if (s!=null && s.owner.equals(sp.getUUID())) SettlementNetwork.open(sp,s.id,getUUID());
            else sp.sendSystemMessage(Component.translatable("message.smartsettlements.owner"));
        }
        return InteractionResult.SUCCESS;
    }
    @Override public void die(DamageSource cause) {
        if (level() instanceof ServerLevel server && settlementId!=null) {
            var data=SettlementData.get(server.getServer());
            var s=data.state.settlements.get(settlementId);
            if (s!=null) { s.residents.removeIf(r->r.id.equals(getUUID())); data.setDirty(); }
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
