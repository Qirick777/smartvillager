package com.yourname.smartvillager.task;

import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;

/**
 * Kinds of work in a village's demand graph (design v3 section 1-1).
 *
 * <p>Each type knows its responsible job, whether it can be split across several workers of that
 * job (gather tasks), and whether it is a craft/build task (atomic; craft tasks are subject to the
 * manufacturer's daily budget).</p>
 */
public enum TaskType {
    GATHER_WOOD(Job.CARPENTER, true, false, false),
    GATHER_STONE(Job.MINER, true, false, false),
    GATHER_ORE(Job.MINER, true, false, false),
    GATHER_FOOD_FARM(Job.FARMER, true, false, false),
    GATHER_FOOD_HUNT(Job.HUNTER, true, false, false),
    GATHER_WOOL(Job.HUNTER, true, false, false),
    CRAFT_TOOL(Job.MANUFACTURER, false, true, false),
    CRAFT_WEAPON(Job.MANUFACTURER, false, true, false),
    BUILD_HOUSE(Job.ARCHITECT, false, false, true),
    BUILD_FARM(Job.ARCHITECT, false, false, true),
    DELIVER_TOOL(null, false, false, false),

    /** Legacy generic food-gather kept for the pre-v3 minimal demand path. */
    GATHER_FOOD(null, true, false, false);

    private final Job assigneeJob;
    private final boolean divisible;
    private final boolean craft;
    private final boolean build;

    TaskType(Job assigneeJob, boolean divisible, boolean craft, boolean build) {
        this.assigneeJob = assigneeJob;
        this.divisible = divisible;
        this.craft = craft;
        this.build = build;
    }

    public Job assigneeJob() {
        return assigneeJob;
    }

    public boolean divisible() {
        return divisible;
    }

    public boolean isCraft() {
        return craft;
    }

    public boolean isBuild() {
        return build;
    }

    /** Which gather task produces a given resource (design v3 section 4, {@code gatherTaskFor}). */
    public static TaskType gatherTaskFor(ResourceType resource) {
        return switch (resource) {
            case WOOD -> GATHER_WOOD;
            case STONE -> GATHER_STONE;
            case INGOT, RAW_ORE, COAL -> GATHER_ORE;
            case WOOL -> GATHER_WOOL;
            case FOOD -> GATHER_FOOD_HUNT;
        };
    }
}
