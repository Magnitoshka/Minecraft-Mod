package space.magnitoshka.settlements.domain;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/** Progress is a stable cursor in the deterministic template, persisted across reloads. */
public final class Building {
    public String id=UUID.randomUUID().toString();
    public String type="house",status="waiting_builder";
    public int x,y,z,progress,total;
    public int level=1,targetLevel=1,templateVersion,upgradeFromLevel,upgradeFromVersion;
    /** Price agreed when queued; live configuration changes never alter an active contract. */
    public int priceWood=-1,priceStone=-1;
    public int stagedWood,stagedStone;
    public int workplaces() {return complete?Math.clamp(level,1,3):0;}
    public List<SiteEdit> preparation=new ArrayList<>();
    public List<SiteColumn> columns=new ArrayList<>();
    public List<SitePoint> access=new ArrayList<>();
    public int columnCursor,foundationRemaining;
    public boolean foundationPrepaid=true;
    public String assignedBuilder="";
    public java.util.Map<String,Integer> reserved=new java.util.HashMap<>();
    public boolean logisticsInitialized,starterSaplings;
    public int materialProgress,prepaidFill;
    public int starterSaplingsRemaining=-1;
    public int prepared,preparationTotal,standX,standY,standZ;
    public boolean hasStand,preparationStarted;
    public int totalSteps() {return total+Math.max(preparationTotal,preparation.size());}
    public int completedSteps() {return progress+prepared;}
    public boolean complete;
    public boolean started;
    public Building() {}
    public Building(BuildingType type,int x,int y,int z,int total) {
        this.type=type.id;this.x=x;this.y=y;this.z=z;this.total=total;
    }
    public boolean contains(int px,int pz,int margin) {
        var t=BuildingType.byId(type);
        return t!=null && Math.abs((long)px-x)<=t.halfWidth+margin && Math.abs((long)pz-z)<=t.halfDepth+margin;
    }
}
