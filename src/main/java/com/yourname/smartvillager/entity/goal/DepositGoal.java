package com.yourname.smartvillager.entity.goal;

import com.yourname.smartvillager.entity.SmartVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.EnumSet;

/**
 * When the villager's inventory is getting full, walk to its personal chest and move the items in.
 * This is the "return home and store" half of the storage routine; tools would be kept (none are
 * carried yet). Part of the physical storage rework (design: deposit excess into your own chest).
 */
public class DepositGoal extends Goal {

    /** Total item count at which the villager goes to deposit into its chest. */
    private static final int DEPOSIT_THRESHOLD = 32;
    /** Give up pathing to the chest after this many ticks. */
    private static final int MAX_RUN_TICKS = 200;

    private final SmartVillager villager;
    private final double speedModifier;
    private int runTicks;

    public DepositGoal(SmartVillager villager, double speedModifier) {
        this.villager = villager;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return villager.getChestPos() != null
                && villager.totalInventoryCount() >= DEPOSIT_THRESHOLD;
    }

    @Override
    public boolean canContinueToUse() {
        return villager.getChestPos() != null
                && villager.totalInventoryCount() > 0
                && runTicks < MAX_RUN_TICKS;
    }

    @Override
    public void start() {
        runTicks = 0;
        moveToChest();
    }

    @Override
    public void tick() {
        runTicks++;
        BlockPos chest = villager.getChestPos();
        if (chest == null) {
            return;
        }
        double distSq = villager.distanceToSqr(
                chest.getX() + 0.5D, chest.getY() + 0.5D, chest.getZ() + 0.5D);
        if (distSq <= 6.0D) {
            deposit(chest);
        } else if (villager.getNavigation().isDone()) {
            moveToChest();
        }
    }

    private void moveToChest() {
        BlockPos chest = villager.getChestPos();
        if (chest != null) {
            villager.getNavigation().moveTo(
                    chest.getX() + 0.5D, chest.getY(), chest.getZ() + 0.5D, speedModifier);
        }
    }

    private void deposit(BlockPos chestPos) {
        if (!(villager.level() instanceof ServerLevel level)) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(chestPos);
        if (!(blockEntity instanceof Container dest)) {
            return;
        }
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty()) {
                inv.setItem(i, insert(dest, stack));
            }
        }
        dest.setChanged();
    }

    /** Moves as much of {@code stack} as fits into {@code dest}; returns the leftover. */
    private static ItemStack insert(Container dest, ItemStack stack) {
        for (int i = 0; i < dest.getContainerSize() && !stack.isEmpty(); i++) {
            ItemStack slot = dest.getItem(i);
            if (slot.isEmpty()) {
                dest.setItem(i, stack.copy());
                stack.setCount(0);
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameTags(slot, stack)) {
                int cap = Math.min(dest.getMaxStackSize(), slot.getMaxStackSize());
                int move = Math.min(stack.getCount(), cap - slot.getCount());
                if (move > 0) {
                    slot.grow(move);
                    stack.shrink(move);
                }
            }
        }
        return stack;
    }
}
