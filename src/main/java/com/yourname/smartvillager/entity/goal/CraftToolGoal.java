package com.yourname.smartvillager.entity.goal;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ToolTier;
import com.yourname.smartvillager.entity.SmartVillager;
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
 * MANUFACTURER work goal (design v3 section 7): crafts tools from the material items it has
 * received (via delivery) — 1 log worth of sticks + 3 head material — and holds the finished tool.
 * Upgrades to an iron tool when it has iron ingots, else makes a stone tool. Limited to
 * {@value #CRAFT_BUDGET} crafts per day.
 */
public class CraftToolGoal extends Goal {

    private static final int CRAFT_INTERVAL_TICKS = 100; // ~5s between attempts
    private static final int CRAFT_BUDGET = 3;           // per day
    private static final int MATERIAL_PER_TOOL = 3;

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
        if (village != null) {
            craft(level, village);
        }
    }

    private void craft(ServerLevel level, Village village) {
        SimpleContainer inv = villager.getInventory();
        if (count(inv, Items.OAK_LOG) < 1) {
            return; // no wood for sticks yet (delivery/demand will supply it)
        }

        ToolTier tier;
        Item material;
        if (count(inv, Items.IRON_INGOT) >= MATERIAL_PER_TOOL) {
            tier = ToolTier.IRON;
            material = Items.IRON_INGOT;
        } else if (count(inv, Items.COBBLESTONE) >= MATERIAL_PER_TOOL) {
            tier = ToolTier.STONE;
            material = Items.COBBLESTONE;
        } else {
            return; // no head material yet
        }

        inv.removeItemType(Items.OAK_LOG, 1);
        inv.removeItemType(material, MATERIAL_PER_TOOL);
        village.addTool(tier, 1);
        villager.giveItem(new ItemStack(tier == ToolTier.IRON ? Items.IRON_PICKAXE : Items.STONE_PICKAXE));
        villager.swing(InteractionHand.MAIN_HAND);
        craftsToday++;
        VillageManager.get(level).setDirty();
        SmartVillagerMod.LOGGER.info(
                "Manufacturer crafted {} tool from inventory (village {}, {} today): tools={}",
                tier, village.getCorePos().toShortString(), craftsToday, village.getToolStock());
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
