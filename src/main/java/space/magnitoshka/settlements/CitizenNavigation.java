package space.magnitoshka.settlements;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** One movement owner per citizen: arrival, completed-path reset and budgeted recovery. */
public final class CitizenNavigation {
    public enum Step { ARRIVED, MOVING, WAITING, BLOCKED }
    private final CitizenEntity citizen;
    private BlockPos goal,observedGoal;
    private int reach=1,lastProgress,retryAt,nodeIndex=-1;
    private double speed=.85,arrivalRange;
    private boolean requireFull,retry,aligning;
    private Vec3 anchor=Vec3.ZERO,alignment;
    public CitizenNavigation(CitizenEntity citizen) {this.citizen=citizen;}
    private void progress() {anchor=citizen.position();lastProgress=citizen.tickCount;}
    public void cancel() {
        citizen.getNavigation().stop();goal=observedGoal=null;retry=aligning=false;nodeIndex=-1;
        citizen.getMoveControl().setWantedPosition(citizen.getX(),citizen.getY(),citizen.getZ(),0);
    }
    private Vec3 destination(BlockPos at) {
        double y=at.getY();var level=citizen.level();
        if(level.hasChunkAt(at)&&level.hasChunkAt(at.below())) {
            var inside=level.getBlockState(at).getCollisionShape(level,at);
            var below=level.getBlockState(at.below()).getCollisionShape(level,at.below());
            if(!inside.isEmpty())y+=inside.max(Direction.Axis.Y);
            else if(inside.isEmpty()&&!below.isEmpty())y=at.getY()-1+below.max(Direction.Axis.Y);
        }
        return new Vec3(at.getX()+.5,y,at.getZ()+.5);
    }
    private boolean clearFinalSegment(BlockPos at,Vec3 point) {
        if(!(citizen.level() instanceof ServerLevel level)||!level.hasChunkAt(at)||!level.hasChunkAt(at.below())
            ||Math.abs(citizen.getY()-point.y)>.55||citizen.distanceToSqr(point)>2.25)return false;
        var floor=BlockPos.containing(point.x,point.y-.01,point.z);
        var feetAt=BlockPos.containing(point);
        if(!level.hasChunkAt(floor)||!level.hasChunkAt(feetAt)||level.getBlockState(floor).getCollisionShape(level,floor).isEmpty()
            ||!level.getFluidState(feetAt).isEmpty())return false;
        var delta=point.subtract(citizen.position());
        for(int i=1;i<=8;i++) {
            var offset=delta.scale(i/8.0);var box=citizen.getBoundingBox().move(offset);
            var feet=BlockPos.containing(citizen.position().add(offset));
            if(!level.hasChunkAt(feet)||!level.hasChunkAt(feet.above())||!level.noCollision(citizen,box))return false;
            var supportAt=BlockPos.containing(box.minX+.3,box.minY-.01,box.minZ+.3);
            if(!level.hasChunkAt(supportAt)||level.getBlockState(supportAt).getCollisionShape(level,supportAt).isEmpty())return false;
        }
        return true;
    }
    public Step approach(BlockPos at,double range,double speed) {
        var point=destination(at);
        if(citizen.distanceToSqr(point)<=range*range) {cancel();return Step.ARRIVED;}
        if(range<=1&&clearFinalSegment(at,point)) {
            if(!aligning||goal==null||!goal.equals(at))progress();
            goal=at.immutable();observedGoal=goal;this.speed=speed;arrivalRange=range;alignment=point;
            reach=0;requireFull=false;aligning=true;retry=false;citizen.getNavigation().stop();
            citizen.getMoveControl().setWantedPosition(point.x,point.y,point.z,speed);return Step.MOVING;
        }
        if(aligning) {aligning=false;citizen.getNavigation().stop();}
        return start(at,range<=1?0:1,speed,false);
    }
    public Step start(BlockPos at,int reach,double speed,boolean full) {return request(at,reach,speed,full,false);}
    public Step detour(BlockPos at,int reach,double speed) {return request(at,reach,speed,true,true);}
    private Step request(BlockPos at,int reach,double speed,boolean full,boolean optional) {
        var nav=citizen.getNavigation();var existing=nav.getPath();
        boolean same=goal!=null&&goal.equals(at);
        boolean matching=same&&this.reach==reach&&requireFull==full;
        if(!optional&&!matching) {
            // A new work goal supersedes the old journey even when this tick has no planning budget.
            nav.stop();goal=at.immutable();this.reach=reach;this.speed=speed;requireFull=full;
            retry=aligning=false;observedGoal=null;nodeIndex=-1;progress();
        }
        if(retry&&same&&citizen.tickCount<retryAt)return Step.WAITING;
        if(!retry&&matching&&!nav.isDone()&&(!full||existing!=null&&existing.canReach()))return Step.MOVING;
        if(!NavigationBudget.take(citizen.level().getServer())) {
            if(!optional) {retry=true;retryAt=citizen.tickCount+jitter();}
            return Step.WAITING;
        }
        if(nav.isDone()||matching&&full&&existing!=null&&!existing.canReach())nav.stop();
        var path=nav.createPath(at,reach);
        if(path==null||full&&!path.canReach()||!nav.moveTo(path,speed)) {
            // Only optional traffic/safety sidesteps preserve the previous intent on failure.
            if(optional) {
                if(existing!=null&&!existing.isDone()&&nav.getPath()!=existing)nav.moveTo(existing,this.speed);
                return Step.BLOCKED;
            }
            retry=true;retryAt=citizen.tickCount+SettlementConfig.REPATH_TICKS.get()+jitter();return Step.BLOCKED;
        }
        goal=at.immutable();this.reach=reach;this.speed=speed;requireFull=full;aligning=false;
        retry=false;observedGoal=path.getTarget();nodeIndex=path.getNextNodeIndex();progress();return Step.MOVING;
    }
    private boolean moved() {
        double dx=citizen.getX()-anchor.x,dz=citizen.getZ()-anchor.z;
        return dx*dx+dz*dz>=.0625;
    }
    private int jitter() {return 1+Math.floorMod(citizen.getUUID().hashCode(),20);}
    private void stalled() {
        citizen.getNavigation().stop();aligning=false;retry=true;retryAt=citizen.tickCount+jitter();
    }
    public void tick() {
        if(citizen.isTalking()||citizen.isSleeping()) {if(goal!=null)cancel();return;}
        int now=citizen.tickCount;
        if(aligning) {
            if(citizen.distanceToSqr(alignment)<=arrivalRange*arrivalRange) {cancel();return;}
            if(moved())progress();
            if(now-lastProgress>=SettlementConfig.REPATH_TICKS.get()) {stalled();return;}
            citizen.getMoveControl().setWantedPosition(alignment.x,alignment.y,alignment.z,speed);return;
        }
        var nav=citizen.getNavigation();var path=nav.getPath();
        if(goal==null) {if(!nav.isDone())nav.stop();return;}
        if(retry) {
            // Vanilla may still have a delayed recomputation scheduled after stop(); it cannot revive an old intent.
            if(!nav.isDone())nav.stop();
            if(now>=retryAt)start(goal,reach,speed,requireFull);
            return;
        }
        if(path==null||path.isDone())return;
        if(observedGoal==null||!observedGoal.equals(path.getTarget())) {stalled();return;}
        if(moved()||nodeIndex<path.getNextNodeIndex()) {nodeIndex=path.getNextNodeIndex();progress();}
        if(now-lastProgress>=SettlementConfig.REPATH_TICKS.get())stalled();
    }
}
