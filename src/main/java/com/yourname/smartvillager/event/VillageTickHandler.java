package com.yourname.smartvillager.event;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.village.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge-event-bus subscriber that ticks each server level's {@link VillageManager} so grace
 * periods advance, inactive villages damage their members, and expired villages are deleted
 * (design document section 5, "코어 파괴 시 처리").
 */
@Mod.EventBusSubscriber(modid = SmartVillagerMod.MOD_ID)
public final class VillageTickHandler {

    private VillageTickHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.level instanceof ServerLevel serverLevel) {
            VillageManager.get(serverLevel).tick(serverLevel);
        }
    }
}
