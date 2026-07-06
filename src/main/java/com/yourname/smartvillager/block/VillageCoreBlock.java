package com.yourname.smartvillager.block;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.demand.DemandTask;
import com.yourname.smartvillager.entity.SmartVillager;
import com.yourname.smartvillager.village.Village;
import com.yourname.smartvillager.village.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

import java.util.UUID;

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

    /** Right-clicking the core prints each villager's manager-assigned task. */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel) {
            Village village = VillageManager.get(serverLevel).getVillageAtCore(pos);
            if (village == null) {
                player.sendSystemMessage(Component.literal("No village here."));
            } else {
                player.sendSystemMessage(Component.literal(String.format(
                        "Village %s: pop=%d, %d demand tasks. Assignments:",
                        pos.toShortString(), village.getPopulation(), village.getTaskGraph().size())));
                for (UUID memberId : village.getMembers()) {
                    if (serverLevel.getEntity(memberId) instanceof SmartVillager member) {
                        DemandTask task = member.getCurrentTask();
                        String line = task == null
                                ? String.format("  %s: idle", member.getJob())
                                : String.format("  %s: %s (%d/%d)", member.getJob(), task.type,
                                        task.amountDone, task.amountRequired);
                        player.sendSystemMessage(Component.literal(line));
                    }
                }
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
