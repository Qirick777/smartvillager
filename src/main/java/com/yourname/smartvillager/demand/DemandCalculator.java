package com.yourname.smartvillager.demand;

import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.task.TaskType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a village's demand graph from a {@link DemandContext} (design v3 sections 2–5).
 *
 * <p>This class is deliberately Minecraft-independent so it can be unit-tested. It collects root
 * demands (tools, food, stockpile), recursively expands each into material/tool prerequisites
 * (creating child gather/craft tasks and merging duplicates by {@code dedupKey}), and propagates
 * priority so a prerequisite is never less urgent than the task that needs it.</p>
 */
public class DemandCalculator {

    public static final int MEALS_PER_DAY = 3;
    public static final int FOOD_BUFFER_DAYS = 2;
    public static final int MAX_DEPTH = 10;

    // Stockpile caps in natural units (logs / stone blocks). Balancing-adjustable; kept modest so
    // the quota is observable in testing rather than requiring dozens of trees.
    public static int woodCap(int pop) {
        return 16 + pop * 2;
    }

    public static int stoneCap(int pop) {
        return 16 + pop * 2;
    }

    private final DemandContext ctx;
    private final Map<UUID, DemandTask> graph = new LinkedHashMap<>();
    private final Map<String, DemandTask> byKey = new HashMap<>();
    private final Map<ResourceType, Integer> reserved = new HashMap<>();
    private final Set<UUID> expanded = new HashSet<>();

    public DemandCalculator(DemandContext ctx) {
        this.ctx = ctx;
    }

    /** Runs a full recalculation and returns the resulting task graph. */
    public Map<UUID, DemandTask> recalculate() {
        graph.clear();
        byKey.clear();
        reserved.clear();
        expanded.clear();

        List<DemandTask> roots = collectRootDemands();
        for (DemandTask root : roots) {
            expand(root, 0);
        }
        propagatePriority();
        return graph;
    }

    // --- available / reserve -----------------------------------------------

    private int available(ResourceType type) {
        return ctx.storage(type) - reserved.getOrDefault(type, 0);
    }

    private void reserve(ResourceType type, int amount) {
        reserved.merge(type, amount, Integer::sum);
    }

    // --- node registry -----------------------------------------------------

    private void register(DemandTask task) {
        graph.put(task.id, task);
        byKey.put(task.dedupKey, task);
    }

    private DemandTask getOrCreateGather(TaskType type, ResourceType resource, double baseIfNew) {
        String key = type.name() + ":" + resource.name();
        DemandTask task = byKey.get(key);
        if (task == null) {
            task = DemandTask.gather(type, resource);
            task.basePriority = baseIfNew;
            register(task);
        }
        return task;
    }

    private DemandTask getOrCreateCraftTool(Job job, int tier, double baseIfNew) {
        String key = TaskType.CRAFT_TOOL.name() + ":" + job.name() + ":" + tier;
        DemandTask task = byKey.get(key);
        if (task == null) {
            task = DemandTask.craftTool(job, tier);
            task.basePriority = baseIfNew;
            register(task);
        }
        return task;
    }

    private void link(DemandTask parent, DemandTask child) {
        if (!parent.childIds.contains(child.id)) {
            parent.childIds.add(child.id);
        }
        if (!child.parentIds.contains(parent.id)) {
            child.parentIds.add(parent.id);
        }
    }

    // --- step 1: root demands ----------------------------------------------

    private List<DemandTask> collectRootDemands() {
        List<DemandTask> roots = new ArrayList<>();

        // R1: essential tools. For now: the miner's pickaxe (blocks stone/ore gathering).
        if (ctx.toolTier(Job.MINER) == 0 || ctx.toolBroken(Job.MINER)) {
            roots.add(getOrCreateCraftTool(Job.MINER, 1, Priorities.P2_TOOL_MISSING));
        }

        // R2: food demand (2-day buffer). Farmland present -> farmers do 70%, else hunters do all.
        int pop = ctx.population();
        if (pop > 0) {
            int target = pop * MEALS_PER_DAY * FOOD_BUFFER_DAYS;
            int shortage = target - available(ResourceType.FOOD);
            if (shortage > 0) {
                double base = Priorities.P3_FOOD + ((double) shortage / target) * 1000.0;
                int farmShare = ctx.farmlandCount() > 0 ? (int) (shortage * 0.7) : 0;
                if (farmShare > 0) {
                    DemandTask farm = getOrCreateGather(TaskType.GATHER_FOOD_FARM, ResourceType.FOOD, base);
                    farm.amountRequired += farmShare;
                    roots.add(farm);
                }
                int huntShare = shortage - farmShare;
                if (huntShare > 0) {
                    DemandTask hunt = getOrCreateGather(TaskType.GATHER_FOOD_HUNT, ResourceType.FOOD, base);
                    hunt.amountRequired += huntShare;
                    roots.add(hunt);
                }
            }
        }

        // R7: baseline stockpiles (idle prevention).
        addStockpile(roots, ResourceType.WOOD, woodCap(pop));
        addStockpile(roots, ResourceType.STONE, stoneCap(pop));

        return roots;
    }

