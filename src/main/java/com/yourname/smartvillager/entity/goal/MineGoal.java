package com.yourname.smartvillager.entity.goal;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.entity.SmartVillager;
import com.yourname.smartvillager.village.Village;
import com.yourname.smartvillager.village.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * MINER work goal (design document section 9): walk to nearby stone/coal/ore, mine it, and store
 * the yield. Coal is stored as milli-coal ({@link ResourceType#MILLI_UNIT} per coal); metal ores
 * become RAW_ORE and are auto-smelted into INGOT using integer milli-coal math
 * ({@link Village#COAL_PER_SMELT_MILLI} per smelt). Active only while the job is {@link Job#MINER}.
 */
public class MineGoal extends MoveToBlockGoal {

    private final SmartVillager villager;

    public MineGoal(SmartVillager villager, double speedModifier, int searchRange) {
        super(villager, speedModifier, searchRange, 2);
        this.villager = villager;
    }

    @Override
    public boolean canUse() {
        return villager.getJob() == Job.MINER && villager.hasActiveGatherTask() && super.canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return villager.getJob() == Job.MINER && villager.hasActiveGatherTask()
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

    @Override
    protected boolean isValidTarget(LevelReader level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)
                || state.is(BlockTags.COAL_ORES) || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.GOLD_ORES) || state.is(BlockTags.COPPER_ORES);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.isReachedTarget() && this.villager.level() instanceof ServerLevel serverLevel) {
            mine(serverLevel);
        }
    }

    private void mine(ServerLevel level) {
        BlockState state = level.getBlockState(this.blockPos);
        if (!isValidTarget(level, this.blockPos)) {
            return;
        }
        level.destroyBlock(this.blockPos, false); // no drops; yield goes to village storage
        this.villager.swing(InteractionHand.MAIN_HAND);
        this.villager.reportProduced(1); // quota progress (1 per block mined)

        BlockPos core = this.villager.getVillageCorePos();
        if (core == null) {
            return;
        }
        VillageManager manager = VillageManager.get(level);
        Village village = manager.getVillageAtCore(core);
        if (village == null) {
            return;
        }

        if (state.is(Blocks.STONE) || state.is(Blocks.DEEPSLATE)) {
            village.addStorage(ResourceType.STONE, 1);
            villager.giveItem(new ItemStack(Items.COBBLESTONE));
        } else if (state.is(BlockTags.COAL_ORES)) {
            village.addStorage(ResourceType.COAL, ResourceType.MILLI_UNIT); // 1 coal = 1000 milli
            villager.giveItem(new ItemStack(Items.COAL));
        } else {
            village.addStorage(ResourceType.RAW_ORE, 1);
            villager.giveItem(new ItemStack(Items.RAW_IRON));
            boolean smelted = village.trySmeltOre();
            if (smelted) {
                // Auto-smelt: swap one raw ore for an ingot in the inventory too.
                villager.getInventory().removeItemType(Items.RAW_IRON, 1);
                villager.giveItem(new ItemStack(Items.IRON_INGOT));
            }
            SmartVillagerMod.LOGGER.info(
                    "Miner smelt={} coalMilli={} rawOre={} ingot={}",
                    smelted, village.getStorage(ResourceType.COAL),
                    village.getStorage(ResourceType.RAW_ORE), village.getStorage(ResourceType.INGOT));
        }
        manager.setDirty();
    }
}
