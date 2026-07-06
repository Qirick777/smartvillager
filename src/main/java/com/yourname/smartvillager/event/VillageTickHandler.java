package com.yourname.smartvillager.event;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.time.DayPhase;
import com.yourname.smartvillager.village.VillageManager;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

/**
 * Forge-event-bus subscriber that, each server-level tick:
 * <ul>
 *   <li>ticks the level's {@link VillageManager} (grace periods, member damage, deletion —
 *       design document section 5), and</li>
 *   <li>logs day/night {@link DayPhase} transitions (design document section 4).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = SmartVillagerMod.MOD_ID)
public final class VillageTickHandler {

    /** Last observed day phase per dimension, so transitions are logged only once. */
    private static final Map<ResourceKey<Level>, DayPhase> LAST_PHASE = new HashMap<>();

    private VillageTickHandler() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.level instanceof ServerLevel serverLevel)) {
            return;
        }

        VillageManager.get(serverLevel).tick(serverLevel);
        handleDayPhase(serverLevel);
    }

    private static void handleDayPhase(ServerLevel level) {
        DayPhase current = DayPhase.fromDayTime(level.getDayTime());
        DayPhase previous = LAST_PHASE.put(level.dimension(), current);
        if (previous == current) {
            return;
        }
        SmartVillagerMod.LOGGER.info("[{}] day phase -> {} (dayTime={})",
                level.dimension().location(), current, level.getDayTime() % DayPhase.TICKS_PER_DAY);

        // Evening gathering: the manager recalculates each village's demand (design section 8).
        if (current == DayPhase.EVENING) {
            VillageManager.get(level).recalculateDemandAll(level);
        }
    }
}
