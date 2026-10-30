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
    public String workplace = "";
    public String taskBuilding = "";
    public BuilderInventory builderInventory=new BuilderInventory();
    public String cargoJob="",supplyStage="";
    public boolean fetching,supplyReady;
    public String workStatus = "idle";
    public int workTicks;
    public int carriedWheat;

    public Resident() {}

    public Resident(String name) {
        this.name = name;
    }

    public double workEfficiency() {
        if (hungerDays >= 2) return 0;
        return (hungerDays == 1 ? .5 : 1) * (1 + .15 * (level - 1)) * Math.clamp((mood + hungerDays * 50) / 100.0, .25, 1.0);
    }
}
