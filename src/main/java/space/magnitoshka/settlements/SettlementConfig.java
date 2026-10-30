package space.magnitoshka.settlements;

import net.minecraftforge.common.ForgeConfigSpec;
import space.magnitoshka.settlements.domain.Rules;

public final class SettlementConfig {
    private static final ForgeConfigSpec.Builder B = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec.IntValue RADIUS = integer("campRadius", 48, 8, 256);
    public static final ForgeConfigSpec.IntValue GAP = integer("settlementGap", 64, 0, 1024);
    public static final ForgeConfigSpec.IntValue CAPACITY = integer("warehouseCapacity", 1000, 1, 1000000);
    public static final ForgeConfigSpec.IntValue HIRE = integer("hireBase", 30, 0, 1000000);
    public static final ForgeConfigSpec.IntValue HIRE_EXTRA = integer("hireExtraAboveTen", 5, 0, 100000);
    public static final ForgeConfigSpec.IntValue WOOD = integer("starterWood", 64, 0, 10000);
    public static final ForgeConfigSpec.IntValue STONE = integer("starterStone", 64, 0, 10000);
    public static final ForgeConfigSpec.IntValue FOOD = integer("starterFood", 48, 0, 10000);
    public static final ForgeConfigSpec.IntValue DAILY_FOOD = integer("foodPerResident", 1, 1, 100);
    public static final ForgeConfigSpec.IntValue IMMIGRATION = integer("immigrationPercent", 20, 0, 100);
    public static final ForgeConfigSpec.IntValue FOOD_DAYS = integer("immigrationFoodDays", 3, 1, 30);
    public static final ForgeConfigSpec.IntValue MOOD = integer("immigrationMoodThreshold", 60, 0, 100);
    public static final ForgeConfigSpec.IntValue BEDS = integer("hallBeds", 3, 1, 3);
    public static final ForgeConfigSpec.IntValue RESIDENTS = integer("initialResidents", 3, 0, 3);
    public static final ForgeConfigSpec.IntValue WALLET = integer("starterWallet", 50, 0, 1000000);
    public static final ForgeConfigSpec.IntValue BREAD = integer("starterBread", 16, 0, 64);
    public static final ForgeConfigSpec.IntValue BANNER = integer("secondBannerPrice", 200, 1, 10000000);
    public static final ForgeConfigSpec.DoubleValue BANNER_SCALE = B.defineInRange("bannerPriceMultiplier", 1.5, 1, 10);
    public static final ForgeConfigSpec.IntValue LOADED = integer("loadedSettlementsPerPlayer", 8, 0, 64);
    public static final ForgeConfigSpec.IntValue BUILD_RATE = integer("builderBlocksPerSecond", 4, 1, 32);
    public static final ForgeConfigSpec.IntValue MINE_SECONDS = integer("mineProductionSeconds", 15, 1, 600);
    public static final ForgeConfigSpec.IntValue LUMBER_SECONDS = integer("lumberWorkSeconds", 4, 1, 120);
    public static final ForgeConfigSpec.IntValue FARM_SECONDS = integer("farmWorkSeconds", 4, 1, 120);
    public static final ForgeConfigSpec.IntValue WAREHOUSE_BONUS = integer("additionalWarehouseCapacity", 1000, 1, 1000000);
    public static final ForgeConfigSpec.IntValue WORK_RADIUS = integer("lumberSearchRadius", 24, 4, 32);
    public static final ForgeConfigSpec.IntValue BUILDER_XP = integer("builderXpPerBlock", 1, 0, 100);
    public static final ForgeConfigSpec.IntValue WORK_XP = integer("workerXpPerDelivery", 5, 0, 1000);
    public static final ForgeConfigSpec.IntValue PATH_BUDGET = integer("pathCalculationsPerTick", 8, 1, 64);
    public static final ForgeConfigSpec.IntValue TERRAIN_BUDGET = integer("terrainColumnsPerTick",256,32,2048);
    public static final ForgeConfigSpec.IntValue VILLAGE_RADIUS=integer("villageRadius",80,8,256);
    public static final ForgeConfigSpec.IntValue CITY_RADIUS=integer("cityRadius",128,8,256);
    public static final ForgeConfigSpec.IntValue VILLAGE_RESIDENTS=integer("villageResidents",8,1,1024);
    public static final ForgeConfigSpec.IntValue CITY_RESIDENTS=integer("cityResidents",20,1,1024);
    public static final ForgeConfigSpec.IntValue WAREHOUSE_TWO=integer("warehouseTierTwoCapacity",3000,1,1000000);
    public static final ForgeConfigSpec.IntValue WAREHOUSE_THREE=integer("warehouseTierThreeCapacity",8000,1,1000000);
    public static final ForgeConfigSpec.DoubleValue BUILDING_SPEED=B.defineInRange("workSpeedPerBuildingTier",.25,0,10);
    public static final java.util.Map<String,ForgeConfigSpec.IntValue> BUILDING_PRICES=new java.util.LinkedHashMap<>();
    static {
        for(var type:space.magnitoshka.settlements.domain.BuildingType.values())for(int tier=1;tier<=3;tier++) {
            BUILDING_PRICES.put(type.id+":"+tier+":wood",integer("buildingPrices."+type.id+".tier"+tier+".logs",type.woodCost*tier,0,1000000));
            BUILDING_PRICES.put(type.id+":"+tier+":stone",integer("buildingPrices."+type.id+".tier"+tier+".cobblestone",type.stoneCost*tier,0,1000000));
        }
    }
    public static int price(space.magnitoshka.settlements.domain.BuildingType type,int tier,boolean wood) {
        return BUILDING_PRICES.get(type.id+":"+Math.clamp(tier,1,3)+(wood?":wood":":stone")).get();
    }
    public static int tierCapacity(int tier) {return tier>=3?WAREHOUSE_THREE.get():tier==2?WAREHOUSE_TWO.get():WAREHOUSE_BONUS.get();}
    public static int tierRadius(int tier) {return tier>=3?CITY_RADIUS.get():tier==2?VILLAGE_RADIUS.get():RADIUS.get();}
    public static final java.util.Map<Integer,ForgeConfigSpec.IntValue[]> MINE_WEIGHTS=new java.util.LinkedHashMap<>();
    static {
        int[][] weights={{70,20,10,0,0},{60,22,15,3,0},{50,22,20,6,2}};
        String[] resources={"stone","coal","iron","gold","diamond"};
        for(int tier=1;tier<=3;tier++) {
            var values=new ForgeConfigSpec.IntValue[5];
            for(int i=0;i<5;i++)values[i]=integer("mineWeights.tier"+tier+"."+resources[i],weights[tier-1][i],0,10000);
            MINE_WEIGHTS.put(tier,values);
        }
    }
    public static final ForgeConfigSpec.BooleanValue AUTO_REPAIR=B.define("autoRepairBuildings",true);
    public static final ForgeConfigSpec.IntValue REPAIR_SCAN=integer("buildingInspectionBlocksPerTick",128,1,4096);
    public static final ForgeConfigSpec.IntValue REPAIR_WOOD=integer("repairLogsPerUnit",1,1,64);
    public static final ForgeConfigSpec.IntValue REPAIR_STONE=integer("repairCobblestonePerUnit",1,1,64);
    public static final ForgeConfigSpec.IntValue REPATH_TICKS=integer("citizenRepathAfterTicks",60,20,1200);
    public static final ForgeConfigSpec SPEC = B.build();
    private static ForgeConfigSpec.IntValue integer(String key, int value, int min, int max) {
        return B.defineInRange(key, value, min, max);
    }
    public static Rules rules() { return rules(BEDS.get()); }
    public static Rules rules(int availableBeds) {
        int beds=Math.clamp(availableBeds,0,1024);
        return new Rules(RADIUS.get(), GAP.get(), CAPACITY.get(), HIRE.get(), HIRE_EXTRA.get(),
            WOOD.get(), STONE.get(), FOOD.get(), DAILY_FOOD.get(), IMMIGRATION.get(), FOOD_DAYS.get(),
            MOOD.get(), beds, Math.min(RESIDENTS.get(),beds));
    }
    private SettlementConfig() {}
}
