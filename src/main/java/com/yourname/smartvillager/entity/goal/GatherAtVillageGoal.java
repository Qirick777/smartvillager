package com.yourname.smartvillager.entity.goal;

import com.yourname.smartvillager.entity.SmartVillager;
import com.yourname.smartvillager.time.DayPhase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * During the evening phase (design document section 4), moves the villager to gather near its
 * village core. Runs only while it is evening and the villager belongs to a village and is not yet
 * within {@link #GATHER_RADIUS} of the core.
 */
public class GatherAtVillageGoal extends Goal {

    private static final double GATHER_RADIUS = 6.0D;

    private final SmartVillager villager;
    private final double speedModifier;

    public GatherAtVillageGoal(SmartVillager villager, double speedModifier) {
        this.villager = villager;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        BlockPos core = villager.getVillageCorePos();
        if (core == null || !isEvening()) {
            return false;
        }
        return distanceToCoreSq(core) > GATHER_RADIUS * GATHER_RADIUS;
    }

    @Override
    public boolean canContinueToUse() {
        BlockPos core = villager.getVillageCorePos();
        return core != null && isEvening() && distanceToCoreSq(core) > GATHER_RADIUS * GATHER_RADIUS;
    }

    @Override
    public void start() {
        moveToCore();
    }

    @Override
    public void tick() {
        if (villager.getNavigation().isDone()) {
            moveToCore();
        }
    }

    private void moveToCore() {
        BlockPos core = villager.getVillageCorePos();
        if (core != null) {
            villager.getNavigation().moveTo(
                    core.getX() + 0.5D, core.getY(), core.getZ() + 0.5D, speedModifier);
        }
    }

    private double distanceToCoreSq(BlockPos core) {
        return villager.distanceToSqr(core.getX() + 0.5D, core.getY(), core.getZ() + 0.5D);
    }

    private boolean isEvening() {
        return DayPhase.fromDayTime(villager.level().getDayTime()) == DayPhase.EVENING;
    }
}
