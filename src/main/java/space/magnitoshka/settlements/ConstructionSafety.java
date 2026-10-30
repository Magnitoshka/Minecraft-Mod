package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import space.magnitoshka.settlements.domain.*;

/** Check at the actual write, before consuming material or advancing a cursor. */
public final class ConstructionSafety {
    public static boolean permits(ServerLevel level,BlockPos at,BlockState target) {
        var shape=target.getCollisionShape(level,at);
        var previous=level.getBlockState(at).getCollisionShape(level,at);
        var bounds=new AABB(at).inflate(1,2,1);
        for(var entity:level.getEntitiesOfClass(LivingEntity.class,bounds,LivingEntity::isAlive)) {
            var body=entity.getBoundingBox().inflate(.03);
            for(var part:shape.toAabbs())if(part.move(at.getX(),at.getY(),at.getZ()).intersects(body))return false;
            // Excavation must not remove the support directly beneath somebody's feet.
            if(!previous.isEmpty()) {
                var feet=new AABB(body.minX,entity.getBoundingBox().minY-.12,body.minZ,
                    body.maxX,entity.getBoundingBox().minY+.01,body.maxZ);
                boolean before=false,after=false;
                for(var part:previous.toAabbs())if(part.move(at.getX(),at.getY(),at.getZ()).intersects(feet)) {before=true;break;}
                for(var part:shape.toAabbs())if(part.move(at.getX(),at.getY(),at.getZ()).intersects(feet)) {after=true;break;}
                if(before&&!after)return false;
            }
        }
        return true;
    }
    public static BlockPos stand(Building job) {
        var type=BuildingType.byId(job.type);
        return job.hasStand?new BlockPos(job.standX,job.standY,job.standZ):new BlockPos(job.x,job.y,job.z-type.halfDepth-1);
    }
    public static void yield(CitizenEntity citizen,ServerLevel level,Building job,Resident r,BlockPos at,BlockState target) {
        r.workStatus="safety_wait";job.status="safety_wait";
        if(!citizen.getNavigation().isDone())return;
        var destinations=new ArrayList<BlockPos>();destinations.add(stand(job));
        for(int distance:new int[]{2,3,1})for(int dy:new int[]{0,1,-1})for(var direction:net.minecraft.core.Direction.Plane.HORIZONTAL)
            destinations.add(citizen.blockPosition().relative(direction,distance).offset(0,dy,0));
        for(var candidate:destinations) {
            if(!level.hasChunkAt(candidate)||!SitePreparation.stable(level,candidate.below()))continue;
            if(candidate.equals(at)||candidate.equals(at.above()))continue;
            var box=citizen.getBoundingBox().move(candidate.getX()+.5-citizen.getX(),candidate.getY()-citizen.getY(),candidate.getZ()+.5-citizen.getZ());
            if(!level.noCollision(citizen,box))continue;
            boolean threatened=false;
            for(var part:target.getCollisionShape(level,at).toAabbs())if(part.move(at.getX(),at.getY(),at.getZ()).intersects(box.inflate(.05))) {threatened=true;break;}
            if(threatened)continue;
            if(!NavigationBudget.take(level.getServer()))return;
            var path=citizen.getNavigation().createPath(candidate,0);
            if(path!=null&&path.canReach()) {citizen.getNavigation().moveTo(path,.85);return;}
        }
    }
    private ConstructionSafety() {}
}
