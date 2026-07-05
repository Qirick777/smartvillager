package com.yourname.smartvillager.village;

/**
 * Lifecycle state of a village (design document section 5, "코어 파괴 시 처리").
 */
public enum VillageState {
    /** Core is present; the village operates normally. */
    ACTIVE,

    /**
     * Core was destroyed. The village is in its grace period: members take periodic damage and the
     * village is deleted when the grace timer runs out, unless a core is re-placed to reactivate it.
     */
    INACTIVE
}
