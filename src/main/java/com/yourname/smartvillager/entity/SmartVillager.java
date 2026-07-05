package com.yourname.smartvillager.entity;

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
 * The Smart Villager mob.
 *
 * <p>Per design document section 2, this is a brand-new entity that extends
 * {@link AgeableMob} (which itself extends {@code PathfinderMob}) directly — it does not reuse
 * vanilla {@code Villager}, its professions, POIs, or the Brain system. AI is built with the
 * classic goal system in {@link #registerGoals()}.</p>
 *
 * <p>Phase 2 is the skeleton: attributes, a few basic movement goals, and no despawning. Jobs,
 * the daily cycle behaviours, and village membership are layered on in later phases.</p>
 */
public class SmartVillager extends AgeableMob {

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
        // Minimal vanilla goals for the skeleton (Phase 2.2). Job/cycle goals come later.
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new WaterAvoidingRandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(2, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RandomLookAroundGoal(this));
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
