package com.yourname.smartvillager.event;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.entity.SmartVillager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Credits a hunter's village when it kills its quarry (design document section 9): sheep yield
 * {@link ResourceType#WOOL}, cows and chickens yield {@link ResourceType#FOOD}. Other animals give
 * nothing (and hunters don't target them).
 */
@Mod.EventBusSubscriber(modid = SmartVillagerMod.MOD_ID)
public final class HuntRewardHandler {

    /** WOOL per sheep. */
    private static final int WOOL_PER_SHEEP = 1;
    /** FOOD per cow. */
    private static final int FOOD_PER_COW = 4;
    /** FOOD per chicken. */
    private static final int FOOD_PER_CHICKEN = 1;

    private HuntRewardHandler() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof SmartVillager villager)
                || villager.getJob() != Job.HUNTER) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim instanceof Sheep) {
            villager.addVillageResource(ResourceType.WOOL, WOOL_PER_SHEEP); // legacy ledger
            villager.giveItem(new ItemStack(Items.WHITE_WOOL, WOOL_PER_SHEEP));
        } else if (victim instanceof Cow) {
            villager.addVillageResource(ResourceType.FOOD, FOOD_PER_COW);
            villager.giveItem(new ItemStack(Items.BEEF, 2));
        } else if (victim instanceof Chicken) {
            villager.addVillageResource(ResourceType.FOOD, FOOD_PER_CHICKEN);
            villager.giveItem(new ItemStack(Items.CHICKEN, 1));
        }
    }

    /**
     * The hunter takes the whole catch into village storage, so the killed animal must not also
     * drop items on the ground. Cancel the drops for a hunter's quarry.
     */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (event.getSource().getEntity() instanceof SmartVillager villager
                && villager.isHuntTarget(event.getEntity())) {
            event.setCanceled(true);
        }
    }
}
