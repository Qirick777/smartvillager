package com.yourname.smartvillager.village;

import com.yourname.smartvillager.SmartVillagerMod;
import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.demand.DemandCalculator;
import com.yourname.smartvillager.entity.SmartVillager;
import com.yourname.smartvillager.task.TaskType;
import com.yourname.smartvillager.task.VillagerTask;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The per-dimension {@link SavedData} that owns every {@link Village} in a level, keyed by core
 * position. Persisted to {@code data/smartvillager_villages.dat}.
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Create / reactivate a village when a Village Core Block is placed.</li>
 *   <li>Put a village into its grace period when its core is destroyed.</li>
 *   <li>Tick grace timers: damage members of inactive villages and delete villages whose grace has
 *       expired (design document section 5, "코어 파괴 시 처리").</li>
 * </ul>
 */
public class VillageManager extends SavedData {

    /** SavedData file name (becomes {@code data/smartvillager_villages.dat}). */
    private static final String DATA_NAME = SmartVillagerMod.MOD_ID + "_villages";

    // TODO (balancing): the design proposes a 48000-tick (2 in-game day) grace period. Kept short
    // here so the destroy -> grace -> death/deletion flow can be observed quickly while testing.
    /** Grace period length in ticks (temporary test value: 600t = 30s). */
    public static final int GRACE_TICKS = 600;
    /** How often an inactive village damages its members, in ticks. */
    public static final int DAMAGE_INTERVAL_TICKS = 20;
    /** Damage dealt to each member per interval while inactive. */
    public static final float DAMAGE_PER_INTERVAL = 1.0F;

    private final Map<BlockPos, Village> villages = new HashMap<>();

    public VillageManager() {
    }

    /** @return the village manager for the given level, creating/loading it on first access. */
    public static VillageManager get(ServerLevel level) {
        // 1.20.1 signature: computeIfAbsent(loadFunction, factorySupplier, name).
        return level.getDataStorage().computeIfAbsent(
                VillageManager::load, VillageManager::new, DATA_NAME);
    }

    public Collection<Village> getVillages() {
        return villages.values();
    }

    public Village getVillageAtCore(BlockPos corePos) {
        return villages.get(corePos.immutable());
    }

    /** Fixed radius (blocks) around an active core within which smart villagers may be summoned. */
    public static final int SPAWN_RANGE = 64;

    /** @return {@code true} if {@code pos} is within {@link #SPAWN_RANGE} of any active village core. */
    public boolean isWithinSpawnRange(BlockPos pos) {
        double rangeSq = (double) SPAWN_RANGE * SPAWN_RANGE;
        for (Village village : villages.values()) {
            if (village.isActive() && village.getCorePos().distSqr(pos) <= rangeSq) {
                return true;
            }
        }
        return false;
    }

    /** @return the active village whose core is nearest to {@code pos}, or {@code null} if none. */
    public Village findNearestActiveVillage(BlockPos pos) {
        Village nearest = null;
        double bestSq = Double.MAX_VALUE;
        for (Village village : villages.values()) {
            if (!village.isActive()) {
                continue;
            }
            double d = village.getCorePos().distSqr(pos);
            if (d < bestSq) {
                bestSq = d;
                nearest = village;
            }
        }
        return nearest;
    }

    // --- Membership / job assignment ---------------------------------------

    /**
     * Attaches a villager to the nearest active village within {@link #SPAWN_RANGE} and assigns a
     * job (design section 3): fewer than 7 members → left unassigned; the 7th triggers the one-time
     * batch assignment of all seven jobs; later members get {@link Village#getShortageJob()}.
     * No-op if the villager already belongs to a village or none is in range.
     */
    public void tryJoinAndAssign(ServerLevel level, SmartVillager villager) {
        if (villager.getVillageCorePos() != null) {
            return;
        }
        Village village = findNearestActiveVillage(villager.blockPosition());
        if (village == null
                || village.getCorePos().distSqr(villager.blockPosition()) > (double) SPAWN_RANGE * SPAWN_RANGE) {
            return;
        }
        if (!village.addMember(villager.getUUID())) {
            return;
        }
        villager.setVillageCorePos(village.getCorePos());

        if (!village.isInitialJobsAssigned()) {
            if (village.getPopulation() >= Village.INITIAL_JOB_COUNT) {
                assignInitialJobs(level, village);
            }
            // Fewer than 7 gathered so far: leave this villager unassigned for now.
        } else {
            Job job = village.getShortageJob();
            villager.setJob(job);
            village.incrementJobCount(job);
            SmartVillagerMod.LOGGER.info("Assigned shortage job {} to villager {} (village {})",
                    job, villager.getUUID(), village.getCorePos());
        }
        setDirty();
    }

