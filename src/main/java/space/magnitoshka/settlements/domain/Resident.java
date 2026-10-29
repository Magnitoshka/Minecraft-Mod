package space.magnitoshka.settlements.domain;

import java.util.UUID;

/** Persisted identity; the Minecraft entity is only its materialized view. */
public final class Resident {
    public UUID id = UUID.randomUUID();
    public String name = "Alden";
    public String profession = "unemployed";
    public int level = 1;
    public int experience;
    public int mood = 100;
    public int hungerDays;
    public boolean spawned;
    public String bed = "";

    public Resident() {}

    public Resident(String name) {
        this.name = name;
    }

    public double workEfficiency() {
        if (hungerDays >= 2) return 0;
        return (hungerDays == 1 ? .5 : 1) * (1 + .15 * (level - 1));
    }
}
