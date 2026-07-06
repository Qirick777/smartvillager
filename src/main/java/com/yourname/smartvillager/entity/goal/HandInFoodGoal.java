package com.yourname.smartvillager.entity.goal;

import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.entity.SmartVillager;
import com.yourname.smartvillager.time.DayPhase;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;

import java.util.EnumSet;
import java.util.List;

/**
 * In the evening, food producers (farmers and hunters) carry all their food to the village manager
 * and hand it over by tossing it — the manager then redistributes it the next morning (design: food
 * is exchanged in the evening, distributed in the morning).
 */
public class HandInFoodGoal extends Goal {

    private static final int MAX_RUN_TICKS = 400;

    private final SmartVillager villager;
    private final double speedModifier;
    private int runTicks;
    private SmartVillager manager;

    public HandInFoodGoal(SmartVillager villager, double speedModifier) {
        this.villager = villager;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (villager.getJob() != Job.FARMER && villager.getJob() != Job.HUNTER) {
            return false;
        }
        if (!isEvening() || villager.foodCount() <= 0) {
            return false;
        }
        manager = findManager();
        return manager != null;
    }

    @Override
    public boolean canContinueToUse() {
        return isEvening() && manager != null && manager.isAlive()
                && villager.foodCount() > 0 && runTicks < MAX_RUN_TICKS;
    }

    @Override
    public void start() {
        runTicks = 0;
        moveToManager();
    }

    @Override
    public void stop() {
        manager = null;
    }

    @Override
    public void tick() {
        runTicks++;
        if (manager == null) {
            return;
        }
        if (villager.distanceToSqr(manager) <= 9.0D) {
            for (ItemStack food : villager.extractFood(64)) {
                villager.tossItemToward(food, manager.getX(), manager.getEyeY(), manager.getZ());
            }
        } else if (villager.getNavigation().isDone()) {
            moveToManager();
        }
    }

    private void moveToManager() {
        if (manager != null) {
            villager.getNavigation().moveTo(manager, speedModifier);
        }
    }

    private boolean isEvening() {
        return DayPhase.fromDayTime(villager.level().getDayTime()) == DayPhase.EVENING;
    }

    private SmartVillager findManager() {
        if (!(villager.level() instanceof ServerLevel level)) {
            return null;
        }
        List<SmartVillager> candidates = level.getEntitiesOfClass(SmartVillager.class,
                villager.getBoundingBox().inflate(48.0D),
                other -> other != villager && other.isAlive() && other.getJob() == Job.MANAGER);
        SmartVillager nearest = null;
        double bestSq = Double.MAX_VALUE;
        for (SmartVillager candidate : candidates) {
            double distSq = villager.distanceToSqr(candidate);
            if (distSq < bestSq) {
                bestSq = distSq;
                nearest = candidate;
            }
        }
        return nearest;
    }
}
