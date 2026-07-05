package com.yourname.smartvillager.client;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.entity.SmartVillagerSpawnEggItem;
import com.yourname.smartvillager.registry.ModEntities;
import com.yourname.smartvillager.registry.ModItems;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client-only mod-event-bus subscribers: entity renderer, model layer, and spawn-egg item color.
 */
@Mod.EventBusSubscriber(modid = SmartVillagerMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SMART_VILLAGER.get(), SmartVillagerRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SmartVillagerRenderer.LAYER,
                SmartVillagerRenderer::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                (stack, tintIndex) -> ((SmartVillagerSpawnEggItem) stack.getItem()).getColor(tintIndex),
                ModItems.SMART_VILLAGER_SPAWN_EGG.get());
    }
}
