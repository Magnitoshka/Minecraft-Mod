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
    public static final ForgeConfigSpec SPEC = B.build();
    private static ForgeConfigSpec.IntValue integer(String key, int value, int min, int max) {
        return B.defineInRange(key, value, min, max);
    }
    public static Rules rules() { return rules(BEDS.get()); }
    public static Rules rules(int availableBeds) {
        int beds=Math.clamp(availableBeds,0,BEDS.get());
        return new Rules(RADIUS.get(), GAP.get(), CAPACITY.get(), HIRE.get(), HIRE_EXTRA.get(),
            WOOD.get(), STONE.get(), FOOD.get(), DAILY_FOOD.get(), IMMIGRATION.get(), FOOD_DAYS.get(),
            MOOD.get(), beds, Math.min(RESIDENTS.get(),beds));
    }
    private SettlementConfig() {}
}
