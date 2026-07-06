package com.yourname.smartvillager.entity.goal;

import com.yourname.smartvillager.entity.SmartVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.EnumSet;

/**
 * When the villager is carrying surplus (non-material) items, it walks to its personal chest,
 * opens it like a player would — lid animation, sound, and a short delay — moves the items in, then
 * closes it. Crafting materials (logs / cobblestone / iron ingots) are intentionally kept in the
 * inventory so they can still be delivered to the manufacturer.
 */
public class DepositGoal extends Goal {

    /** Items withheld from deposit because they are delivered to the manufacturer instead. */
    private static final Item[] MATERIALS = {Items.OAK_LOG, Items.COBBLESTONE, Items.IRON_INGOT};
    private static final int DEPOSIT_THRESHOLD = 16;
    private static final int OPEN_DELAY = 10;
    private static final int CLOSE_DELAY = 10;
    private static final int MAX_RUN_TICKS = 200;

    private final SmartVillager villager;
    private final double speedModifier;
    /** 0 approach, 1 opened (waiting), 2 deposited (waiting to close), 3 done. */
    private int phase;
    private int timer;
    private int runTicks;

    public DepositGoal(SmartVillager villager, double speedModifier) {
        this.villager = villager;
        this.speedModifier = speedModifier;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return villager.getChestPos() != null && depositableCount() >= DEPOSIT_THRESHOLD;
    }

    @Override
    public boolean canContinueToUse() {
        if (villager.getChestPos() == null) {
            return false;
        }
        if (phase >= 1 && phase < 3) {
            return true; // once opened, always finish the close
        }
        return depositableCount() > 0 && runTicks < MAX_RUN_TICKS;
    }

    @Override
    public void start() {
        phase = 0;
        timer = 0;
        runTicks = 0;
        moveToChest();
    }

    @Override
    public void stop() {
        if ((phase == 1 || phase == 2) && villager.getChestPos() != null
                && villager.level() instanceof ServerLevel level) {
            villager.closeChestVisual(level, villager.getChestPos());
        }
        phase = 3;
    }

    @Override
    public void tick() {
        runTicks++;
        BlockPos chest = villager.getChestPos();
        if (chest == null || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        switch (phase) {
            case 0 -> {
                if (nearChest(chest)) {
                    villager.openChestVisual(level, chest);
                    phase = 1;
                    timer = 0;
                } else if (villager.getNavigation().isDone()) {
                    moveToChest();
                }
            }
            case 1 -> {
                if (++timer >= OPEN_DELAY) {
                    deposit(level);
                    phase = 2;
                    timer = 0;
                }
            }
            case 2 -> {
                if (++timer >= CLOSE_DELAY) {
                    villager.closeChestVisual(level, chest);
                    phase = 3;
                }
            }
            default -> {
            }
        }
    }

    private boolean nearChest(BlockPos chest) {
        return villager.distanceToSqr(chest.getX() + 0.5D, chest.getY() + 0.5D, chest.getZ() + 0.5D)
                <= 9.0D;
    }

    private void moveToChest() {
        BlockPos chest = villager.getChestPos();
        if (chest != null) {
            villager.getNavigation().moveTo(
                    chest.getX() + 0.5D, chest.getY(), chest.getZ() + 0.5D, speedModifier);
        }
    }

    private void deposit(ServerLevel level) {
        Container dest = villager.getChestContainer(level);
        if (dest == null) {
            return;
        }
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && !isMaterial(stack)) {
                inv.setItem(i, insert(dest, stack));
            }
        }
        dest.setChanged();
    }

    private int depositableCount() {
        SimpleContainer inv = villager.getInventory();
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && !isMaterial(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Materials are kept for delivery; food is kept for the evening hand-in to the manager. */
    private static boolean isMaterial(ItemStack stack) {
        if (SmartVillager.isFoodItem(stack)) {
            return true;
        }
        for (Item material : MATERIALS) {
            if (stack.is(material)) {
                return true;
            }
        }
        return false;
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
