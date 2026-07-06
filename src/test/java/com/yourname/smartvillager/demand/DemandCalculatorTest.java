package com.yourname.smartvillager.demand;

import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.task.TaskType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the demand graph engine (design v3). World-independent: uses a fake context.
 */
class DemandCalculatorTest {

    /** Adjustable fake village context. */
    private static final class FakeContext implements DemandContext {
        int population = 7;
        int farmland = 0;
        final int[] tiers = new int[Job.VALUES.length];
        final Map<ResourceType, Integer> storage = new HashMap<>();

        @Override public int population() { return population; }
        @Override public int storage(ResourceType type) { return storage.getOrDefault(type, 0); }
        @Override public int farmlandCount() { return farmland; }
        @Override public int toolTier(Job job) { return tiers[job.ordinal()]; }
        @Override public boolean toolBroken(Job job) { return false; }
    }

    private static DemandTask byKey(Map<UUID, DemandTask> graph, String key) {
        return graph.values().stream().filter(t -> t.dedupKey.equals(key)).findFirst().orElse(null);
    }

    private static long countType(Map<UUID, DemandTask> graph, TaskType type) {
        return graph.values().stream().filter(t -> t.type == type).count();
    }

    @Test
    void newVillage_buildsPickaxeChainAndFoodDemand() {
        Map<UUID, DemandTask> graph = new DemandCalculator(new FakeContext()).recalculate();

        DemandTask craft = byKey(graph, "CRAFT_TOOL:MINER:1");
        DemandTask wood = byKey(graph, "GATHER_WOOD:WOOD");
        DemandTask stone = byKey(graph, "GATHER_STONE:STONE");

        assertNotNull(craft, "pickaxe craft task");
        assertNotNull(wood, "wood gather task");
        assertNotNull(stone, "stone gather task");
        assertNotNull(byKey(graph, "GATHER_FOOD_HUNT:FOOD"), "hunt food (no farmland)");
        assertNull(byKey(graph, "GATHER_FOOD_FARM:FOOD"), "no farm food when farmland is 0");
    }

    @Test
    void gatherTasksAreDeduplicatedIntoSingleNodes() {
        Map<UUID, DemandTask> graph = new DemandCalculator(new FakeContext()).recalculate();
        assertEquals(1, countType(graph, TaskType.CRAFT_TOOL));
        assertEquals(1, countType(graph, TaskType.GATHER_WOOD));
        assertEquals(1, countType(graph, TaskType.GATHER_STONE));
    }

    @Test
    void dependencyEdgesLinkCraftToMaterialsAndToolToStone() {
        Map<UUID, DemandTask> graph = new DemandCalculator(new FakeContext()).recalculate();
        DemandTask craft = byKey(graph, "CRAFT_TOOL:MINER:1");
        DemandTask wood = byKey(graph, "GATHER_WOOD:WOOD");
        DemandTask stone = byKey(graph, "GATHER_STONE:STONE");

        assertTrue(craft.childIds.contains(wood.id), "craft needs wood");
        assertTrue(stone.childIds.contains(craft.id), "stone gathering needs a pickaxe");
        assertEquals(TaskState.READY, wood.state, "wood is barehand-terminal");
        assertEquals(TaskState.BLOCKED, craft.state, "craft is blocked on wood");
    }

    @Test
    void priorityPropagatesSoPrerequisiteOutranksParent() {
        Map<UUID, DemandTask> graph = new DemandCalculator(new FakeContext()).recalculate();
        DemandTask craft = byKey(graph, "CRAFT_TOOL:MINER:1");
        DemandTask wood = byKey(graph, "GATHER_WOOD:WOOD");

        assertEquals(Priorities.P2_TOOL_MISSING, craft.basePriority);
        assertTrue(wood.effectivePriority >= craft.effectivePriority + 1,
                "the wood a blocked craft needs must be at least as urgent as the craft");
    }

    @Test
    void withPickaxe_stoneGatheringNeedsNoCraftChild() {
        FakeContext ctx = new FakeContext();
        ctx.tiers[Job.MINER.ordinal()] = 1; // already has a wood pickaxe
        Map<UUID, DemandTask> graph = new DemandCalculator(ctx).recalculate();

        assertEquals(0, countType(graph, TaskType.CRAFT_TOOL));
        DemandTask stone = byKey(graph, "GATHER_STONE:STONE");
        assertNotNull(stone);
        assertTrue(stone.childIds.isEmpty());
        assertEquals(TaskState.READY, stone.state);
    }
}
