package com.yourname.smartvillager.data;

import net.minecraft.util.StringRepresentable;

/**
 * The seven Smart Villager jobs.
 *
 * <p>When exactly seven villagers gather inside a village for the first time, one of each job is
 * assigned. Later individuals (extra summons or offspring) are assigned via
 * {@code Village.getShortageJob()} to whichever job is most in demand.</p>
 *
 * <p>See design document section 3.</p>
 */
public enum Job implements StringRepresentable {

    /** Farmer — harvests and replants crops using the vanilla farming algorithm. */
    FARMER("farmer"),

    /** Carpenter — chops nearby trees, replants saplings, supplies logs as WOOD. */
    CARPENTER("carpenter"),

    /** Miner — mines stone/coal/ores and auto-smelts them. */
    MINER("miner"),

    /** Hunter — hunts sheep (WOOL) and cows/chickens (FOOD). Other animals are ignored. */
    HUNTER("hunter"),

    /** Manufacturer — crafts weapons/armor/tools from carpenter wood and miner ores. */
    MANUFACTURER("manufacturer"),

    /** Architect — builds houses from schematics. */
    ARCHITECT("architect"),

    /** Manager — calculates village demand at the evening gathering and assigns tasks. */
    MANAGER("manager");

    /** Immutable snapshot of {@link #values()} to avoid defensive array copies on hot paths. */
    public static final Job[] VALUES = values();

    private final String name;

    Job(String name) {
        this.name = name;
    }

    /** @return the lowercase serialization/registry key for this job. */
    @Override
    public String getSerializedName() {
        return this.name;
    }

    /**
     * Resolves a {@link Job} from its serialized name.
     *
     * @param name the serialized name (see {@link #getSerializedName()})
     * @return the matching job, or {@code null} if none matches
     */
    public static Job byName(String name) {
        for (Job job : VALUES) {
            if (job.name.equals(name)) {
                return job;
            }
        }
        return null;
    }
}
