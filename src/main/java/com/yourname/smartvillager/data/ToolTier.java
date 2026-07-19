package com.yourname.smartvillager.data;

import net.minecraft.util.StringRepresentable;

/**
 * Tool material tiers the manufacturer can craft (design document section 10, v3 tier ladder).
 *
 * <p>Each tier has an integer level (1=wood, 2=stone, 3=iron) matching the demand engine, and a
 * head material mapped to a stored {@link ResourceType}. The item that represents each tier's tool
 * lives in the goal code (to keep this enum Minecraft-item-independent).</p>
 */
public enum ToolTier implements StringRepresentable {

    WOOD("wood", 1, ResourceType.WOOD),
    STONE("stone", 2, ResourceType.STONE),
    IRON("iron", 3, ResourceType.INGOT);

    public static final ToolTier[] VALUES = values();

    private final String name;
    private final int level;
    private final ResourceType material;

    ToolTier(String name, int level, ResourceType material) {
        this.name = name;
        this.level = level;
        this.material = material;
    }

    /** @return the tier level (1=wood, 2=stone, 3=iron), as used by the demand engine. */
    public int level() {
        return level;
    }

    /** @return the stored resource consumed for this tier's tool head. */
    public ResourceType getMaterial() {
        return material;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static ToolTier byName(String name) {
        for (ToolTier tier : VALUES) {
            if (tier.name.equals(name)) {
                return tier;
            }
        }
        return null;
    }

    /** @return the tier for a level (1/2/3), or {@code null} if out of range. */
    public static ToolTier byLevel(int level) {
        for (ToolTier tier : VALUES) {
            if (tier.level == level) {
                return tier;
            }
        }
        return null;
    }
}
