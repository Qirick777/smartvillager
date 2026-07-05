package com.yourname.smartvillager.village;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link VillageBedScanner} (design document section 5 BFS algorithm).
 *
 * <p>These run with plain JUnit ({@code ./gradlew test}) and touch no world state — only the
 * immutable {@link BlockPos} coordinate holder — matching the "world-independent, unit testable"
 * requirement for Task 1.3.</p>
 */
class VillageBedScannerTest {

    private static final BlockPos CORE = new BlockPos(0, 0, 0);
    private static final int CORE_RADIUS = 64;
    private static final int BED_RADIUS = 32;

    @Test
    void tier1_bedWithinCoreRadius_isRecognized() {
        BlockPos bed = new BlockPos(60, 0, 0); // 60 <= 64
        List<BlockPos> result =
                VillageBedScanner.computeRecognizedBeds(CORE, CORE_RADIUS, BED_RADIUS, List.of(bed));
        assertTrue(result.contains(bed));
    }

    @Test
    void bedExactlyAtCoreRadius_isIncluded() {
        BlockPos bed = new BlockPos(0, 64, 0); // exactly on the boundary (<=)
        List<BlockPos> result =
                VillageBedScanner.computeRecognizedBeds(CORE, CORE_RADIUS, BED_RADIUS, List.of(bed));
        assertTrue(result.contains(bed));
    }

    @Test
    void isolatedBedBeyondCoreRadius_isExcluded() {
        BlockPos bed = new BlockPos(0, 0, 70); // 70 > 64, nothing else nearby
        List<BlockPos> result =
                VillageBedScanner.computeRecognizedBeds(CORE, CORE_RADIUS, BED_RADIUS, List.of(bed));
        assertFalse(result.contains(bed));
    }

    @Test
    void chainExpansion_carriesRecognitionBeyondCoreRadius() {
        BlockPos a = new BlockPos(60, 0, 0);   // tier 1: 60 from core
        BlockPos b = new BlockPos(85, 0, 0);   // 85 > 64 from core, but 25 from A -> tier 2
        BlockPos c = new BlockPos(110, 0, 0);  // 110 from core, but 25 from B -> tier 3
        BlockPos far = new BlockPos(200, 0, 0); // 90 from C -> never reached

        List<BlockPos> result = VillageBedScanner.computeRecognizedBeds(
                CORE, CORE_RADIUS, BED_RADIUS, Arrays.asList(a, b, c, far));

        assertTrue(result.contains(a), "A within core radius");
        assertTrue(result.contains(b), "B chained via A");
        assertTrue(result.contains(c), "C chained via B");
        assertFalse(result.contains(far), "far bed is out of the expansion chain");
        assertEquals(3, result.size());
    }

    @Test
    void tier1BedsAreRecognizedBeforeExpandedBeds() {
        BlockPos a = new BlockPos(60, 0, 0);  // tier 1
        BlockPos b = new BlockPos(85, 0, 0);  // tier 2 (via A)
        List<BlockPos> result = VillageBedScanner.computeRecognizedBeds(
                CORE, CORE_RADIUS, BED_RADIUS, Arrays.asList(b, a));
        assertTrue(result.indexOf(a) < result.indexOf(b),
                "tier-1 beds should be recognized before expanded beds");
    }

    @Test
    void emptyCandidates_yieldEmptyResult() {
        assertTrue(VillageBedScanner
                .computeRecognizedBeds(CORE, CORE_RADIUS, BED_RADIUS, Collections.emptyList())
                .isEmpty());
    }
}
