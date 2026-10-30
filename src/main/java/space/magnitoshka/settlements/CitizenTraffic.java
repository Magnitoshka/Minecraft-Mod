package space.magnitoshka.settlements;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Deterministic right of way. A bounded sidestep, or backing up in a narrow lane. */
public final class CitizenTraffic {
    private int until,cooldown;
    public boolean tick(CitizenEntity citizen,ServerLevel level) {
        if(citizen.tickCount<until)return true;
        if(citizen.tickCount<cooldown||citizen.tickCount%5!=0||citizen.isSleeping()||citizen.getNavigation().isDone())return false;
        var pathAhead=citizen.getNavigation().getPath();
        if(pathAhead==null||pathAhead.isDone())return false;
        var next=pathAhead.getNextNodePos();
        var heading=Vec3.atBottomCenterOf(next).subtract(citizen.position()).multiply(1,0,1).normalize();
        for(var other:level.getEntitiesOfClass(CitizenEntity.class,citizen.getBoundingBox().inflate(1.2,.3,1.2))) {
            if(other==citizen||other.isSleeping())continue;
            var delta=other.position().subtract(citizen.position()).multiply(1,0,1);
            if(delta.lengthSqr()>2.25||delta.dot(heading)<=.1)continue;
            if(!other.isTalking()&&citizen.getUUID().compareTo(other.getUUID())<0)continue;
            // The same side is tried first, preventing two residents from mirroring each other.
            Vec3 side=new Vec3(-heading.z,0,heading.x);
            for(var offset:new Vec3[]{side.scale(1.5),side.scale(-1.5),heading.scale(-2.5),heading.scale(-1)}) {
                var at=BlockPos.containing(citizen.position().add(offset));
                if(!level.hasChunkAt(at)||!SitePreparation.stable(level,at.below()))continue;
                var box=citizen.getBoundingBox().move(at.getX()+.5-citizen.getX(),at.getY()-citizen.getY(),at.getZ()+.5-citizen.getZ());
                if(!level.noCollision(citizen,box))continue;
                var route=citizen.routes().detour(at,0,.85);
                if(route==CitizenNavigation.Step.WAITING)return false;
                if(route==CitizenNavigation.Step.BLOCKED)continue;
                until=citizen.tickCount+30;cooldown=until+30;return true;
            }
            // No safe pocket: wait briefly, then retry after the other resident moves.
            citizen.routes().cancel();until=citizen.tickCount+15;cooldown=until;return true;
        }
        return false;
    }
}
