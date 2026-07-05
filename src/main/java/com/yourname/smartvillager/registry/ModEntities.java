package com.yourname.smartvillager.registry;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.entity.SmartVillager;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Deferred registry for all Smart Villager entity types (the Smart Villager mob).
 *
 * <p>Entries are added here as {@link net.minecraftforge.registries.RegistryObject} fields; this
 * class is currently just the registry scaffold. The Smart Villager entity will extend
 * {@code PathfinderMob}/{@code AgeableMob} directly (not vanilla {@code Villager}) and use the
 * classic goal system. See design document sections 2 and 6.</p>
 */
public final class ModEntities {

    /** Deferred register bound to the vanilla entity-type registry under this mod's namespace. */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, SmartVillagerMod.MOD_ID);

    private ModEntities() {
    }

    /** The Smart Villager mob (design document sections 2 and 6). */
    public static final RegistryObject<EntityType<SmartVillager>> SMART_VILLAGER =
            ENTITY_TYPES.register("smart_villager",
                    () -> EntityType.Builder.of(SmartVillager::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(10)
                            .build("smart_villager"));

    /**
     * Attaches this registry to the given mod event bus.
     *
     * @param modEventBus the mod-specific event bus obtained in the {@code @Mod} constructor
     */
    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
    }
}
