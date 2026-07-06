package com.yourname.smartvillager.registry;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.entity.SmartVillagerSpawnEggItem;
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

    /** Spawn egg for the Smart Villager; only usable near an active village core. */
    public static final RegistryObject<Item> SMART_VILLAGER_SPAWN_EGG =
            ITEMS.register("smart_villager_spawn_egg",
                    () -> new SmartVillagerSpawnEggItem(ModEntities.SMART_VILLAGER,
                            0x5D7C43, 0x3B4A2C, new Item.Properties()));

    /** Debug tool: right-click a Smart Villager to print its 10-slot inventory. */
    public static final RegistryObject<Item> VILLAGER_INSPECTOR =
            ITEMS.register("villager_inspector",
                    () -> new Item(new Item.Properties().stacksTo(1)));

    /**
     * Attaches this registry to the given mod event bus.
     *
     * @param modEventBus the mod-specific event bus obtained in the {@code @Mod} constructor
     */
    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}
