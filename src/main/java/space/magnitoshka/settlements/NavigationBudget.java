package space.magnitoshka.settlements;

import net.minecraft.server.MinecraftServer;

/** All citizens share the server's bounded path calculation budget. */
public final class NavigationBudget {
    private static MinecraftServer activeServer;
    private static int tick=-1,used;
    public static boolean take(MinecraftServer server) {
        int now=server.getTickCount();
        if(server!=activeServer||tick!=now) {activeServer=server;tick=now;used=0;}
        return used++<SettlementConfig.PATH_BUDGET.get();
    }
    private NavigationBudget() {}
}
