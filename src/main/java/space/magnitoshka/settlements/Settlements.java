package space.magnitoshka.settlements;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@Mod(Settlements.MODID)
public final class Settlements {
    public static final String MODID = "smartsettlements";
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);
    public static final DeferredRegister<net.minecraft.server.level.TicketType> TICKETS = DeferredRegister.create(Registries.TICKET_TYPE, MODID);
    public static final RegistryObject<net.minecraft.server.level.TicketType> LOADING = TICKETS.register("settlement", () ->
        new net.minecraft.server.level.TicketType(100L, net.minecraft.server.level.TicketType.FLAG_LOADING |
            net.minecraft.server.level.TicketType.FLAG_SIMULATION | net.minecraft.server.level.TicketType.FLAG_KEEP_DIMENSION_ACTIVE));
    public static final RegistryObject<Item> BANNER = ITEMS.register("founder_banner",
        () -> new FounderBanner(new Item.Properties().setId(ITEMS.key("founder_banner")).stacksTo(1)));
    public static final RegistryObject<EntityType<CitizenEntity>> CITIZEN = ENTITIES.register("citizen",
        () -> EntityType.Builder.of(CitizenEntity::new, MobCategory.CREATURE).sized(.6f, 1.8f)
            .clientTrackingRange(10).build(ENTITIES.key("citizen")));

    public Settlements(FMLJavaModLoadingContext context) {
        var group = context.getModBusGroup();
        ITEMS.register(group);
        ENTITIES.register(group);
        TICKETS.register(group);
        context.registerConfig(ModConfig.Type.SERVER, SettlementConfig.SPEC);
        EntityAttributeCreationEvent.BUS.addListener(e -> e.put(CITIZEN.get(), CitizenEntity.attributes().build()));
        BuildCreativeModeTabContentsEvent.BUS.addListener(e -> {
            if (e.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) e.accept(BANNER.get());
        });
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(ServerSettlements::login);
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e -> SettlementNetwork.close(e.getEntity().getUUID()));
        TickEvent.ServerTickEvent.Post.BUS.addListener(ServerSettlements::tick);
        PlayerInteractEvent.RightClickBlock.BUS.addListener(ServerSettlements::interact);
        if(net.minecraftforge.gametest.ForgeGameTestHooks.isGametestEnabled()) {
            var tests=DeferredRegister.<java.util.function.Consumer<net.minecraft.gametest.framework.GameTestHelper>>create(Registries.TEST_FUNCTION,MODID);
            var suite=new SettlementGameTests();
            tests.register("citizen",()->suite::citizenRegistryAndAuthoritySurviveTicking);
            tests.register("economy",()->suite::savedEconomyUsesRegisteredNutritionAndRoundTrips);
            tests.register("hall",()->suite::hallContainsThreeCompleteBedsAndWarehouse);
            tests.register(group);
        }
        SettlementNetwork.init();
    }
}
