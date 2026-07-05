package com.yourname.smartvillager.registry;

import com.yourname.smartvillager.SmartVillagerMod;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Deferred registry for all Smart Villager blocks (e.g. the upcoming Village Core Block).
 *
 * <p>Entries are added here as {@link net.minecraftforge.registries.RegistryObject} fields; this
 * class is currently just the registry scaffold. See design document sections 2 and 5.</p>
 */
public final class ModBlocks {

    /** Deferred register bound to the vanilla block registry under this mod's namespace. */
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SmartVillagerMod.MOD_ID);

    private ModBlocks() {
    }

    // Example (to be filled in for Phase 2):
    // public static final RegistryObject<Block> VILLAGE_CORE = BLOCKS.register(
    //         "village_core",
    //         () -> new VillageCoreBlock(BlockBehaviour.Properties.of().strength(3.5F)));

    /**
     * Attaches this registry to the given mod event bus.
     *
     * @param modEventBus the mod-specific event bus obtained in the {@code @Mod} constructor
     */
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
