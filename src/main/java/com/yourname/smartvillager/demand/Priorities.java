package com.yourname.smartvillager.demand;

/**
 * Category base priorities for demand tasks (design v3 section 5-1). Higher is more urgent.
 */
public final class Priorities {

    /** Survival crisis (food storage 0 + personal foodStock depleted); a promotion target. */
    public static final double P0_SURVIVAL = 10000;
    /** Breeding -> house construction; always top per v2. */
    public static final double P1_BREEDING_HOUSE = 9000;
    /** Essential tool missing/broken (job blocked), e.g. pickaxe. */
    public static final double P2_TOOL_MISSING = 8000;
    /** Food shortage (dynamic: + shortageRate * 1000). */
    public static final double P3_FOOD = 5000;
    /** Active construction materials / hunting weapon (dynamic). */
    public static final double P4_WEAPON = 4000;
    /** Farmland expansion (dynamic: + shortageRate * 500). */
    public static final double P5_FARM = 3000;
    /** Tool tier upgrade (spare-time work). */
    public static final double P6_TOOL_UPGRADE = 2000;
    /** Baseline stockpiling (idle prevention). */
    public static final double P7_STOCKPILE = 1000;

    private Priorities() {
    }
}