    /** Assigns one of each of the seven jobs to the village's (first seven) members. */
    private void assignInitialJobs(ServerLevel level, Village village) {
        List<UUID> members = village.getMembers();
        int assigned = 0;
        for (int i = 0; i < members.size() && assigned < Job.VALUES.length; i++) {
            if (level.getEntity(members.get(i)) instanceof SmartVillager member) {
                member.setJob(Job.VALUES[assigned]);
                assigned++;
            }
        }
        village.setInitialJobsAssigned(true);
        village.resetJobCountsToOneEach();
        SmartVillagerMod.LOGGER.info("Initial job assignment complete for village {} ({} jobs)",
                village.getCorePos(), assigned);
        // Give the fresh workers an initial demand plan so they start working immediately.
        recalculateDemand(village);
    }

    // --- Demand calculation (manager, design section 8) --------------------

    /** Per-capita daily food requirement used by the food shortage calculation. */
    public static final int FOOD_PER_CAPITA = 3;

    /** Recomputes demand and (re)assigns each villager a task (evening gathering). */
    public void recalculateDemandAll(ServerLevel level) {
        for (Village village : villages.values()) {
            if (village.isActive()) {
                recalculateDemand(village);
                assignTasks(level, village);
            }
        }
    }

    /** The manager hands each member its task for the day from the fresh demand graph. */
    private void assignTasks(ServerLevel level, Village village) {
        for (UUID memberId : village.getMembers()) {
            if (level.getEntity(memberId) instanceof SmartVillager member) {
                var task = member.assignFromManager(level);
                SmartVillagerMod.LOGGER.info("Manager assigned {} ({}) -> {}",
                        memberId, member.getJob(), task);
            }
        }
    }

    /**
     * Minimal demand calculation (Phase 4.2): the FOOD item only. Shortage rate is
     * {@code (need - have) / need}; a positive rate queues a {@link TaskType#GATHER_FOOD} task for
     * farmers/hunters. Later phases add equipment, building materials, and breeding demands, then
     * sort the queue by shortage rate.
     */
    public void recalculateDemand(Village village) {
        // v3 demand graph (dependency DAG). Recomputed each evening.
        village.setTaskGraph(new DemandCalculator(village).recalculate());
        SmartVillagerMod.LOGGER.info("Village {} demand graph: {} tasks",
                village.getCorePos().toShortString(), village.getTaskGraph().size());

        // Legacy minimal food demandQueue (still drives the manufacturer's material requests).
        List<VillagerTask> queue = village.getDemandQueue();
        queue.clear();

        int population = village.getPopulation();
        if (population <= 0) {
            return;
        }

        int need = population * FOOD_PER_CAPITA;
        int have = village.getStorage(ResourceType.FOOD);
        double shortage = (double) (need - have) / need;
        if (shortage > 0.0) {
            queue.add(new VillagerTask(TaskType.GATHER_FOOD, shortage));
            SmartVillagerMod.LOGGER.info(
                    "Village {} demand: GATHER_FOOD shortage={} (need={}, have={}) -> FARMER/HUNTER",
                    village.getCorePos().toShortString(), String.format("%.2f", shortage), need, have);
        }
        // TODO (Phase 8): equipment (CRAFT_TOOL), building materials (WOOD/STONE/WOOL),
        // breeding -> BUILD_HOUSE (always top priority); then sort queue by shortage desc.
    }

    /** Removes a villager from its village (on death/discard), keeping job counts in sync. */
    public void onMemberRemoved(BlockPos corePos, UUID id, Job job) {
        Village village = villages.get(corePos.immutable());
        if (village == null) {
            return;
        }
        if (village.removeMember(id) && job != null) {
            village.decrementJobCount(job);
        }
        village.releaseBedsOf(id);
        setDirty();
    }

    // --- Core placement / removal ------------------------------------------

