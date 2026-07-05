package com.yourname.smartvillager;

import com.mojang.logging.LogUtils;
import com.yourname.smartvillager.registry.ModBlocks;
import com.yourname.smartvillager.registry.ModCreativeTabs;
import com.yourname.smartvillager.registry.ModEntities;
import com.yourname.smartvillager.registry.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.slf4j.Logger;

/**
 * Main entry point for the Smart Villager mod.
 *
 * <p>Adds a brand-new AI-driven villager mob ("Smart Villager") that lives in player-built
 * villages, holds one of seven jobs, and participates in a resource economy. This class only
 * wires up the deferred registries to the mod event bus; content is defined in the
 * {@code registry} package.</p>
 */
@Mod(SmartVillagerMod.MOD_ID)
public class SmartVillagerMod {

    /** The mod id. Must match {@code mod_id} in gradle.properties and mods.toml. */
    public static final String MOD_ID = "smartvillager";

    /** Shared SLF4J logger for the whole mod. */
    public static final Logger LOGGER = LogUtils.getLogger();

    public SmartVillagerMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register the deferred registries so their contents are added during registration events.
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModEntities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);

        // Common setup (runs on both client and server).
        modEventBus.addListener(this::commonSetup);

        LOGGER.info("Smart Villager mod constructed (environment: {})", FMLEnvironment.dist);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Smart Villager common setup complete");
    }

    /** @return {@code true} when running on the physical client. */
    public static boolean isClient() {
        return FMLEnvironment.dist == Dist.CLIENT;
    }
}
