package com.yourname.smartvillager.village;

import net.minecraft.core.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pure, world-independent implementation of the village bed-recognition algorithm
 * (design document section 5, "BFS 확장 방식").
 *
 * <p>The algorithm decides which beds belong to a village as a flood-fill:</p>
 * <ol>
 *   <li>Every candidate bed within {@code coreRadius} of the core is recognized (tier 1).</li>
 *   <li>Any not-yet-recognized bed within {@code bedExpansionRadius} of an already-recognized
 *       bed is recognized too, and itself becomes a source for further expansion.</li>
 *   <li>Step 2 repeats until no new bed is added (BFS/flood-fill termination).</li>
 * </ol>
 *
 * <p>This class intentionally depends only on {@link BlockPos} as an immutable coordinate holder
 * (no {@code Level}/world access), so it can be exercised directly in unit tests.</p>
 */
public final class VillageBedScanner {

    private VillageBedScanner() {
    }

    /**
     * Computes the set of beds recognized as belonging to a village.
     *
     * @param corePos           the village core position (BFS seed origin)
     * @param coreRadius         tier-1 recognition radius around the core, in blocks (design: 64)
     * @param bedExpansionRadius expansion radius around each recognized bed, in blocks (design: 32)
     * @param candidateBeds      all bed positions to consider (e.g. beds found near the village)
     * @return recognized bed positions, in the order they were recognized (tier 1 first)
     */
    public static List<BlockPos> computeRecognizedBeds(BlockPos corePos,
                                                       int coreRadius,
                                                       int bedExpansionRadius,
                                                       Collection<BlockPos> candidateBeds) {
        // LinkedHashSet: O(1) membership checks + stable, recognition-order iteration.
        Set<BlockPos> recognized = new LinkedHashSet<>();
        Deque<BlockPos> frontier = new ArrayDeque<>();

        long coreRadiusSq = (long) coreRadius * coreRadius;
        long bedRadiusSq = (long) bedExpansionRadius * bedExpansionRadius;

        // Tier 1: beds within coreRadius of the core.
        for (BlockPos bed : candidateBeds) {
            if (bed != null && distanceSq(corePos, bed) <= coreRadiusSq && recognized.add(bed)) {
                frontier.add(bed);
            }
        }

        // Tier 2+: recursive expansion from each recognized bed.
        while (!frontier.isEmpty()) {
            BlockPos source = frontier.poll();
            for (BlockPos bed : candidateBeds) {
                if (bed != null && !recognized.contains(bed)
                        && distanceSq(source, bed) <= bedRadiusSq
                        && recognized.add(bed)) {
                    frontier.add(bed);
                }
            }
        }

        return new ArrayList<>(recognized);
    }

    /**
     * Squared Euclidean distance between two positions, computed with {@code long} math so large
     * world coordinates cannot overflow. Squared distance avoids a {@code sqrt} and lets callers
     * compare against a squared radius.
     */
    private static long distanceSq(BlockPos a, BlockPos b) {
        long dx = (long) a.getX() - b.getX();
        long dy = (long) a.getY() - b.getY();
        long dz = (long) a.getZ() - b.getZ();
        return dx * dx + dy * dy + dz * dz;
    }
}
