package com.yourname.smartvillager.time;

/**
 * The daily cycle phases from design document section 4, derived from the level's day time.
 *
 * <ul>
 *   <li>{@link #DAY}: 0–12000 (and 23000–24000 dawn) — villagers do their job work.</li>
 *   <li>{@link #EVENING}: 12000–13000 — gather near the core; the manager recalculates demand.</li>
 *   <li>{@link #NIGHT}: 13000–23000 — sleep.</li>
 * </ul>
 */
public enum DayPhase {
    DAY,
    EVENING,
    NIGHT;

    /** Ticks in a full Minecraft day. */
    public static final long TICKS_PER_DAY = 24000L;

    /**
     * Maps a raw day-time value (any magnitude; wrapped into a single day) to its phase.
     *
     * @param dayTime the level day time, e.g. from {@code Level.getDayTime()}
     */
    public static DayPhase fromDayTime(long dayTime) {
        long t = Math.floorMod(dayTime, TICKS_PER_DAY);
        if (t < 12000L) {
            return DAY;
        }
        if (t < 13000L) {
            return EVENING;
        }
        if (t < 23000L) {
            return NIGHT;
        }
        return DAY; // 23000–24000: dawn
    }
}
