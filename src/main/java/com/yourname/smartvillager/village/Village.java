package com.yourname.smartvillager.village;

import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.data.ToolTier;
import com.yourname.smartvillager.demand.DemandContext;
import com.yourname.smartvillager.demand.DemandTask;
import com.yourname.smartvillager.task.VillagerTask;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
public class Village implements DemandContext {

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
    /** Crafted tool stock per tier (design section 10). */
    private final Map<ToolTier, Integer> toolStock = new EnumMap<>(ToolTier.class);
    /** Which bed (head position) each villager has claimed as its own. */
    private final Map<BlockPos, UUID> claimedBeds = new HashMap<>();

    /**
     * Game-time tick at which the grace period ends while {@link VillageState#INACTIVE}.
     * Meaningless (and not consulted) while {@link VillageState#ACTIVE}.
     */
    private long graceDeadlineTick;

    /** Number of members that triggers the one-time initial job assignment (design section 3). */
    public static final int INITIAL_JOB_COUNT = 7;

    /** Whether the one-time "7 gather -> assign all 7 jobs" batch assignment has happened. */
    private boolean initialJobsAssigned;

    /**
     * Current demand tasks, recomputed each evening by the manager (design section 8). Transient:
     * regenerated every evening, so it is not persisted to NBT.
     */
    private final List<VillagerTask> demandQueue = new ArrayList<>();

    /** v3 demand graph, recomputed each evening by {@code DemandCalculator}. Transient for now. */
    private Map<UUID, DemandTask> taskGraph = new LinkedHashMap<>();
    /** Recognized farmland tiles in the village (design v3). Not yet populated. */
    private int farmlandCount;

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

    /** Adds a member if not already present. @return true if newly added. */
    public boolean addMember(UUID id) {
        if (members.contains(id)) {
            return false;
        }
        members.add(id);
        return true;
    }

    /** Removes a member. @return true if it was present. */
    public boolean removeMember(UUID id) {
        return members.remove(id);
    }

    /** @return current population (design: {@code population}). */
    public int getPopulation() {
        return members.size();
    }

    /** @return free beds = bedCount - population (design: {@code surplusBeds}). */
    public int getSurplusBeds() {
        return getBedCount() - getPopulation();
    }

    // --- Jobs --------------------------------------------------------------

    public boolean isInitialJobsAssigned() {
        return initialJobsAssigned;
    }

    public void setInitialJobsAssigned(boolean assigned) {
        this.initialJobsAssigned = assigned;
    }

    public Map<Job, Integer> getJobCounts() {
        return jobCounts;
    }

    public int getJobCount(Job job) {
        return jobCounts.getOrDefault(job, 0);
    }

    public void incrementJobCount(Job job) {
        jobCounts.merge(job, 1, Integer::sum);
    }

    public void decrementJobCount(Job job) {
        int next = getJobCount(job) - 1;
        if (next <= 0) {
            jobCounts.remove(job);
        } else {
            jobCounts.put(job, next);
        }
    }

    /** Resets the job tally to exactly one of each job (used by the initial batch assignment). */
    public void resetJobCountsToOneEach() {
        jobCounts.clear();
        for (Job job : Job.VALUES) {
            jobCounts.put(job, 1);
        }
    }

    /**
     * Picks the job to assign to a newly added member once the village is past its initial
     * assignment (design section 8, {@code getShortageJob()}).
     *
     * <p>TODO (Phase 8): replace this headcount-only stub with the demand-weighted version that
     * also factors in the recent {@code demandQueue} task history.</p>
     *
     * @return the job with the current lowest headcount (ties broken by enum order)
     */
    public Job getShortageJob() {
        Job shortage = Job.VALUES[0];
        int best = Integer.MAX_VALUE;
        for (Job job : Job.VALUES) {
            int count = getJobCount(job);
            if (count < best) {
                best = count;
                shortage = job;
            }
        }
        return shortage;
    }

    // --- Storage -----------------------------------------------------------

    public Map<ResourceType, Integer> getStorage() {
        return storage;
    }

    public int getStorage(ResourceType type) {
        return storage.getOrDefault(type, 0);
    }

    /** Adds (or subtracts, if negative) an amount to a resource, clamped at zero. */
    public void addStorage(ResourceType type, int amount) {
        int next = Math.max(0, getStorage(type) + amount);
        if (next == 0) {
            storage.remove(type);
        } else {
            storage.put(type, next);
        }
    }

    /**
     * Milli-coal consumed to smelt one ore. Design section 9: smelting one ore uses 0.125 coal,
     * i.e. {@code 0.125 * }{@link ResourceType#MILLI_UNIT}{@code  = 125} milli-coal — kept as an
     * integer so there is no floating-point drift.
     */
    public static final int COAL_PER_SMELT_MILLI = 125;

