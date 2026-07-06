package com.yourname.smartvillager.entity.goal;

import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.entity.SmartVillager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;
import java.util.List;

/**
 * When a worker is idle (its gather quota is met) and carries crafting materials, it walks to the
 * village manufacturer and hands them over by tossing the stacks — like a player dropping items —
 * which the manufacturer then picks up into its own inventory (design: share by throwing, receive
 * into inventory).
 */
public class DeliverGoal extends Goal {

    /** Items the manufacturer consumes and therefore worth delivering. */
    private static final Item[] MATERIALS = {Items.OAK_LOG, Items.COBBLESTONE, Items.IRON_INGOT};
    private static final int MAX_RUN_TICKS = 200;

    private final SmartVillager villager;
    private final double speedModifier;
    private int runTicks;
    private SmartVillager target;

    public DeliverGoal(SmartVillager villager, double speedModifier) {
        this.villager = villager;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (villager.getJob() == null || villager.getJob() == Job.MANUFACTURER
                || villager.hasActiveGatherTask() || materialSlot() < 0) {
            return false;
        }
        target = findManufacturer();
        return target != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && target.isAlive() && materialSlot() >= 0 && runTicks < MAX_RUN_TICKS;
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
            int slot = materialSlot();
            if (slot >= 0) {
                ItemStack stack = villager.getInventory().getItem(slot);
                villager.getInventory().setItem(slot, ItemStack.EMPTY);
                villager.tossItemToward(stack, target.getX(), target.getEyeY(), target.getZ(),
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

    /** @return the first inventory slot holding a deliverable material, or -1. */
    private int materialSlot() {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            for (Item material : MATERIALS) {
                if (stack.is(material)) {
                    return i;
                }
            }
        }
        return -1;
    }

    private SmartVillager findManufacturer() {
        if (!(villager.level() instanceof ServerLevel level)) {
            return null;
        }
        List<SmartVillager> candidates = level.getEntitiesOfClass(SmartVillager.class,
                villager.getBoundingBox().inflate(48.0D),
                other -> other != villager && other.isAlive() && other.getJob() == Job.MANUFACTURER);
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
