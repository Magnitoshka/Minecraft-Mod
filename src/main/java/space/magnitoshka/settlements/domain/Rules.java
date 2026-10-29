package space.magnitoshka.settlements.domain;

/** Server configuration snapshot. World logic never reads client configuration. */
public record Rules(int radius, int gap, int capacity, int hireBase, int hireExtra,
                    int starterWood, int starterStone, int starterFood,
                    int foodPerResident, int immigrationPercent, int immigrantFoodDays,
                    int moodThreshold, int beds, int initialResidents) {
    public Rules {
        if (radius < 1 || gap < 0 || capacity < 0 || hireBase < 0 || hireExtra < 0
                || starterWood < 0 || starterStone < 0 || starterFood < 0
                || foodPerResident < 1 || immigrationPercent < 0 || immigrationPercent > 100
                || immigrantFoodDays < 1 || moodThreshold < 0 || moodThreshold > 100
                || beds < 0 || initialResidents < 0 || initialResidents > beds) {
            throw new IllegalArgumentException("Invalid settlement rules");
        }
    }

    public static Rules defaults() {
        return new Rules(48, 64, 1000, 30, 5, 64, 64, 48, 1, 20, 3, 60, 3, 3);
    }
}
