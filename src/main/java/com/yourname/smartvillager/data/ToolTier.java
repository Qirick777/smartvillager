package com.yourname.smartvillager.data;

import net.minecraft.util.StringRepresentable;

/**
 * Tool material tiers the manufacturer can craft (design document section 10).
 *
 * <p>Each tier's head material maps to a stored {@link ResourceType}. The village storage currently
 * tracks metal only as a single generic {@link ResourceType#INGOT}, so only {@link #STONE} and
 * {@link #IRON} exist for now.</p>
 *
 * <p>TODO (Phase balancing): distinguish copper/gold/diamond once storage tracks metal types, and
 * add the tier-priority consumption order.</p>
 */
public enum ToolTier implements StringRepresentable {

    STONE("stone", ResourceType.STONE),
    IRON("iron", ResourceType.INGOT);

    public static final ToolTier[] VALUES = values();

    private final String name;
    private final ResourceType material;

    ToolTier(String name, ResourceType material) {
        this.name = name;
        this.material = material;
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
}
