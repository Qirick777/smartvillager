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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;

/**
 * CARPENTER work goal (design document section 3, 9): fell a real tree and replant.
 *
 * <p>Only logs that are part of an actual tree are chopped — a log is a valid target only if its
 * trunk column has leaves next to it. This deliberately excludes bare log pillars (e.g. building
 * supports), which have no attached leaves. On felling, the trunk column and its surrounding leaf
 * canopy are removed (so the villager is not blocked by floating leaves) and an oak sapling is
 * replanted. Logs collapse into unified {@code WOOD} (1 log = {@link ResourceType#MILLI_UNIT}).</p>
 */
public class ChopTreeGoal extends MoveToBlockGoal {

    /** Safety cap on how many logs a single fell walks upward. */
    private static final int MAX_TRUNK_HEIGHT = 32;
    /** Horizontal radius around the trunk within which canopy leaves are cleared. */
    private static final int CANOPY_RADIUS = 4;

    private final SmartVillager villager;

    public ChopTreeGoal(SmartVillager villager, double speedModifier, int searchRange) {
        super(villager, speedModifier, searchRange, 2);
        this.villager = villager;
    }

    @Override
    public boolean canUse() {
        return villager.getJob() == Job.CARPENTER && villager.hasActiveGatherTask() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return villager.getJob() == Job.CARPENTER && villager.hasActiveGatherTask()
                && super.canContinueToUse();
    }

    @Override
    protected int nextStartTick(PathfinderMob mob) {
        return reducedTickDelay(10);
    }

    @Override
    public double acceptedDistance() {
        return 2.0D;
    }

    /** Targets the base log of a tree: a log with no log beneath it whose column has leaves. */
    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        return level.getBlockState(pos).is(BlockTags.LOGS)
                && !level.getBlockState(pos.below()).is(BlockTags.LOGS)
                && columnHasLeaves(level, pos);
    }

    /** @return true if any log in the upward column from {@code base} has an adjacent leaf block. */
    private boolean columnHasLeaves(LevelReader level, BlockPos base) {
        BlockPos.MutableBlockPos cursor = base.mutable();
        for (int h = 0; h < MAX_TRUNK_HEIGHT && level.getBlockState(cursor).is(BlockTags.LOGS); h++) {
            for (Direction dir : Direction.values()) {
                if (level.getBlockState(cursor.relative(dir)).is(BlockTags.LEAVES)) {
                    return true;
                }
            }
            cursor.move(Direction.UP);
        }
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.villager.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        if (this.isReachedTarget()) {
            this.villager.stopBreaking();
            fellTree(serverLevel);
            return;
        }
        // Still approaching: if leaves block the way, break through them like a player would.
        BlockPos leaf = findObstructingLeaf(serverLevel);
        if (leaf != null) {
            this.villager.getLookControl().setLookAt(
                    leaf.getX() + 0.5D, leaf.getY() + 0.5D, leaf.getZ() + 0.5D);
            this.villager.startBreaking(leaf);
        } else {
            this.villager.stopBreaking();
        }
    }

    @Override
    public void stop() {
        super.stop();
        this.villager.stopBreaking();
    }

    /**
     * Finds a leaf block near the villager that lies between it and the trunk (closer to the target
     * than the villager) and is within reach — i.e. one that is blocking the approach.
     */
    private BlockPos findObstructingLeaf(ServerLevel level) {
        BlockPos target = this.blockPos;
        BlockPos self = this.villager.blockPosition();
        double selfToTargetSq = self.distSqr(target);
        BlockPos best = null;
        double bestReachSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -1; dy <= 2; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    cursor.set(self.getX() + dx, self.getY() + dy, self.getZ() + dz);
                    if (!level.getBlockState(cursor).is(BlockTags.LEAVES)
                            || cursor.distSqr(target) >= selfToTargetSq) {
                        continue;
                    }
                    double reachSq = this.villager.distanceToSqr(
                            cursor.getX() + 0.5D, cursor.getY() + 0.5D, cursor.getZ() + 0.5D);
                    if (reachSq <= 9.0D && reachSq < bestReachSq) {
                        bestReachSq = reachSq;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }

    private void fellTree(ServerLevel level) {
        if (!isValidTarget(level, this.blockPos)) {
            return;
        }
        BlockPos base = this.blockPos.immutable();
        BlockPos.MutableBlockPos cursor = base.mutable();
        int logs = 0;
        int topY = base.getY();
        while (logs < MAX_TRUNK_HEIGHT && level.getBlockState(cursor).is(BlockTags.LOGS)) {
            topY = cursor.getY();
            level.destroyBlock(cursor, false); // no drops; wood goes to village storage instead
            logs++;
            cursor.move(Direction.UP);
        }
        if (logs == 0) {
            return;
        }
        clearCanopyLeaves(level, base, topY);
        this.villager.swing(InteractionHand.MAIN_HAND);
        this.villager.giveItem(new ItemStack(Items.OAK_LOG, logs)); // unified wood (design 9)
        this.villager.addVillageResource(ResourceType.WOOD, logs * ResourceType.MILLI_UNIT);
        this.villager.reportProduced(logs); // quota progress (natural units: logs)

        // Replant a sapling on suitable ground where the trunk stood.
        if (level.getBlockState(base.below()).is(BlockTags.DIRT)) {
            level.setBlockAndUpdate(base, Blocks.OAK_SAPLING.defaultBlockState());
        }
    }

    /** Removes leaves around the felled trunk so floating leaves don't block the villager. */
    private void clearCanopyLeaves(ServerLevel level, BlockPos base, int topY) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = base.getY(); y <= topY + 3; y++) {
            for (int dx = -CANOPY_RADIUS; dx <= CANOPY_RADIUS; dx++) {
                for (int dz = -CANOPY_RADIUS; dz <= CANOPY_RADIUS; dz++) {
                    cursor.set(base.getX() + dx, y, base.getZ() + dz);
                    if (level.getBlockState(cursor).is(BlockTags.LEAVES)) {
                        level.destroyBlock(cursor, false);
                    }
                }
            }
        }
    }
}
