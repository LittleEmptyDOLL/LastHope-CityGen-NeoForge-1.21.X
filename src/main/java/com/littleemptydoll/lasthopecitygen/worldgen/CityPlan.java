package com.littleemptydoll.lasthopecitygen.worldgen;

import java.util.Optional;
import java.util.function.Predicate;

/** Pure, order-independent street and lot plan. Coordinates in at() are chunks. */
public final class CityPlan {
    public static final int REGION = 32;
    public static final int SIZE = 8;
    public static final int REGION_BLOCKS = REGION * 16;
    private static final int OFFSET = (REGION - SIZE) / 2;
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

    public static Cell at(long seed, int chunkX, int chunkZ) {
        int rx = Math.floorDiv(chunkX, REGION);
        int rz = Math.floorDiv(chunkZ, REGION);
        int x = Math.floorMod(chunkX, REGION) - OFFSET;
        int z = Math.floorMod(chunkZ, REGION) - OFFSET;
        if (x < 0 || z < 0 || x >= SIZE || z >= SIZE || !hasCity(seed, rx, rz)) {
            return new Cell(Kind.OUTSIDE, District.RESIDENTIAL, Front.NORTH, 0, 0);
        }
        long citySeed = mix(seed ^ ((long) rx * 0xD6E8FEB86659FD93L)
                ^ ((long) rz * 0xA5A3564E27F8862DL));
        int roadNeighbors = neighbors(citySeed, x, z);
        long cellSeed = mix(seed ^ ((long) chunkX * 0x9E3779B97F4A7C15L)
                ^ ((long) chunkZ * 0xC2B2AE3D27D4EB4FL));
        District district = x >= 5 && z >= 5 ? District.INDUSTRIAL
                : x >= 5 && z <= 3 ? District.CIVIC : District.RESIDENTIAL;
        if (isRoad(citySeed, x, z)) {
            int degree = Integer.bitCount(roadNeighbors);
            Kind kind = switch (degree) {
                case 4 -> Kind.CROSS;
                case 3 -> Kind.T_JUNCTION;
                case 2 -> roadNeighbors == (NORTH | SOUTH) || roadNeighbors == (EAST | WEST)
                        ? Kind.STRAIGHT : Kind.CORNER;
                default -> Kind.DEAD_END;
            };
            return new Cell(kind, district, Front.NORTH, roadNeighbors, cellSeed);
        }
        if (roadNeighbors == 0) return new Cell(Kind.PARK, district, Front.NORTH, 0, cellSeed);
        Front front = frontage(roadNeighbors, cellSeed);
        return new Cell(Kind.LOT, district, front, 0, cellSeed);
    }

    private static boolean isRoad(long citySeed, int x, int z) {
        if (x < 0 || z < 0 || x >= SIZE || z >= SIZE) return false;
        // A connected 7x7 street grid with two optional short spurs. Its ends form
        // corners and T junctions without leaving any isolated road segments.
        if (x <= 6 && z <= 6 && (x % 3 == 0 || z % 3 == 0)) return true;
        return (x == 7 && z == 3 && (citySeed & 1) != 0)
                || (x == 3 && z == 7 && (citySeed & 2) != 0);
    }

    private static int neighbors(long citySeed, int x, int z) {
        int mask = 0;
        if (isRoad(citySeed, x, z - 1)) mask |= NORTH;
        if (isRoad(citySeed, x + 1, z)) mask |= EAST;
        if (isRoad(citySeed, x, z + 1)) mask |= SOUTH;
        if (isRoad(citySeed, x - 1, z)) mask |= WEST;
        return mask;
    }

    private static Front frontage(int roads, long seed) {
        Front[] directions = Front.values();
        for (int offset = 0; offset < directions.length; offset++) {
            int index = (int) ((seed + offset) & 3);
            if ((roads & (1 << index)) != 0) return directions[index];
        }
        throw new IllegalArgumentException("Lot has no street frontage");
    }

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
