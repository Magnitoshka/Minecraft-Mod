package space.magnitoshka.settlements.domain;

public enum BuildingType {
    HOUSE("house",16,12,2,2), WAREHOUSE("warehouse",20,16,3,2),
    LUMBER_HUT("lumber_hut",12,8,2,2), MINE("mine",12,20,2,2), FIELD("field",4,4,2,2);
    public final String id;
    public final int woodCost,stoneCost,halfWidth,halfDepth;
    BuildingType(String id,int wood,int stone,int width,int depth) {
        this.id=id;woodCost=wood;stoneCost=stone;halfWidth=width;halfDepth=depth;
    }
    public static BuildingType byId(String id) {
        for(var type:values()) if(type.id.equals(id))return type;
        return null;
    }
}
