package com.yourname.smartvillager.entity;

import com.yourname.smartvillager.village.VillageManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeSpawnEggItem;

import java.util.function.Supplier;

/**
 * Spawn egg for the Smart Villager. Unlike a plain spawn egg, it only works within
 * {@link VillageManager#SPAWN_RANGE} blocks of an active village core (design document section 5:
 * "소환 판정은 코어 기준 64블록 고정").
 */
public class SmartVillagerSpawnEggItem extends ForgeSpawnEggItem {

    public SmartVillagerSpawnEggItem(Supplier<? extends EntityType<? extends Mob>> type,
                                     int backgroundColor, int highlightColor, Properties properties) {
        super(type, backgroundColor, highlightColor, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        // The village registry is server-only; validate there and let the server decide.
        if (level instanceof ServerLevel serverLevel) {
            if (!VillageManager.get(serverLevel).isWithinSpawnRange(context.getClickedPos())) {
                Player player = context.getPlayer();
                if (player != null) {
                    player.displayClientMessage(
                            Component.translatable("message.smartvillager.out_of_range"), true);
                }
                return InteractionResult.FAIL;
            }
        }
        return super.useOn(context);
    }
}
