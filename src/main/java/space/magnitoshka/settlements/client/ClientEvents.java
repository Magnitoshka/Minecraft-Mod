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
        event.enqueueWork(()->net.minecraft.client.gui.screens.MenuScreens.register(Settlements.WAREHOUSE_MENU.get(),WarehouseScreen::new));
        SettlementNetwork.noticeReceiver=notice->{
            var screen=Minecraft.getInstance().gui.screen();
            if(screen instanceof SettlementScreen map)map.notice(notice);
            else if(screen instanceof WarehouseScreen warehouse)warehouse.notice(notice);
        };
        SettlementNetwork.terrainReceiver=survey->{
            if(Minecraft.getInstance().gui.screen() instanceof SettlementScreen screen)screen.terrain(survey);
        };
        SettlementNetwork.clientReceiver=snapshot -> {
            var mc=Minecraft.getInstance();
            if(mc.gui.screen() instanceof WarehouseScreen warehouse)warehouse.update(snapshot);
            else if (mc.gui.screen() instanceof SettlementScreen screen) screen.update(snapshot);
            else if (snapshot.open()) mc.gui.setScreen(new SettlementScreen(snapshot));
        };
        net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(e->CitizenSkins.clear());
        net.minecraftforge.event.entity.EntityLeaveLevelEvent.BUS.addListener(e->{
            if(e.getLevel().isClientSide()&&e.getEntity() instanceof CitizenEntity)
                Minecraft.getInstance().execute(()->CitizenSkins.release(e.getEntity().getUUID()));
        });
        TickEvent.ClientTickEvent.Post.BUS.addListener(e -> {
            while (MAP.consumeClick()) {
                var mc=Minecraft.getInstance();
                if (mc.player!=null && mc.gui.screen()==null) SettlementNetwork.request("open","","","",0);
            }
        });
    }
    private static final class CitizenState extends HumanoidRenderState {
        Identifier skin=Identifier.withDefaultNamespace("textures/entity/player/wide/steve.png");
    }
    private static final class CitizenRenderer extends HumanoidMobRenderer<CitizenEntity,CitizenState,HumanoidModel<CitizenState>> {
        CitizenRenderer(EntityRendererProvider.Context ctx) { super(ctx,new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)),.5f); }
        @Override public CitizenState createRenderState() { return new CitizenState(); }
        @Override public void extractRenderState(CitizenEntity citizen,CitizenState state,float delta) {
            super.extractRenderState(citizen,state,delta);
            state.skin=CitizenSkins.texture(citizen.getUUID());
        }
        @Override public Identifier getTextureLocation(CitizenState state) {return state.skin;}
    }
}
