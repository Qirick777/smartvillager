package com.yourname.smartvillager.data;

import net.minecraft.util.StringRepresentable;

/**
 * Abstract resource types tracked in a village's shared storage.
 *
 * <p>Resources are stored as integer counts in {@code Village.storage}, but some types use a
 * scaled ("milli") unit so fractional amounts can be represented without floating point:</p>
 *
 * <ul>
 *   <li>{@link #WOOD}: planks/stairs/slabs cost 0.25 each, so wood is tracked in milli-units
 *       (1 log = {@value #MILLI_UNIT} milli-wood). See design document section 9.</li>
 *   <li>{@link #COAL}: smelting one ore costs 0.125 coal, so coal is tracked in milli-units
 *       (1 coal = {@value #MILLI_UNIT} milli-coal). See design document section 9.</li>
 * </ul>
 *
 * <p>All other types are plain whole-item counts.</p>
 */
public enum ResourceType implements StringRepresentable {

    /** Wood from the carpenter. Tracked in milli-units (1 log = 1000). */
    WOOD("wood", true),

    /** Stone from the miner. Whole-block count. */
    STONE("stone", false),

    /** Coal from the miner, consumed by smelting. Tracked in milli-units (1 coal = 1000). */
    COAL("coal", true),

    /** Un-smelted ore from the miner (raw iron, raw gold, raw copper, ...). Whole-item count. */
    RAW_ORE("raw_ore", false),

    /** Smelted metal ingots, consumed by the manufacturer. Whole-item count. */
    INGOT("ingot", false),

    /** Wool from the hunter (sheep), used for beds. Whole-item count. */
    WOOL("wool", false),

    /** Food from the farmer and hunter (cows/chickens). Whole-item count. */
    FOOD("food", false);

    /** Scale factor for resources tracked in milli-units (1 whole unit = 1000 milli-units). */
    public static final int MILLI_UNIT = 1000;

    /** Immutable snapshot of {@link #values()} to avoid defensive array copies on hot paths. */
    public static final ResourceType[] VALUES = values();

    private final String name;
    private final boolean milli;

    ResourceType(String name, boolean milli) {
        this.name = name;
        this.milli = milli;
    }

    /** @return the lowercase serialization/storage key for this resource. */
    @Override
    public String getSerializedName() {
        return this.name;
    }

    /**
     * @return {@code true} if this resource is stored in milli-units (see {@link #MILLI_UNIT}),
     *         {@code false} if it is a plain whole-item count.
     */
    public boolean isMilliUnit() {
        return this.milli;
    }

    /**
     * Resolves a {@link ResourceType} from its serialized name.
     *
     * @param name the serialized name (see {@link #getSerializedName()})
     * @return the matching resource, or {@code null} if none matches
     */
    public static ResourceType byName(String name) {
        for (ResourceType type : VALUES) {
            if (type.name.equals(name)) {
                return type;
            }
        }
        return null;
    }
}