    private void addStockpile(List<DemandTask> roots, ResourceType resource, int cap) {
        int toCap = cap - available(resource);
        if (toCap > 0) {
            DemandTask gather = getOrCreateGather(
                    TaskType.gatherTaskFor(resource), resource, Priorities.P7_STOCKPILE);
            gather.amountRequired += toCap;
            roots.add(gather);
        }
    }

    // --- step 2: recursive expansion ---------------------------------------

    private void expand(DemandTask task, int depth) {
        if (depth > MAX_DEPTH) {
            task.state = TaskState.EXPIRED;
            return;
        }
        if (!expanded.add(task.id)) {
            return; // already expanded (reused via dedup)
        }

        // (a) mandatory tool requirement. Rule A: the required tier is always lower than what this
        // task would produce, so the recursion terminates (barehanded wood gathering is terminal).
        int minTier = mandatoryPickaxeTier(task);
        if (minTier > 0 && ctx.toolTier(Job.MINER) < minTier) {
            DemandTask craft = getOrCreateCraftTool(Job.MINER, minTier, Priorities.P2_TOOL_MISSING);
            link(task, craft);
            expand(craft, depth + 1);
        }

        // (b) material requirements (craft/build tasks). Missing amounts spawn gather children.
        for (Map.Entry<ResourceType, Integer> ingredient : recipeFor(task).entrySet()) {
            ResourceType resource = ingredient.getKey();
            int amount = ingredient.getValue();
            int use = Math.min(available(resource), amount);
            reserve(resource, use);
            int missing = amount - use;
            if (missing > 0) {
                DemandTask gather = getOrCreateGather(
                        TaskType.gatherTaskFor(resource), resource, Priorities.P7_STOCKPILE);
                gather.amountRequired += missing;
                link(task, gather);
                expand(gather, depth + 1);
            }
        }

        task.state = task.childIds.isEmpty() ? TaskState.READY : TaskState.BLOCKED;
    }

    /** @return the minimum pickaxe tier a gather task mandates, or 0 if none/optional. */
    private static int mandatoryPickaxeTier(DemandTask task) {
        return switch (task.type) {
            case GATHER_STONE -> 1; // any pickaxe
            case GATHER_ORE -> 2;   // stone+ pickaxe to mine iron
            default -> 0;           // wood (barehand) / food / wool are optional-tool
        };
    }

    /**
     * Abstract vanilla-style recipe costs for a craft task, in natural units (logs / blocks /
     * ingots). {@link DemandContext#storage} is expected to report the same natural units.
     */
    private static Map<ResourceType, Integer> recipeFor(DemandTask task) {
        if (task.type != TaskType.CRAFT_TOOL) {
            return Map.of();
        }
        return switch (task.targetTier) {
            case 1 -> Map.of(ResourceType.WOOD, 1);
            case 2 -> Map.of(ResourceType.WOOD, 1, ResourceType.STONE, 3);
            case 3 -> Map.of(ResourceType.WOOD, 1, ResourceType.INGOT, 3);
            default -> Map.of();
        };
    }

    // --- step 4: priority propagation (child >= parent + 1) ----------------

    private void propagatePriority() {
        for (DemandTask task : graph.values()) {
            task.effectivePriority = task.basePriority;
        }
        for (int iteration = 0; iteration <= graph.size(); iteration++) {
            boolean changed = false;
            for (DemandTask parent : graph.values()) {
                for (UUID childId : parent.childIds) {
                    DemandTask child = graph.get(childId);
                    double raised = Math.max(child.effectivePriority, parent.effectivePriority + 1);
                    if (raised > child.effectivePriority) {
                        child.effectivePriority = raised;
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
    }
}
