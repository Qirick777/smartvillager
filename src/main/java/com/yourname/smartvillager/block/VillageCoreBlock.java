package com.yourname.smartvillager.block;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.village.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The Village Core Block. Placing it establishes a village at its position; destroying it starts
 * the village's grace period (design document section 5).
 */
public class VillageCoreBlock extends Block {

    public VillageCoreBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level instanceof ServerLevel serverLevel) {
            SmartVillagerMod.LOGGER.info("Village Core placed at {}", pos);
            VillageManager.get(serverLevel).createVillage(pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState,
                         boolean isMoving) {
        // Only react when this block is actually being removed/replaced by a different block,
        // not on a state change that keeps it a village core.
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel) {
            VillageManager.get(serverLevel).onCoreRemoved(serverLevel, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
