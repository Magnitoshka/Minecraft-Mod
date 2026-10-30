package space.magnitoshka.settlements.domain;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

/** Progress is a stable cursor in the deterministic template, persisted across reloads. */
public final class Building {
    public String id=UUID.randomUUID().toString();
    public String type="house",status="waiting_builder";
    public int x,y,z,progress,total;
    public List<SiteEdit> preparation=new ArrayList<>();
    public List<SiteColumn> columns=new ArrayList<>();
    public List<SitePoint> access=new ArrayList<>();
    public int columnCursor,foundationRemaining;
    public boolean foundationPrepaid=true;
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
