package com.yourname.smartvillager.entity;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.demand.DemandTask;
import com.yourname.smartvillager.demand.TaskState;
import com.yourname.smartvillager.entity.goal.ChopTreeGoal;
import com.yourname.smartvillager.entity.goal.CraftToolGoal;
import com.yourname.smartvillager.entity.goal.DepositGoal;
import com.yourname.smartvillager.entity.goal.FarmGoal;
import com.yourname.smartvillager.entity.goal.GatherAtVillageGoal;
import com.yourname.smartvillager.entity.goal.MineGoal;
import com.yourname.smartvillager.registry.ModItems;
import com.yourname.smartvillager.village.Village;
import com.yourname.smartvillager.village.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

import javax.annotation.Nullable;

import java.util.UUID;

/**
 * The Smart Villager mob (design document sections 2 and 6).
 *
 * <p>Extends {@link AgeableMob} (→ {@code PathfinderMob}) directly — no vanilla {@code Villager},
 * professions, POIs, or Brain — and uses the classic goal system in {@link #registerGoals()}.</p>
 *
 * <p>Phase 3 adds a {@link Job} and village membership: on the server the villager joins the
 * nearest active village in range and is assigned a job by {@link VillageManager}.</p>
 */
public class SmartVillager extends AgeableMob {

    /** Starting food buffer for a freshly spawned villager. */
    private static final int INITIAL_FOOD_STOCK = 6;

    @Nullable
    private Job job;
    /** Core position of the village this villager belongs to, or {@code null} if unaffiliated. */
    @Nullable
    private BlockPos villageCorePos;

    /** Personal food buffer, consumed 3x/day (design section 6). */
    private int foodStock = INITIAL_FOOD_STOCK;
    /** Last meal slot (0=morning, 1=noon, 2=evening); -1 until first evaluated. */
    private int lastMealSlot = -1;

    /** Id of the demand task this villager is currently working on, or {@code null} if idle. */
    @Nullable
    private UUID currentTaskId;

    /** Number of personal inventory slots each villager carries. */
    public static final int INVENTORY_SIZE = 10;
    /** The villager's personal 10-slot inventory (real item stacks). */
    private final SimpleContainer inventory = new SimpleContainer(INVENTORY_SIZE);
    /** Head position of this villager's claimed bed, or {@code null} if none. */
    @Nullable
    private BlockPos bedPos;
    /** Position of this villager's personal chest (placed beside its bed), or {@code null}. */
    @Nullable
    private BlockPos chestPos;

    // --- Block-breaking controller (reusable by work goals) ----------------
    /** Ticks of "mining time" per point of block hardness (leaves 0.2 -> ~6 ticks). */
    private static final float BREAK_TICKS_PER_HARDNESS = 30.0F;

    @Nullable
    private BlockPos breakTarget;
    private int breakProgressTicks;
    private int breakTotalTicks;
    private int lastBreakStage = -1;

    public SmartVillager(EntityType<? extends SmartVillager> type, Level level) {
        super(type, level);
    }

