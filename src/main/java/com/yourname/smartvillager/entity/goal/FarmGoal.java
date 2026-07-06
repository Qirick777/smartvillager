package com.yourname.smartvillager.entity.goal;

import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.entity.SmartVillager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * FARMER work goal: walk to a fully grown crop, harvest it, and replant at age 0 — the vanilla
 * farmer harvest/replant loop (design document section 3). Active only while the villager's job is
 * {@link Job#FARMER}. Each harvest adds one {@link ResourceType#FOOD} to the village storage.
 */
public class FarmGoal extends MoveToBlockGoal {

    private final SmartVillager villager;

    public FarmGoal(SmartVillager villager, double speedModifier, int searchRange) {
        super(villager, speedModifier, searchRange, 2);
        this.villager = villager;
    }

    /**
     * Re-scan for the next crop quickly. The vanilla default is ~200–400 ticks, which makes the
     * farmer look idle; a short cooldown keeps it actively working the field.
     */
    @Override
    protected int nextStartTick(PathfinderMob mob) {
        return reducedTickDelay(10);
    }

    @Override
    public boolean canUse() {
        return villager.getJob() == Job.FARMER && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return villager.getJob() == Job.FARMER && super.canContinueToUse();
    }

    /** Targets fully grown crops. */
    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state);
    }

    /** A little extra reach so the villager can harvest from an adjacent tile. */
    @Override
    public double acceptedDistance() {
        return 2.0D;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isReachedTarget() && this.villager.level() instanceof ServerLevel serverLevel) {
            harvest(serverLevel);
        }
    }

    private void harvest(ServerLevel level) {
        BlockState state = level.getBlockState(this.blockPos);
        if (state.getBlock() instanceof CropBlock crop && crop.isMaxAge(state)) {
            level.destroyBlock(this.blockPos, false); // harvest with break particles, no drops
            level.setBlockAndUpdate(this.blockPos, crop.defaultBlockState()); // replant at age 0
            this.villager.swing(InteractionHand.MAIN_HAND);
            this.villager.addVillageResource(ResourceType.FOOD, 1);
        }
    }
}
