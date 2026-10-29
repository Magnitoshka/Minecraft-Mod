package space.magnitoshka.settlements.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import space.magnitoshka.settlements.*;

@Mod.EventBusSubscriber(modid=Settlements.MODID,value=Dist.CLIENT)
public final class ClientEvents {
    private static final KeyMapping MAP = new KeyMapping("key.smartsettlements.map",com.mojang.blaze3d.platform.InputConstants.KEY_M,KeyMapping.Category.GAMEPLAY);
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(MAP); }
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(Settlements.CITIZEN.get(),CitizenRenderer::new);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        SettlementNetwork.clientReceiver=snapshot -> {
            var mc=Minecraft.getInstance();
            if (mc.gui.screen() instanceof SettlementScreen screen) screen.update(snapshot);
            else if (snapshot.open()) mc.gui.setScreen(new SettlementScreen(snapshot));
        };
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> {
            while (MAP.consumeClick()) {
                var mc=Minecraft.getInstance();
                if (mc.player!=null && mc.gui.screen()==null) SettlementNetwork.request("open","","","",0);
            }
        });
    }
    private static final class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity,HumanoidRenderState,HumanoidModel<HumanoidRenderState>> {
        CitizenRenderer(EntityRendererProvider.Context ctx) { super(ctx,new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)),.5f); }
        @Override public HumanoidRenderState createRenderState() { return new HumanoidRenderState(); }
        @Override public Identifier getTextureLocation(HumanoidRenderState state) {
            return Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
        }
    }
}
