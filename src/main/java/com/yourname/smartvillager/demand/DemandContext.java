package com.yourname.smartvillager.demand;

import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;

/**
 * Read-only view of a village that the {@link DemandCalculator} needs. Keeping the calculator
 * behind this interface makes it Minecraft-independent and unit-testable (design v3 section 2).
 */
public interface DemandContext {

    /** Current population (number of villagers). */
    int population();

    /** Raw stored amount of a resource (WOOD/COAL are in milli-units, as elsewhere). */
    int storage(ResourceType type);

    /** Number of recognized farmland tiles in the village. */
    int farmlandCount();

    /** Tool tier held by a job: 0 none, 1 wood, 2 stone, 3 iron. */
    int toolTier(Job job);

    /** Whether a job's tool is currently broken. */
    boolean toolBroken(Job job);
}
