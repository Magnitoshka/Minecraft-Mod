package space.magnitoshka.settlements.domain;

/** Saved world-space edit. Digging checks the original block before changing it. */
public final class SiteEdit {
    public int x,y,z;
    public boolean fill;
    public String expected="";
    public SiteEdit() {}
    public SiteEdit(int x,int y,int z,boolean fill,String expected) {
        this.x=x;this.y=y;this.z=z;this.fill=fill;this.expected=expected;
    }
}
