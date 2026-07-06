package com.yourname.smartvillager.task;

/**
 * A single demand entry in a village's demand queue (design document section 8).
 *
 * <p>{@code shortage} is the shortage rate {@code (need - have) / need} in {@code (0, 1]}; higher
 * means more urgent. The manager sorts the queue by this value so the most-lacking need is served
 * first.</p>
 */
public class VillagerTask {

    private final TaskType type;
    private final double shortage;

    public VillagerTask(TaskType type, double shortage) {
        this.type = type;
        this.shortage = shortage;
    }

    public TaskType getType() {
        return type;
    }

    public double getShortage() {
        return shortage;
    }

    @Override
    public String toString() {
        return String.format("%s(%.2f)", type, shortage);
    }
}
