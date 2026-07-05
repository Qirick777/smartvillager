package com.yourname.smartvillager.village;

import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * A single village, keyed by its core position. Holds all persistent village state from design
 * document section 5.
 *
 * <p>This is a plain data holder with NBT (de)serialization; persistence to disk is handled by
 * {@link VillageManager}, which is the {@code SavedData} that owns the collection of villages.
 * Modelling one {@code SavedData} that contains many villages (rather than one {@code SavedData}
 * per village) keeps every village enumerable in a single tick pass and stored in a single
 * {@code data/smartvillager_villages.dat} file.</p>
 */
public class Village {

    /** Tier-1 bed recognition radius around the core, in blocks (design: 64). */
    public static final int CORE_RECOGNITION_RADIUS = 64;
    /** Expansion radius around each recognized bed, in blocks (design: 32). */
    public static final int BED_EXPANSION_RADIUS = 32;

    private BlockPos corePos;
    private VillageState state = VillageState.ACTIVE;

    /** Beds recognized as belonging to this village (see {@link VillageBedScanner}). */
    private final List<BlockPos> recognizedBeds = new ArrayList<>();
    /** UUIDs of this village's smart villagers. */
    private final List<UUID> members = new ArrayList<>();
    /** Headcount per job. */
    private final Map<Job, Integer> jobCounts = new EnumMap<>(Job.class);
    /** Shared resource storage (WOOD/COAL are in milli-units; see {@link ResourceType}). */
    private final Map<ResourceType, Integer> storage = new EnumMap<>(ResourceType.class);

    /**
     * Game-time tick at which the grace period ends while {@link VillageState#INACTIVE}.
     * Meaningless (and not consulted) while {@link VillageState#ACTIVE}.
     */
    private long graceDeadlineTick;

    // TODO (Phase 5/8): demandQueue: List<VillagerTask> and houseSites: List<HouseSite> are added
    // once those types exist (manager demand calculation and the architect/schematic system).

    public Village(BlockPos corePos) {
        this.corePos = corePos.immutable();
    }

    private Village() {
    }

    // --- Core / state -------------------------------------------------------

    public BlockPos getCorePos() {
        return corePos;
    }

    public VillageState getState() {
        return state;
    }

    public boolean isActive() {
        return state == VillageState.ACTIVE;
    }

    public long getGraceDeadlineTick() {
        return graceDeadlineTick;
    }

    /** Marks the village active (e.g. on core (re)placement) and clears any grace deadline. */
    public void activate() {
        this.state = VillageState.ACTIVE;
        this.graceDeadlineTick = 0L;
    }

    /**
     * Marks the village inactive (core destroyed) and records when the grace period expires.
     *
     * @param graceDeadlineTick absolute game-time tick at which the village should be deleted
     */
    public void deactivate(long graceDeadlineTick) {
        this.state = VillageState.INACTIVE;
        this.graceDeadlineTick = graceDeadlineTick;
    }

    // --- Beds ---------------------------------------------------------------

    public List<BlockPos> getRecognizedBeds() {
        return recognizedBeds;
    }

    public void setRecognizedBeds(List<BlockPos> beds) {
        recognizedBeds.clear();
        for (BlockPos bed : beds) {
            recognizedBeds.add(bed.immutable());
        }
    }

    /** @return number of beds recognized in this village (design: {@code bedCount}). */
    public int getBedCount() {
        return recognizedBeds.size();
    }

    // --- Members ------------------------------------------------------------

    public List<UUID> getMembers() {
        return members;
    }

    /** @return current population (design: {@code population}). */
    public int getPopulation() {
        return members.size();
    }

    /** @return free beds = bedCount - population (design: {@code surplusBeds}). */
    public int getSurplusBeds() {
        return getBedCount() - getPopulation();
    }

    // --- Job counts / storage ----------------------------------------------

    public Map<Job, Integer> getJobCounts() {
        return jobCounts;
    }

    public Map<ResourceType, Integer> getStorage() {
        return storage;
    }

    // --- NBT ----------------------------------------------------------------

    /** Serializes this village into a fresh {@link CompoundTag}. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("CorePos", NbtUtils.writeBlockPos(corePos));
        tag.putString("State", state.name());
        tag.putLong("GraceDeadlineTick", graceDeadlineTick);

        ListTag bedList = new ListTag();
        for (BlockPos bed : recognizedBeds) {
            bedList.add(NbtUtils.writeBlockPos(bed));
        }
        tag.put("RecognizedBeds", bedList);

        ListTag memberList = new ListTag();
        for (UUID member : members) {
            memberList.add(NbtUtils.createUUID(member));
        }
        tag.put("Members", memberList);

        CompoundTag jobs = new CompoundTag();
        for (Map.Entry<Job, Integer> e : jobCounts.entrySet()) {
            jobs.putInt(e.getKey().getSerializedName(), e.getValue());
        }
        tag.put("JobCounts", jobs);

        CompoundTag store = new CompoundTag();
        for (Map.Entry<ResourceType, Integer> e : storage.entrySet()) {
            store.putInt(e.getKey().getSerializedName(), e.getValue());
        }
        tag.put("Storage", store);

        return tag;
    }

    /** Deserializes a village previously written by {@link #save()}. */
    public static Village load(CompoundTag tag) {
        Village village = new Village();
        village.corePos = NbtUtils.readBlockPos(tag.getCompound("CorePos"));
        village.state = parseState(tag.getString("State"));
        village.graceDeadlineTick = tag.getLong("GraceDeadlineTick");

        ListTag bedList = tag.getList("RecognizedBeds", Tag.TAG_COMPOUND);
        for (int i = 0; i < bedList.size(); i++) {
            village.recognizedBeds.add(NbtUtils.readBlockPos(bedList.getCompound(i)));
        }

        ListTag memberList = tag.getList("Members", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < memberList.size(); i++) {
            village.members.add(NbtUtils.loadUUID(memberList.get(i)));
        }

        CompoundTag jobs = tag.getCompound("JobCounts");
        for (String key : jobs.getAllKeys()) {
            Job job = Job.byName(key);
            if (job != null) {
                village.jobCounts.put(job, jobs.getInt(key));
            }
        }

        CompoundTag store = tag.getCompound("Storage");
        for (String key : store.getAllKeys()) {
            ResourceType type = ResourceType.byName(key);
            if (type != null) {
                village.storage.put(type, store.getInt(key));
            }
        }

        return village;
    }

    private static VillageState parseState(String name) {
        try {
            return VillageState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return VillageState.ACTIVE;
        }
    }
}
