package com.yourname.smartvillager.entity.goal;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ToolTier;
import com.yourname.smartvillager.demand.DemandTask;
import com.yourname.smartvillager.demand.TaskState;
import com.yourname.smartvillager.entity.SmartVillager;
import com.yourname.smartvillager.task.TaskType;
import com.yourname.smartvillager.village.Village;
import com.yourname.smartvillager.village.VillageManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * MANUFACTURER work goal: crafts the pickaxe tier the demand graph asks for, from the material
 * items it has received (design v3). A wood pickaxe needs only logs; stone/iron pickaxes also need
 * cobblestone / iron ingots. On success it consumes the items, holds the finished tool, and marks
 * the CRAFT_TOOL task done (which unblocks the gather tasks that were waiting on the tool). Limited
 * to {@value #CRAFT_BUDGET} crafts per day.
 */
public class CraftToolGoal extends Goal {

    private static final int CRAFT_INTERVAL_TICKS = 100;
    private static final int CRAFT_BUDGET = 3;

    private final SmartVillager villager;
    private int cooldown;
    private long lastCraftDay = -1;
    private int craftsToday;

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
        long day = level.getDayTime() / 24000L;
        if (day != lastCraftDay) {
            lastCraftDay = day;
            craftsToday = 0;
        }
        if (craftsToday >= CRAFT_BUDGET) {
            return;
        }
        Village village = VillageManager.get(level).getVillageAtCore(villager.getVillageCorePos());
        if (village == null) {
            return;
        }
        DemandTask craft = highestCraftTask(village);
        if (craft == null) {
            return;
        }
        ToolTier tier = ToolTier.byLevel(craft.targetTier);
        if (tier != null && craftTier(village, tier)) {
            village.onTaskDone(craft);
            craftsToday++;
            VillageManager.get(level).setDirty();
            SmartVillagerMod.LOGGER.info(
                    "Manufacturer crafted {} pickaxe (village {}, {}/day): tools={}",
                    tier, village.getCorePos().toShortString(), craftsToday, village.getToolStock());
        }
    }

    /** @return the highest-priority not-done CRAFT_TOOL task in the graph, or null. */
    private DemandTask highestCraftTask(Village village) {
        DemandTask best = null;
        for (DemandTask task : village.getTaskGraph().values()) {
            if (task.type == TaskType.CRAFT_TOOL && task.state != TaskState.DONE
                    && (best == null || task.effectivePriority > best.effectivePriority)) {
                best = task;
            }
        }
        return best;
    }

    /** Consumes the tier's materials from inventory and produces a held pickaxe. */
    private boolean craftTier(Village village, ToolTier tier) {
        SimpleContainer inv = villager.getInventory();
        switch (tier) {
            case WOOD -> {
                if (count(inv, Items.OAK_LOG) < 2) {
                    return false;
                }
                inv.removeItemType(Items.OAK_LOG, 2);
            }
            case STONE -> {
                if (count(inv, Items.OAK_LOG) < 1 || count(inv, Items.COBBLESTONE) < 3) {
                    return false;
                }
                inv.removeItemType(Items.OAK_LOG, 1);
                inv.removeItemType(Items.COBBLESTONE, 3);
            }
            case IRON -> {
                if (count(inv, Items.OAK_LOG) < 1 || count(inv, Items.IRON_INGOT) < 3) {
                    return false;
                }
                inv.removeItemType(Items.OAK_LOG, 1);
                inv.removeItemType(Items.IRON_INGOT, 3);
            }
            default -> {
                return false;
            }
        }
        village.addTool(tier, 1);
        villager.giveItem(new ItemStack(pickaxeItem(tier)));
        villager.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    private static Item pickaxeItem(ToolTier tier) {
        return switch (tier) {
            case WOOD -> Items.WOODEN_PICKAXE;
            case STONE -> Items.STONE_PICKAXE;
            case IRON -> Items.IRON_PICKAXE;
        };
    }

    private static int count(SimpleContainer inv, Item item) {
        int total = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
