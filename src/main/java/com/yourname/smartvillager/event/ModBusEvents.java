package com.yourname.smartvillager.event;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.entity.SmartVillager;
import com.yourname.smartvillager.registry.ModEntities;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;

/**
 * Mod-event-bus subscriber for one-time setup that runs during mod loading (both physical sides),
 * such as registering entity attributes.
 */
@Mod.EventBusSubscriber(modid = SmartVillagerMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModBusEvents {

    private ModBusEvents() {
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SMART_VILLAGER.get(), SmartVillager.createAttributes().build());
    }
}