    /** Base attributes; registered via {@code EntityAttributeCreationEvent}. */
    public static AttributeSupplier.Builder createAttributes() {
        return AgeableMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new DepositGoal(this, 0.7D));
        this.goalSelector.addGoal(1, new GatherAtVillageGoal(this, 0.6D));
        this.goalSelector.addGoal(2, new FarmGoal(this, 0.8D, 12));
        this.goalSelector.addGoal(2, new ChopTreeGoal(this, 0.8D, 12));
        this.goalSelector.addGoal(2, new MineGoal(this, 0.8D, 12));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(2, new CraftToolGoal(this));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        // HUNTER targeting: only sheep/cows/chickens, and only while this villager is a hunter.
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(
                this, Animal.class, 10, true, false, this::isHuntTarget));
    }

    /** @return true if this villager is a hunter with quota and the entity is valid quarry. */
    public boolean isHuntTarget(LivingEntity entity) {
        return job == Job.HUNTER && hasActiveGatherTask()
                && (entity instanceof Sheep || entity instanceof Cow || entity instanceof Chicken);
    }

    // --- Job / village membership ------------------------------------------

    @Nullable
    public Job getJob() {
        return job;
    }

    public void setJob(@Nullable Job job) {
        this.job = job;
        // Debug: float the job name above the entity until real models/UI exist.
        if (job == null) {
            this.setCustomName(null);
            this.setCustomNameVisible(false);
        } else {
            this.setCustomName(Component.literal(job.name()));
            this.setCustomNameVisible(true);
        }
    }

    @Nullable
    public BlockPos getVillageCorePos() {
        return villageCorePos;
    }

    public void setVillageCorePos(@Nullable BlockPos corePos) {
        this.villageCorePos = corePos == null ? null : corePos.immutable();
    }

    /** Adds a resource to this villager's village storage (no-op if unaffiliated). */
    public void addVillageResource(ResourceType type, int amount) {
        if (villageCorePos != null && level() instanceof ServerLevel serverLevel) {
            VillageManager manager = VillageManager.get(serverLevel);
            Village village = manager.getVillageAtCore(villageCorePos);
            if (village != null) {
                village.addStorage(type, amount);
                manager.setDirty();
            }
        }
    }

    public int getFoodStock() {
        return foodStock;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        // Unaffiliated villagers periodically try to join a village and get a job.
        if (villageCorePos == null && this.tickCount % 20 == 0) {
            VillageManager.get(serverLevel).tryJoinAndAssign(serverLevel, this);
        }
        if (bedPos == null && villageCorePos != null && this.tickCount % 40 == 0) {
            tickHomeClaim(serverLevel);
        }
        if (this.tickCount % 20 == 0) {
            assignTaskIfNeeded(serverLevel);
        }
        tickMeals(serverLevel);
        tickBreaking(serverLevel);
    }

    // --- Demand tasks (quota-driven work) ----------------------------------

    @Nullable
    private Village village(ServerLevel level) {
        return villageCorePos == null ? null : VillageManager.get(level).getVillageAtCore(villageCorePos);
    }

    /** @return the demand task this villager is working on, or {@code null}. */
    @Nullable
    public DemandTask getCurrentTask() {
        if (currentTaskId == null || !(level() instanceof ServerLevel serverLevel)) {
            return null;
        }
        Village village = village(serverLevel);
        return village == null ? null : village.getTaskGraph().get(currentTaskId);
    }

    /** @return true if this villager has a gather task with remaining quota. */
    public boolean hasActiveGatherTask() {
        DemandTask task = getCurrentTask();
        return task != null && task.type.isGather() && task.amountDone < task.amountRequired
                && (task.state == TaskState.READY || task.state == TaskState.IN_PROGRESS
                    || task.state == TaskState.ASSIGNED);
    }

    /** Credits {@code amount} toward the current gather task; completes it when the quota is met. */
    public void reportProduced(int amount) {
        DemandTask task = getCurrentTask();
        if (task == null || !task.type.isGather()) {
            return;
        }
        task.amountDone += amount;
        if (task.amountDone >= task.amountRequired && level() instanceof ServerLevel serverLevel) {
            Village village = village(serverLevel);
            if (village != null) {
                village.onTaskDone(task);
            }
            currentTaskId = null;
        }
    }

    /** Picks the highest-priority READY gather task for this villager's job, if idle. */
    private void assignTaskIfNeeded(ServerLevel level) {
        DemandTask current = getCurrentTask();
        if (current != null && current.state != TaskState.DONE
                && current.amountDone < current.amountRequired) {
            return; // still working on a valid task
        }
        currentTaskId = null;
        Village village = village(level);
        if (village == null || job == null) {
            return;
        }
        DemandTask best = null;
        for (DemandTask task : village.getTaskGraph().values()) {
            if (task.assigneeJob != job || !task.type.isGather()
                    || task.amountDone >= task.amountRequired) {
                continue;
            }
            boolean claimable = task.state == TaskState.READY
                    || (task.state == TaskState.IN_PROGRESS && task.type.divisible());
            if (claimable && (best == null || task.effectivePriority > best.effectivePriority)) {
                best = task;
            }
        }
        if (best != null) {
            best.state = TaskState.IN_PROGRESS;
            currentTaskId = best.id;
        }
    }

    // --- Home: claim a bed + free chest ------------------------------------

    public SimpleContainer getInventory() {
        return inventory;
    }

    /** Adds an item to the villager's inventory, dropping any overflow at its feet. */
    public void giveItem(ItemStack stack) {
        ItemStack leftover = inventory.addItem(stack);
        if (!leftover.isEmpty()) {
            spawnAtLocation(leftover);
        }
    }

    /** @return total number of items across all inventory slots. */
    public int totalInventoryCount() {
        int total = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            total += inventory.getItem(i).getCount();
        }
        return total;
    }

    @Nullable
    public BlockPos getBedPos() {
        return bedPos;
    }

    @Nullable
    public BlockPos getChestPos() {
        return chestPos;
    }

    /** Claims a nearby unclaimed bed and places a free personal chest beside it. */
    private void tickHomeClaim(ServerLevel level) {
        Village village = VillageManager.get(level).getVillageAtCore(villageCorePos);
        if (village == null) {
            return;
        }
        BlockPos bedHead = findUnclaimedBed(level, village);
        if (bedHead == null || !village.claimBed(bedHead, getUUID())) {
            return;
        }
        this.bedPos = bedHead;
        this.chestPos = placeFreeChest(level, bedHead);
        VillageManager.get(level).setDirty();
        SmartVillagerMod.LOGGER.info("Villager {} claimed bed {} (chest {})",
                getUUID(), bedHead, chestPos);
    }

    /** Finds the nearest unclaimed bed head within a box around this villager. */
    @Nullable
    private BlockPos findUnclaimedBed(ServerLevel level, Village village) {
        int horizontal = 24;
        int vertical = 6;
        BlockPos origin = blockPosition();
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -vertical; dy <= vertical; dy++) {
            for (int dx = -horizontal; dx <= horizontal; dx++) {
                for (int dz = -horizontal; dz <= horizontal; dz++) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!(state.getBlock() instanceof BedBlock)
                            || state.getValue(BedBlock.PART) != BedPart.HEAD
                            || village.isBedClaimed(cursor)) {
                        continue;
                    }
                    double d = cursor.distSqr(origin);
                    if (d < bestSq) {
                        bestSq = d;
                        best = cursor.immutable();
                    }
                }
            }
        }
        return best;
    }

    /** Places the villager's free chest in a replaceable spot beside the bed head. */
    @Nullable
    private BlockPos placeFreeChest(ServerLevel level, BlockPos bedHead) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = bedHead.relative(dir);
            if (level.getBlockState(side).isAir()) {
                level.setBlockAndUpdate(side, Blocks.CHEST.defaultBlockState());
                return side;
            }
        }
        // Fallback: force the chest on the east side.
        BlockPos side = bedHead.east();
        level.setBlockAndUpdate(side, Blocks.CHEST.defaultBlockState());
        return side;
    }

    // --- Inspector tool ----------------------------------------------------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).is(ModItems.VILLAGER_INSPECTOR.get())) {
            if (!level().isClientSide) {
                player.sendSystemMessage(describeInventory());
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.mobInteract(player, hand);
    }

    private Component describeInventory() {
        StringBuilder sb = new StringBuilder();
        sb.append("Smart Villager [").append(job == null ? "no job" : job.name()).append("] inventory:");
        boolean any = false;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) {
                sb.append("\n  [").append(i).append("] ")
                        .append(stack.getCount()).append("x ")
                        .append(stack.getHoverName().getString());
                any = true;
            }
        }
        if (!any) {
            sb.append(" (empty, ").append(INVENTORY_SIZE).append(" slots)");
        }
        return Component.literal(sb.toString());
    }

    // --- Block-breaking controller -----------------------------------------
    // A reusable, player-like block breaker: a work goal points it at an obstructing/target block
    // and it mines that block over time (crack animation, hit sounds, hardness-based speed), then
    // destroys it (break sound + particles). Currently used to clear leaves in the carpenter's way;
    // it works on any block, so other jobs can reuse it later.

    /** Point the breaker at {@code pos}; restarts progress if it's a new block. */
    public void startBreaking(BlockPos pos) {
        if (pos.equals(breakTarget)) {
            return;
        }
        clearBreakProgress();
        breakTarget = pos.immutable();
        breakProgressTicks = 0;
        lastBreakStage = -1;
        float hardness = level().getBlockState(pos).getDestroySpeed(level(), pos);
        breakTotalTicks = hardness < 0.0F
                ? Integer.MAX_VALUE
                : Math.max(1, (int) (hardness * BREAK_TICKS_PER_HARDNESS));
    }

    /** Stop breaking and clear any crack overlay. */
    public void stopBreaking() {
        clearBreakProgress();
        breakTarget = null;
        lastBreakStage = -1;
    }

    private void clearBreakProgress() {
        if (breakTarget != null && level() instanceof ServerLevel serverLevel) {
            serverLevel.destroyBlockProgress(getId(), breakTarget, -1);
        }
    }

    private void tickBreaking(ServerLevel level) {
        if (breakTarget == null) {
            return;
        }
        BlockState state = level.getBlockState(breakTarget);
        boolean tooFar = distanceToSqr(breakTarget.getX() + 0.5D, breakTarget.getY() + 0.5D,
                breakTarget.getZ() + 0.5D) > 20.0D;
        if (state.isAir() || tooFar) {
            stopBreaking();
            return;
        }

        breakProgressTicks++;
        if (breakProgressTicks % 4 == 1) {
            swing(InteractionHand.MAIN_HAND);
            SoundType sound = state.getSoundType();
            level.playSound(null, breakTarget, sound.getHitSound(), SoundSource.BLOCKS,
                    0.25F, sound.getPitch() * 0.5F);
        }

        int stage = (int) (10.0F * breakProgressTicks / breakTotalTicks);
        if (stage != lastBreakStage) {
            lastBreakStage = stage;
            level.destroyBlockProgress(getId(), breakTarget, Math.min(9, stage));
        }

        if (breakProgressTicks >= breakTotalTicks) {
            level.destroyBlock(breakTarget, false); // break sound + particles (levelEvent 2001)
            stopBreaking();
        }
    }

    /** Consumes one food per meal at the three daily meal boundaries (design section 6). */
    private void tickMeals(ServerLevel level) {
        long dayTime = level.getDayTime() % 24000L;
        int slot = dayTime < 6000L ? 0 : (dayTime < 12000L ? 1 : 2);
        if (lastMealSlot == -1) {
            lastMealSlot = slot; // establish baseline without eating on the first evaluation
            return;
        }
        if (slot == lastMealSlot) {
            return;
        }
        lastMealSlot = slot;

        if (foodStock > 0) {
            foodStock--;
        } else if (villageCorePos != null) {
            // Own buffer empty: eat from communal storage if the village has any FOOD.
            VillageManager manager = VillageManager.get(level);
            Village village = manager.getVillageAtCore(villageCorePos);
            if (village != null && village.getStorage(ResourceType.FOOD) > 0) {
                village.addStorage(ResourceType.FOOD, -1);
                manager.setDirty();
            }
        }
        SmartVillagerMod.LOGGER.info("Villager {} meal (slot {}): foodStock={}",
                getUUID(), slot, foodStock);
    }

    @Override
    public void remove(RemovalReason reason) {
        // On death/discard (not chunk unload), leave the village so counts stay correct.
        if (reason.shouldDestroy() && villageCorePos != null
                && level() instanceof ServerLevel serverLevel) {
            VillageManager.get(serverLevel).onMemberRemoved(villageCorePos, getUUID(), job);
        }
        super.remove(reason);
    }

    // --- Persistence -------------------------------------------------------

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (job != null) {
            tag.putString("Job", job.getSerializedName());
        }
        if (villageCorePos != null) {
            tag.put("VillageCore", NbtUtils.writeBlockPos(villageCorePos));
        }
        tag.putInt("FoodStock", foodStock);
        tag.put("Inventory", inventory.createTag());
        if (bedPos != null) {
            tag.put("BedPos", NbtUtils.writeBlockPos(bedPos));
        }
        if (chestPos != null) {
            tag.put("ChestPos", NbtUtils.writeBlockPos(chestPos));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.job = tag.contains("Job") ? Job.byName(tag.getString("Job")) : null;
        this.villageCorePos = tag.contains("VillageCore")
                ? NbtUtils.readBlockPos(tag.getCompound("VillageCore"))
                : null;
        if (tag.contains("FoodStock")) {
            this.foodStock = tag.getInt("FoodStock");
        }
        inventory.fromTag(tag.getList("Inventory", 10)); // 10 = TAG_COMPOUND
        this.bedPos = tag.contains("BedPos") ? NbtUtils.readBlockPos(tag.getCompound("BedPos")) : null;
        this.chestPos = tag.contains("ChestPos")
                ? NbtUtils.readBlockPos(tag.getCompound("ChestPos"))
                : null;
    }

    /** No breeding yet (Phase 9); required by {@link AgeableMob}. */
    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    /** Smart Villagers are village residents, so they never despawn on distance. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
