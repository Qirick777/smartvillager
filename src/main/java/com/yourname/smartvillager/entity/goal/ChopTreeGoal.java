package com.yourname.smartvillager.entity.goal;

import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.entity.SmartVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * CARPENTER work goal (design document section 3, 9): walk to the base of a tree, fell its whole
 * log column, replant an oak sapling, and add the felled logs to village storage as {@code WOOD}
 * (all logs collapse into one unified WOOD resource, 1 log = {@link ResourceType#MILLI_UNIT}
 * milli-wood). Active only while the villager's job is {@link Job#CARPENTER}.
 */
public class ChopTreeGoal extends MoveToBlockGoal {

    /** Safety cap on how many logs a single fell walks upward. */
    private static final int MAX_TRUNK_HEIGHT = 32;

    private final SmartVillager villager;

    public ChopTreeGoal(SmartVillager villager, double speedModifier, int searchRange) {
        super(villager, speedModifier, searchRange, 2);
        this.villager = villager;
    }

    @Override
    public boolean canUse() {
        return villager.getJob() == Job.CARPENTER && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return villager.getJob() == Job.CARPENTER && super.canContinueToUse();
    }

    @Override
    protected int nextStartTick(PathfinderMob mob) {
        return reducedTickDelay(10);
    }

    @Override
    public double acceptedDistance() {
        return 2.0D;
    }

    /** Targets the base log of a tree (a log with no log directly beneath it). */
    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.LOGS)
                && !level.getBlockState(pos.below()).is(BlockTags.LOGS);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isReachedTarget() && this.villager.level() instanceof ServerLevel serverLevel) {
            fellTree(serverLevel);
        }
    }

    private void fellTree(ServerLevel level) {
        if (!level.getBlockState(this.blockPos).is(BlockTags.LOGS)) {
            return;
        }
        BlockPos.MutableBlockPos cursor = this.blockPos.mutable();
        int logs = 0;
        while (logs < MAX_TRUNK_HEIGHT && level.getBlockState(cursor).is(BlockTags.LOGS)) {
            level.destroyBlock(cursor, false); // no drops; wood goes to village storage instead
            logs++;
            cursor.move(Direction.UP);
        }
        if (logs == 0) {
            return;
        }
        this.villager.swing(InteractionHand.MAIN_HAND);
        this.villager.addVillageResource(ResourceType.WOOD, logs * ResourceType.MILLI_UNIT);

        // Replant a sapling on suitable ground where the trunk stood.
        BlockState ground = level.getBlockState(this.blockPos.below());
        if (ground.is(BlockTags.DIRT)) {
            level.setBlockAndUpdate(this.blockPos, Blocks.OAK_SAPLING.defaultBlockState());
        }
    }
}
