package space.magnitoshka.settlements;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import space.magnitoshka.settlements.domain.*;

/** Assign only unemployed, empty-handed residents. Manual professions are never changed. */
public final class AutomaticProfessions {
    private record Needs(boolean food,int farmers,int builders,boolean delivery,boolean wood,boolean stone) {}
    public static void tick(ServerLevel level,SettlementData data,Settlement s) {
        if(!SettlementConfig.AUTO_PROFESSIONS.get()||!level.hasChunkAt(new BlockPos(s.x,s.y,s.z)))return;
        // Spread settlements across interval seconds instead of scanning every village in the same second.
        if(Math.floorMod(level.getServer().getTickCount()/20L+s.id.hashCode(),SettlementConfig.AUTO_ASSIGN_SECONDS.get())!=0)return;
        int jobs=(int)s.buildings.stream().filter(b->!b.complete||b.repairing).count();
        for(var r:s.residents)if(r.autoAssigned&&r.profession.equals("builder")&&jobs==0&&empty(r)) {
            if(data.state.assign(s.owner,s.id,r.id,"unemployed"))data.setDirty();
        }
        var needs=needs(level,s,jobs);
        for(var r:s.residents)if(r.spawned&&r.profession.equals("unemployed")&&empty(r)&&r.workEfficiency()>0) {
            String profession=choose(s,needs);
            if(profession.isEmpty())break;
            if(data.state.assign(s.owner,s.id,r.id,profession)) {
                r.autoAssigned=true;data.setDirty();
                SettlementNetwork.ownerNotice(level.getServer(),s,true,"action.smartsettlements.auto_profession",r.name,
                    net.minecraft.network.chat.Component.translatable("profession.smartsettlements."+profession));
            }
        }
    }
    private static boolean empty(Resident r) {return r.builderInventory.size()==0&&r.cargoJob.isEmpty()&&r.taskBuilding.isEmpty();}
    private static long workers(Settlement s,String profession) {
        return s.residents.stream().filter(r->r.profession.equals(profession)&&r.workEfficiency()>0).count();
    }
    private static boolean vacancy(Settlement s,String type) {
        return s.buildings.stream().filter(b->b.complete&&b.type.equals(type)).anyMatch(b->
            s.residents.stream().filter(r->b.id.equals(r.workplace)).count()<b.workplaces());
    }
    private static String choose(Settlement s,Needs n) {
        if(n.food&&workers(s,"farmer")<n.farmers&&vacancy(s,"field"))return "farmer";
        long builders=workers(s,"builder");
        if(n.builders>0&&builders==0)return "builder";
        boolean wood=n.wood&&vacancy(s,"lumber_hut"),stone=n.stone&&vacancy(s,"mine");
        // Supply the first builder before allocating every spare resident to parallel construction.
        if(wood&&workers(s,"lumberjack")==0)return "lumberjack";
        if(stone&&workers(s,"miner")==0)return "miner";
        if(n.delivery&&workers(s,"courier")==0&&vacancy(s,"warehouse"))return "courier";
        if(builders<n.builders)return "builder";
        if(wood&&stone)return workers(s,"lumberjack")<=workers(s,"miner")?"lumberjack":"miner";
        if(wood)return "lumberjack";
        if(stone)return "miner";
        return "";
    }
    private static Needs needs(ServerLevel level,Settlement s,int jobs) {
        long nutrition=Math.max(0,s.foodCredit);
        for(var entry:s.resources.entrySet())nutrition+=(long)entry.getValue()*ServerSettlements.nutrition(entry.getKey());
        long target=(long)s.residents.size()*SettlementConfig.DAILY_FOOD.get()*5*SettlementConfig.FOOD_DAYS.get();
        long wood=s.resources.entrySet().stream().filter(e->Construction.isWood(e.getKey())).mapToLong(Map.Entry::getValue).sum();
        boolean goods=false;
        for(var hut:s.buildings)if(hut.complete&&hut.type.equals("lumber_hut")) {
            var inventory=HutStorage.get(level,hut);if(inventory==null)continue;
            for(int slot=0;slot<inventory.getContainerSize();slot++) {
                var stack=inventory.getItem(slot);if(!HutStorage.transportable(stack))continue;
                goods=true;if(Construction.isWood(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()))wood+=stack.getCount();
            }
        }
        for(var r:s.residents)for(var slot:r.builderInventory.slots)
            if(slot.count>0&&(slot.origin.equals("harvest")||slot.origin.equals("courier"))&&Construction.isWood(slot.item))wood+=slot.count;
        boolean room=s.stored()<Construction.capacity(s);
        int perFarmer=SettlementConfig.AUTO_RESIDENTS_PER_FARMER.get();
        int farmers=Math.max(1,(s.residents.size()+perFarmer-1)/perFarmer);
        return new Needs(room&&nutrition<target,farmers,Math.min(jobs,SettlementConfig.AUTO_BUILDERS.get()),room&&goods,
            room&&wood<SettlementConfig.AUTO_WOOD.get(),room&&s.resources.getOrDefault("minecraft:cobblestone",0)<SettlementConfig.AUTO_STONE.get());
    }
    private AutomaticProfessions() {}
}
