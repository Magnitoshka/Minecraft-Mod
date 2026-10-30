package space.magnitoshka.settlements;

import space.magnitoshka.settlements.domain.*;

/** Reservation ledger and material demand, independent from movement/GUI. */
public final class BuilderLogistics {
    public static void initialize(Building job) {
        if(job.logisticsInitialized)return;
        var type=BuildingType.byId(job.type);if(type==null)return;
        job.materialProgress=Math.min(job.total,job.progress);
        // Old versions already paid the price. Materialize only their unspent contract balance.
        job.reserved.put("minecraft:oak_log",price(job,true)-paid(price(job,true),job.materialProgress,job.total));
        job.reserved.put("minecraft:cobblestone",price(job,false)-paid(price(job,false),job.materialProgress,job.total));
        if(job.foundationPrepaid)job.prepaidFill=(int)job.preparation.stream().skip(job.prepared).filter(e->e.fill).count();
        job.logisticsInitialized=true;
    }
    public static int price(Building job,boolean wood) {
        int stored=wood?job.priceWood:job.priceStone;
        if(stored>=0)return stored;
        var type=BuildingType.byId(job.type);return wood?type.woodCost:type.stoneCost;
    }
    private static int paid(int price,int cursor,int total) {return total<=0?price:(int)((long)price*Math.clamp(cursor,0,total)/total);}
    public static int charge(Building job,boolean wood) {
        if(job.progress<job.materialProgress)return 0;
        int price=price(job,wood);
        return paid(price,job.progress+1,job.total)-paid(price,job.progress,job.total);
    }
    /** Material can reach one work step in several physical trips, even with high configured prices. */
    public static void stageBase(Resident r,Building job) {
        job.stagedWood+=r.builderInventory.take("base",Construction::isWood,Math.max(0,charge(job,true)-job.stagedWood));
        job.stagedStone+=r.builderInventory.take("base",key->key.equals("minecraft:cobblestone"),Math.max(0,charge(job,false)-job.stagedStone));
    }
    public static boolean hasBase(Resident r,Building job) {
        return job.stagedWood>=charge(job,true)&&job.stagedStone>=charge(job,false);
    }
    public static void spendBase(Resident r,Building job) {
        job.stagedWood-=charge(job,true);job.stagedStone-=charge(job,false);
        job.materialProgress=Math.max(job.materialProgress,job.progress+1);
    }
    public static String fillOrigin(Building job) {return job.foundationPrepaid?"prepaid_fill":"fill";}
    public static boolean hasFill(Resident r,Building job) {return r.builderInventory.count(fillOrigin(job),key->key.equals("minecraft:cobblestone"))>0;}
    public static void spendFill(Resident r,Building job) {r.builderInventory.take(fillOrigin(job),key->key.equals("minecraft:cobblestone"),1);}
    public static int load(Settlement s,Resident r,Building job,boolean foundation) {
        BuilderAssignments.unload(s,r);r.cargoJob=job.id;
        if(foundation) {
            int amount=job.foundationPrepaid?job.prepaidFill:s.resources.getOrDefault("minecraft:cobblestone",0);
            int moved=r.builderInventory.add("minecraft:cobblestone",fillOrigin(job),Math.min(amount,Math.max(1,job.foundationRemaining>0?job.foundationRemaining:job.prepaidFill)));
            if(job.foundationPrepaid)job.prepaidFill-=moved;else s.withdraw("minecraft:cobblestone",moved);
        } else {
            // Stone gets a slot first even if the wood reservation spans many species.
            var keys=job.reserved.keySet().stream().sorted((a,b)->Boolean.compare(!a.equals("minecraft:cobblestone"),!b.equals("minecraft:cobblestone"))).toList();
            for(var key:keys) {
                int amount=job.reserved.getOrDefault(key,0);
                if(key.equals("minecraft:cobblestone"))amount=Math.min(amount,64);
                int moved=r.builderInventory.add(key,"base",amount);
                job.reserved.computeIfPresent(key,(k,n)->n-moved);
            }
        }
        return r.builderInventory.size();
    }
    public static boolean hasFoundationWork(Building job) {
        return job.foundationRemaining>0||(job.foundationPrepaid&&job.preparation.stream().skip(job.prepared).anyMatch(e->e.fill));
    }
    private BuilderLogistics() {}
}
