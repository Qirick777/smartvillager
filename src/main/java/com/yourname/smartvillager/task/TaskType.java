package com.yourname.smartvillager.task;

/**
 * Kinds of work the manager can queue in a village's demand queue (design document section 8).
 * Only {@link #GATHER_FOOD} is produced by the Phase 4 minimal demand calculation; the rest are
 * placeholders for later phases.
 */
public enum TaskType {
    /** Food is short — assign farmers/hunters to produce more. */
    GATHER_FOOD,
    /** Wood is short — assign carpenters. (Phase 8) */
    GATHER_WOOD,
    /** Stone is short — assign miners. (Phase 8) */
    GATHER_STONE,
    /** A tool needs (re)making — assign the manufacturer. (Phase 8) */
    CRAFT_TOOL,
    /** A new house is needed — assign the architect. (Phase 8) */
    BUILD_HOUSE
}
