package com.yourname.smartvillager.entity;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.entity.goal.FarmGoal;
import com.yourname.smartvillager.entity.goal.GatherAtVillageGoal;
import com.yourname.smartvillager.village.Village;
import com.yourname.smartvillager.village.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

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

    public SmartVillager(EntityType<? extends SmartVillager> type, Level level) {
        super(type, level);
    }

    /** Base attributes; registered via {@code EntityAttributeCreationEvent}. */
    public static AttributeSupplier.Builder createAttributes() {
        return AgeableMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new GatherAtVillageGoal(this, 0.6D));
        this.goalSelector.addGoal(2, new FarmGoal(this, 0.8D, 12));
        this.goalSelector.addGoal(3, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
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
        tickMeals(serverLevel);
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
