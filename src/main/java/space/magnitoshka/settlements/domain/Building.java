package space.magnitoshka.settlements.domain;

import java.util.UUID;

/** Progress is a stable cursor in the deterministic template, persisted across reloads. */
public final class Building {
    public String id=UUID.randomUUID().toString();
    public String type="house",status="waiting_builder";
    public int x,y,z,progress,total;
    public boolean complete;
    public Building() {}
    public Building(BuildingType type,int x,int y,int z,int total) {
        this.type=type.id;this.x=x;this.y=y;this.z=z;this.total=total;
    }
    public boolean contains(int px,int pz,int margin) {
        var t=BuildingType.byId(type);
        return t!=null && Math.abs((long)px-x)<=t.halfWidth+margin && Math.abs((long)pz-z)<=t.halfDepth+margin;
    }
}
