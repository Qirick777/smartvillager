package com.yourname.smartvillager.demand;

/**
 * State machine for a demand task (design v3 section 1-3).
 *
 * <pre>
 * PENDING --(expanded, has children)--> BLOCKED --(all children DONE)--> READY
 * PENDING --(expanded, no children)---------------------------------> READY
 * READY --(assigned)--> ASSIGNED --> IN_PROGRESS --> DONE
 * any --> CANCELLED (demand disappeared) | EXPIRED (stuck too long)
 * </pre>
 */
public enum TaskState {
    PENDING,
    BLOCKED,
    READY,
    ASSIGNED,
    IN_PROGRESS,
    DONE,
    CANCELLED,
    EXPIRED
}