    /**
     * Smelts one RAW_ORE into one INGOT if enough milli-coal is available, using integer math only.
     *
     * @return {@code true} if a smelt happened
     */
    public boolean trySmeltOre() {
        if (getStorage(ResourceType.RAW_ORE) >= 1 && getStorage(ResourceType.COAL) >= COAL_PER_SMELT_MILLI) {
            addStorage(ResourceType.RAW_ORE, -1);
            addStorage(ResourceType.COAL, -COAL_PER_SMELT_MILLI);
            addStorage(ResourceType.INGOT, 1);
            return true;
        }
        return false;
    }

    // --- Tools -------------------------------------------------------------

    public Map<ToolTier, Integer> getToolStock() {
        return toolStock;
    }

    public int getToolStock(ToolTier tier) {
        return toolStock.getOrDefault(tier, 0);
    }

    public void addTool(ToolTier tier, int amount) {
        int next = Math.max(0, getToolStock(tier) + amount);
        if (next == 0) {
            toolStock.remove(tier);
        } else {
            toolStock.put(tier, next);
        }
    }

    // --- Bed ownership -----------------------------------------------------

    public Map<BlockPos, UUID> getClaimedBeds() {
        return claimedBeds;
    }

    public boolean isBedClaimed(BlockPos bedHead) {
        return claimedBeds.containsKey(bedHead);
    }

    /** Claims a bed for an owner if it isn't already taken. @return true if newly claimed. */
    public boolean claimBed(BlockPos bedHead, UUID owner) {
        if (claimedBeds.containsKey(bedHead)) {
            return false;
        }
        claimedBeds.put(bedHead.immutable(), owner);
        return true;
    }

    /** Releases all beds owned by the given villager (e.g. on death). */
    public void releaseBedsOf(UUID owner) {
        claimedBeds.values().removeIf(owner::equals);
    }

    // --- Demand queue (transient) ------------------------------------------

    public List<VillagerTask> getDemandQueue() {
        return demandQueue;
    }

    // --- Demand graph + DemandContext --------------------------------------

    public Map<UUID, DemandTask> getTaskGraph() {
        return taskGraph;
    }

    public void setTaskGraph(Map<UUID, DemandTask> taskGraph) {
        this.taskGraph = taskGraph;
    }

    public int getFarmlandCount() {
        return farmlandCount;
    }

    public void setFarmlandCount(int farmlandCount) {
        this.farmlandCount = farmlandCount;
    }

    @Override
    public int population() {
        return members.size();
    }

    @Override
    public int storage(ResourceType type) {
        return getStorage(type);
    }

    @Override
    public int farmlandCount() {
        return farmlandCount;
    }

    @Override
    public int toolTier(Job job) {
        return 0; // TODO (S3-B): back this with a per-job tool registry.
    }

    @Override
    public boolean toolBroken(Job job) {
        return false; // TODO (S3-B): track broken tools.
    }

    // --- NBT ----------------------------------------------------------------

    /** Serializes this village into a fresh {@link CompoundTag}. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("CorePos", NbtUtils.writeBlockPos(corePos));
        tag.putString("State", state.name());
        tag.putLong("GraceDeadlineTick", graceDeadlineTick);
        tag.putBoolean("InitialJobsAssigned", initialJobsAssigned);

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

        CompoundTag tools = new CompoundTag();
        for (Map.Entry<ToolTier, Integer> e : toolStock.entrySet()) {
            tools.putInt(e.getKey().getSerializedName(), e.getValue());
        }
        tag.put("Tools", tools);

        ListTag beds = new ListTag();
        for (Map.Entry<BlockPos, UUID> e : claimedBeds.entrySet()) {
            CompoundTag entry = new CompoundTag();
            entry.put("Pos", NbtUtils.writeBlockPos(e.getKey()));
            entry.putUUID("Owner", e.getValue());
            beds.add(entry);
        }
        tag.put("ClaimedBeds", beds);

        return tag;
    }

    /** Deserializes a village previously written by {@link #save()}. */
    public static Village load(CompoundTag tag) {
        Village village = new Village();
        village.corePos = NbtUtils.readBlockPos(tag.getCompound("CorePos"));
        village.state = parseState(tag.getString("State"));
        village.graceDeadlineTick = tag.getLong("GraceDeadlineTick");
        village.initialJobsAssigned = tag.getBoolean("InitialJobsAssigned");

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

        CompoundTag tools = tag.getCompound("Tools");
        for (String key : tools.getAllKeys()) {
            ToolTier tier = ToolTier.byName(key);
            if (tier != null) {
                village.toolStock.put(tier, tools.getInt(key));
            }
        }

        ListTag beds = tag.getList("ClaimedBeds", Tag.TAG_COMPOUND);
        for (int i = 0; i < beds.size(); i++) {
            CompoundTag entry = beds.getCompound(i);
            village.claimedBeds.put(
                    NbtUtils.readBlockPos(entry.getCompound("Pos")), entry.getUUID("Owner"));
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
