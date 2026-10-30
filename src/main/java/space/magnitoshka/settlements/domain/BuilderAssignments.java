package space.magnitoshka.settlements.domain;

/** One authoritative owner per building; all transfers happen on the server's single writer. */
public final class BuilderAssignments {
    public static Building claim(Settlement settlement,Resident resident,long now) {
        for(var b:settlement.buildings)if((!b.complete||b.repairing)&&b.assignedBuilder.equals(resident.id.toString())) {resident.taskBuilding=b.id;return b;}
        for(var b:settlement.buildings)if(!b.complete&&b.workRetryAt<=now&&b.assignedBuilder.isEmpty()) {
            b.assignedBuilder=resident.id.toString();resident.taskBuilding=b.id;resident.supplyReady=false;resident.fetching=true;return b;
        }
        for(var b:settlement.buildings)if(b.complete&&b.repairing&&b.workRetryAt<=now&&b.assignedBuilder.isEmpty()) {
            b.assignedBuilder=resident.id.toString();resident.taskBuilding=b.id;resident.supplyReady=false;resident.fetching=true;return b;
        }
        resident.taskBuilding="";return null;
    }
    public static void unload(Settlement s,Resident r) {
        var job=s.buildings.stream().filter(b->b.id.equals(r.cargoJob)).findFirst().orElse(null);
        for(var slot:r.builderInventory.slots)if(slot.count>0&&!slot.origin.equals("harvest")&&!slot.origin.equals("courier")) {
            if(job!=null&&!job.complete&&slot.origin.equals("base"))job.reserved.merge(slot.item,slot.count,Integer::sum);
            else if(job!=null&&!job.complete&&slot.origin.equals("prepaid_fill"))job.prepaidFill+=slot.count;
            else s.resources.merge(slot.item,slot.count,Integer::sum);
            slot.count=0;slot.item="";slot.origin="";
        }
        r.cargoJob="";r.supplyReady=false;
    }
    public static void release(Settlement s,Resident r) {
        unload(s,r);
        for(var b:s.buildings)if(b.assignedBuilder.equals(r.id.toString())) {b.assignedBuilder="";if(!b.complete)b.status="waiting_builder";else if(b.repairing)b.status="needs_repair";}
        r.taskBuilding="";r.fetching=false;
    }
    public static void retire(Settlement s,Resident r) {
        release(s,r);
        // Departing residents leave real production cargo; it never bypasses warehouse capacity.
        for(var slot:r.builderInventory.slots)if(slot.count>0) {
            s.departedCargo.merge(slot.item,slot.count,Integer::sum);slot.count=0;slot.item="";slot.origin="";
        }
    }
    public static boolean reconcile(Settlement s) {
        boolean changed=false;
        for(var b:s.buildings)if(!b.assignedBuilder.isEmpty()&&((b.complete&&!b.repairing)||s.residents.stream().noneMatch(r->r.id.toString().equals(b.assignedBuilder)&&r.profession.equals("builder")))) {b.assignedBuilder="";changed=true;}
        return changed;
    }
    private BuilderAssignments() {}
}
