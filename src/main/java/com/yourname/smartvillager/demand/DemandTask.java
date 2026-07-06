package com.yourname.smartvillager.demand;

import com.yourname.smartvillager.data.Job;
import com.yourname.smartvillager.data.ResourceType;
import com.yourname.smartvillager.task.TaskType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A node in a village's demand graph (design v3 section 1-2).
 */
public class DemandTask {

    public final UUID id = UUID.randomUUID();
    public final TaskType type;
    /** GATHER tasks: what resource is being collected. */
    public final ResourceType targetResource;
    /** CRAFT_TOOL tasks: which job's tool and which tier. */
    public final Job targetJob;
    public final int targetTier;

    public int amountRequired;
    public int amountDone;
    public double basePriority;
    public double effectivePriority;
    public TaskState state = TaskState.PENDING;

    public final List<UUID> parentIds = new ArrayList<>();
    public final List<UUID> childIds = new ArrayList<>();
    public Job assigneeJob;
    public final String dedupKey;

    private DemandTask(TaskType type, ResourceType targetResource, Job targetJob, int targetTier,
                       String dedupKey) {
        this.type = type;
        this.targetResource = targetResource;
        this.targetJob = targetJob;
        this.targetTier = targetTier;
        this.dedupKey = dedupKey;
        this.assigneeJob = type.assigneeJob();
    }

    /** A gather task, keyed by type+resource so it merges across parents. */
    public static DemandTask gather(TaskType type, ResourceType resource) {
        return new DemandTask(type, resource, null, 0, type.name() + ":" + resource.name());
    }

    /** A craft-tool task, keyed by job+tier. */
    public static DemandTask craftTool(Job job, int tier) {
        return new DemandTask(TaskType.CRAFT_TOOL, null, job, tier,
                TaskType.CRAFT_TOOL.name() + ":" + job.name() + ":" + tier);
    }

    @Override
    public String toString() {
        String what = targetResource != null
                ? targetResource.name()
                : (targetJob != null ? targetJob.name() + "@t" + targetTier : "");
        return String.format("%s[%s]x%d p=%.0f/%.0f %s",
                type, what, amountRequired, basePriority, effectivePriority, state);
    }
}
