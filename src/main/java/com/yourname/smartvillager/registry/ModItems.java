package com.yourname.smartvillager.registry;

import com.yourname.smartvillager.SmartVillagerMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Deferred registry for all Smart Villager items (block items, the spawn egg, etc.).
 *
 * <p>Entries are added here as {@link net.minecraftforge.registries.RegistryObject} fields; this
 * class is currently just the registry scaffold. See design document sections 2 and 3.</p>
 */
public final class ModItems {

    /** Deferred register bound to the vanilla item registry under this mod's namespace. */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, SmartVillagerMod.MOD_ID);

    private ModItems() {
    }

    /** Block item for the Village Core Block. */
    public static final RegistryObject<Item> VILLAGE_CORE = ITEMS.register("village_core",
            () -> new BlockItem(ModBlocks.VILLAGE_CORE.get(), new Item.Properties()));

    // Example (to be filled in for Phase 3):
    // public static final RegistryObject<Item> SMART_VILLAGER_SPAWN_EGG = ITEMS.register(
    //         "smart_villager_spawn_egg",
    //         () -> new ForgeSpawnEggItem(ModEntities.SMART_VILLAGER, 0x8899AA, 0x556677,
    //                 new Item.Properties()));

    /**
     * Attaches this registry to the given mod event bus.
     *
     * @param modEventBus the mod-specific event bus obtained in the {@code @Mod} constructor
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
