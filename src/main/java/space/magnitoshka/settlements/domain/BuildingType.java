package space.magnitoshka.settlements.domain;

public enum BuildingType {
    HOUSE("house",16,12,2,2,1), WAREHOUSE("warehouse",20,16,3,2,1),
    LUMBER_HUT("lumber_hut",12,8,2,2,1), MINE("mine",12,20,2,2,1), FIELD("field",4,4,2,2,1),
    SMELTERY("smeltery",24,32,3,3,2), SMITHY("smithy",32,32,3,3,2), BARN("barn",32,20,3,3,2),
    MARKET("market",24,16,3,3,2), TRADING_POST("trading_post",32,24,3,3,2),
    BARRACKS("barracks",40,40,4,3,2), ARCHERY_RANGE("archery_range",28,20,4,3,2),
    MAGE_TOWER("mage_tower",40,64,3,3,3), CHURCH("church",48,64,4,3,3),
    TOWN_HALL("town_hall",64,64,4,3,1);
    public final String id;
    public final int woodCost,stoneCost,halfWidth,halfDepth,requiredSettlementLevel;
    BuildingType(String id,int wood,int stone,int width,int depth,int unlock) {
        this.id=id;woodCost=wood;stoneCost=stone;halfWidth=width;halfDepth=depth;requiredSettlementLevel=unlock;
    }
    public static BuildingType byId(String id) {
        for(var type:values()) if(type.id.equals(id))return type;
        return null;
    }
}
