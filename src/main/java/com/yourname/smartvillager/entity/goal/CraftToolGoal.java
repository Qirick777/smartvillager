package com.yourname.smartvillager.entity.goal;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.data.ToolTier;
import com.yourname.smartvillager.entity.SmartVillager;
import com.yourname.smartvillager.task.TaskType;
import com.yourname.smartvillager.task.VillagerTask;
import com.yourname.smartvillager.village.Village;
import com.yourname.smartvillager.village.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * MANUFACTURER work goal (design document section 10): crafts tools from village materials using
 * vanilla-style recipe costs — 2 sticks + 3 head material. Sticks come from wood (1 log = 16
 * sticks, so 2 sticks = {@value #WOOD_MILLI_PER_TOOL} milli-wood). Default output is a stone tool;
 * if surplus metal (INGOT) is on hand it upgrades to an iron tool. If materials are short it queues
 * a precedent GATHER task instead.
 *
 * <p>Has no movement flag, so the manufacturer keeps crafting while its other goals move it.</p>
 */
public class CraftToolGoal extends Goal {

    private static final int CRAFT_INTERVAL_TICKS = 100; // ~5s between craft attempts
    private static final int MATERIAL_PER_TOOL = 3;      // vanilla tool head = 3 units
    private static final int WOOD_MILLI_PER_TOOL = 125;  // 2 sticks

    private final SmartVillager villager;
    private int cooldown;

    public CraftToolGoal(SmartVillager villager) {
        this.villager = villager;
    }

    @Override
    public boolean canUse() {
        return villager.getJob() == Job.MANUFACTURER && villager.getVillageCorePos() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void tick() {
        if (cooldown-- > 0) {
            return;
        }
        cooldown = CRAFT_INTERVAL_TICKS;
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos core = villager.getVillageCorePos();
        Village village = VillageManager.get(level).getVillageAtCore(core);
        if (village != null) {
            craftOrRequest(level, village);
        }
    }

    private void craftOrRequest(ServerLevel level, Village village) {
        boolean hasWood = village.getStorage(ResourceType.WOOD) >= WOOD_MILLI_PER_TOOL;

        // Prefer the highest tier whose material is in surplus (design 10: upgrade when possible).
        ToolTier tier = null;
        if (village.getStorage(ResourceType.INGOT) >= MATERIAL_PER_TOOL) {
            tier = ToolTier.IRON;
        } else if (village.getStorage(ResourceType.STONE) >= MATERIAL_PER_TOOL) {
            tier = ToolTier.STONE;
        }

        if (tier != null && hasWood) {
            village.addStorage(ResourceType.WOOD, -WOOD_MILLI_PER_TOOL);
            village.addStorage(tier.getMaterial(), -MATERIAL_PER_TOOL);
            village.addTool(tier, 1);
            villager.swing(InteractionHand.MAIN_HAND);
            VillageManager.get(level).setDirty();
            SmartVillagerMod.LOGGER.info("Manufacturer crafted {} tool (village {}): tools={}",
                    tier, village.getCorePos().toShortString(), village.getToolStock());
            return;
        }

        // Materials short -> queue a precedent GATHER task (design 10).
        if (!hasWood) {
            queueDemand(village, TaskType.GATHER_WOOD);
        } else {
            queueDemand(village, TaskType.GATHER_STONE);
        }
    }

    private void queueDemand(Village village, TaskType type) {
        for (VillagerTask task : village.getDemandQueue()) {
            if (task.getType() == type) {
                return; // already queued
            }
        }
        village.getDemandQueue().add(new VillagerTask(type, 1.0D));
        SmartVillagerMod.LOGGER.info("Manufacturer needs {}: queued precedent gather (village {})",
                type, village.getCorePos().toShortString());
    }
}