    /**
     * Called when a Village Core Block is placed. Reactivates an inactive village that still exists
     * at this position (grace recovery), otherwise creates a new village.
     *
     * @return the created or reactivated village
     */
    public Village createVillage(BlockPos corePos) {
        BlockPos key = corePos.immutable();
        Village existing = villages.get(key);
        if (existing != null) {
            existing.activate();
            SmartVillagerMod.LOGGER.info("Reactivated village at core {} (grace recovery)", key);
            setDirty();
            return existing;
        }
        Village village = new Village(key);
        villages.put(key, village);
        SmartVillagerMod.LOGGER.info("Created village at core {}", key);
        setDirty();
        return village;
    }

    /**
     * Called when a Village Core Block is destroyed. Puts the village at that position into its
     * grace period (design section 5). No-op if no village is registered at that core.
     */
    public void onCoreRemoved(ServerLevel level, BlockPos corePos) {
        Village village = villages.get(corePos.immutable());
        if (village == null || !village.isActive()) {
            return;
        }
        long deadline = level.getGameTime() + GRACE_TICKS;
        village.deactivate(deadline);
        SmartVillagerMod.LOGGER.info(
                "Core destroyed at {}; village INACTIVE, grace ends at tick {}", corePos, deadline);
        setDirty();
    }

    // --- Ticking ------------------------------------------------------------

    /**
     * Advances grace timers for this level's villages. Inactive villages periodically damage their
     * members and are deleted once their grace deadline passes.
     */
    public void tick(ServerLevel level) {
        long now = level.getGameTime();
        List<BlockPos> expired = null;

        for (Village village : villages.values()) {
            if (village.isActive()) {
                continue;
            }
            if (now >= village.getGraceDeadlineTick()) {
                if (expired == null) {
                    expired = new ArrayList<>();
                }
                expired.add(village.getCorePos());
            } else if (now % DAMAGE_INTERVAL_TICKS == 0) {
                damageMembers(level, village);
            }
        }

        if (expired != null) {
            for (BlockPos corePos : expired) {
                villages.remove(corePos);
                SmartVillagerMod.LOGGER.info("Village at {} deleted (grace expired)", corePos);
            }
            setDirty();
        }
    }

    /**
     * Scans the world around a village's core for beds, runs the BFS recognition
     * ({@link VillageBedScanner}), stores the result on the village, and returns it.
     *
     * <p>This is a debug/verification helper (invoked from the {@code /smartvillager rescan}
     * command): it does a bounded block scan rather than maintaining an incremental bed index.</p>
     */
    public List<BlockPos> recomputeBeds(ServerLevel level, Village village) {
        BlockPos core = village.getCorePos();
        // Search box: horizontal reach = core radius + one expansion hop; limited vertical band.
        int reach = Village.CORE_RECOGNITION_RADIUS + Village.BED_EXPANSION_RADIUS;
        int vertical = 24;

        List<BlockPos> candidateBeds = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -vertical; dy <= vertical; dy++) {
                    cursor.set(core.getX() + dx, core.getY() + dy, core.getZ() + dz);
                    BlockState state = level.getBlockState(cursor);
                    // Count each bed once, by its head half only.
                    if (state.getBlock() instanceof BedBlock
                            && state.getValue(BedBlock.PART) == BedPart.HEAD) {
                        candidateBeds.add(cursor.immutable());
                    }
                }
            }
        }

        List<BlockPos> recognized = VillageBedScanner.computeRecognizedBeds(
                core, Village.CORE_RECOGNITION_RADIUS, Village.BED_EXPANSION_RADIUS, candidateBeds);
        village.setRecognizedBeds(recognized);
        setDirty();
        return recognized;
    }

    private void damageMembers(ServerLevel level, Village village) {
        for (UUID memberId : village.getMembers()) {
            Entity entity = level.getEntity(memberId);
            if (entity instanceof LivingEntity living) {
                living.hurt(level.damageSources().magic(), DAMAGE_PER_INTERVAL);
            }
        }
    }

    // --- NBT ----------------------------------------------------------------

    public static VillageManager load(CompoundTag tag) {
        VillageManager manager = new VillageManager();
        ListTag list = tag.getList("Villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Village village = Village.load(list.getCompound(i));
            manager.villages.put(village.getCorePos(), village);
        }
        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Village village : villages.values()) {
            list.add(village.save());
        }
        tag.put("Villages", list);
        return tag;
    }
}
