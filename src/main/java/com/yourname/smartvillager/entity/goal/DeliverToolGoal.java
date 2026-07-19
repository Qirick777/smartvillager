package com.yourname.smartvillager.entity.goal;

import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.entity.SmartVillager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.List;

/**
 * The manufacturer carries a finished pickaxe to the miner and hands it over by tossing it — the
 * miner then equips it. (Only pickaxes / the miner for now; axes/swords for other jobs later.)
 */
public class DeliverToolGoal extends Goal {

    private static final int MAX_RUN_TICKS = 200;

    private final SmartVillager villager;
    private final double speedModifier;
    private int runTicks;
    private SmartVillager target;

    public DeliverToolGoal(SmartVillager villager, double speedModifier) {
        this.villager = villager;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (villager.getJob() != Job.MANUFACTURER || pickaxeSlot() < 0) {
            return false;
        }
        target = findMiner();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && pickaxeSlot() >= 0 && runTicks < MAX_RUN_TICKS;
    }

    @Override
    public void start() {
        runTicks = 0;
        moveToTarget();
    }

    @Override
    public void stop() {
        target = null;
    }

    @Override
    public void tick() {
        runTicks++;
        if (target == null) {
            return;
        }
        if (villager.distanceToSqr(target) <= 9.0D) {
            int slot = pickaxeSlot();
            if (slot >= 0) {
                ItemStack pickaxe = villager.getInventory().getItem(slot);
                villager.getInventory().setItem(slot, ItemStack.EMPTY);
                villager.tossItemToward(pickaxe, target.getX(), target.getEyeY(), target.getZ(),
                        target.getUUID());
            }
        } else if (villager.getNavigation().isDone()) {
            moveToTarget();
        }
    }

    private void moveToTarget() {
        if (target != null) {
            villager.getNavigation().moveTo(target, speedModifier);
        }
    }

    private int pickaxeSlot() {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(Items.WOODEN_PICKAXE) || stack.is(Items.STONE_PICKAXE)
                    || stack.is(Items.IRON_PICKAXE)) {
                return i;
            }
        }
        return -1;
    }

    private SmartVillager findMiner() {
        if (!(villager.level() instanceof ServerLevel level)) {
            return null;
        }
        List<SmartVillager> candidates = level.getEntitiesOfClass(SmartVillager.class,
                villager.getBoundingBox().inflate(48.0D),
                other -> other != villager && other.isAlive() && other.getJob() == Job.MINER);
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
