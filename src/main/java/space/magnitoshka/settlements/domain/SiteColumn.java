package space.magnitoshka.settlements.domain;

import java.util.ArrayList;
import java.util.List;

/** Depth does not expand save size: repeated original blocks are stored as runs. */
public final class SiteColumn {
    public int x,z,y,workIndex,runIndex,clearY,fillY,fillRun;
    public List<SiteRun> clear=new ArrayList<>(),fill=new ArrayList<>();
    public SiteColumn() {}
    public SiteColumn(int x,int y,int z,int workIndex) {this.x=x;this.y=y;this.z=z;this.workIndex=workIndex;}
}
