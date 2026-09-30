package com.littleemptydoll.lasthopecitygen.worldgen;

import java.util.Optional;
import java.util.function.Predicate;

/** Seed-selected city regions and shared cell types for the terrain-aware layout. */
public final class CityPlan {
    public static final int REGION = 32;
    public static final int SIZE = 8;
    public static final int MAX_SIZE = 16;
    public static final int REGION_BLOCKS = REGION * 16;
    private static final int SEARCH_RADIUS_REGIONS = 16;

    public static final int NORTH = 1;
    public static final int EAST = 2;
    public static final int SOUTH = 4;
    public static final int WEST = 8;

    public enum Kind { OUTSIDE, STRAIGHT, CORNER, T_JUNCTION, CROSS, DEAD_END, LOT, PARK }
    public enum District { RESIDENTIAL, INDUSTRIAL, CIVIC }
    public enum Front { NORTH, EAST, SOUTH, WEST }

    public record Cell(Kind kind, District district, Front front, int connections, long lotSeed) {
        public boolean isRoad() {
            return switch (kind) {
                case STRAIGHT, CORNER, T_JUNCTION, CROSS, DEAD_END -> true;
                default -> false;
            };
        }
    }
    public record CityCenter(int blockX, int blockZ) { }

    private CityPlan() { }

    /** Even footprints keep the center of every city at the region center. */
    public static int sizeFor(long seed, int regionX, int regionZ) {
        long value = mix(seed ^ ((long) regionX * 0x71D67FFFEDA60001L)
                ^ ((long) regionZ * 0x9E3779B97F4A7C15L) ^ 0x5B27C1A9D3E4F608L);
        return SIZE + 2 * (int) Long.remainderUnsigned(value, 5);
    }

    public static int offsetFor(int size) { return (REGION - size) / 2; }

    /** One region in six; region selection does not depend on chunk traversal. */
    public static boolean hasCity(long seed, int regionX, int regionZ) {
        return Long.remainderUnsigned(mix(seed ^ ((long) regionX * 0xD6E8FEB86659FD93L)
                ^ ((long) regionZ * 0xA5A3564E27F8862DL)), 6) == 0;
    }

    /** Finds a planned city; terrain may prevent some cells from being placed. */
    public static Optional<CityCenter> nearestCity(long seed, int blockX, int blockZ) {
        return nearestCity(seed, blockX, blockZ, center -> true);
    }

    /** Allows callers to skip unsuitable sites without changing the seed-based plan. */
    public static Optional<CityCenter> nearestCity(long seed, int blockX, int blockZ,
                                                    Predicate<CityCenter> suitable) {
        int regionX = Math.floorDiv(blockX, REGION_BLOCKS);
        int regionZ = Math.floorDiv(blockZ, REGION_BLOCKS);
        CityCenter nearest = null;
        long bestDistance = Long.MAX_VALUE;
        for (int rx = regionX - SEARCH_RADIUS_REGIONS; rx <= regionX + SEARCH_RADIUS_REGIONS; rx++) {
            for (int rz = regionZ - SEARCH_RADIUS_REGIONS; rz <= regionZ + SEARCH_RADIUS_REGIONS; rz++) {
                if (!hasCity(seed, rx, rz)) continue;
                CityCenter candidate = centerForRegion(rx, rz);
                int x = candidate.blockX();
                int z = candidate.blockZ();
                long dx = (long) x - blockX;
                long dz = (long) z - blockZ;
                long distance = dx * dx + dz * dz;
                if (distance < bestDistance && suitable.test(candidate)) {
                    bestDistance = distance;
                    nearest = candidate;
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    public static CityCenter centerForRegion(int regionX, int regionZ) {
        return new CityCenter(regionX * REGION_BLOCKS + REGION_BLOCKS / 2,
                regionZ * REGION_BLOCKS + REGION_BLOCKS / 2);
    }

    public static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
