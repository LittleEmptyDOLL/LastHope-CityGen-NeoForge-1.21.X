package com.littleemptydoll.lasthopecitygen.worldgen;

import java.util.Optional;

/** Pure, order-independent planning. All coordinates here are chunk coordinates. */
public final class CityPlan {
    public static final int REGION = 32;
    public static final int SIZE = 8;
    public static final int REGION_BLOCKS = REGION * 16;
    private static final int OFFSET = (REGION - SIZE) / 2;
    private static final int SEARCH_RADIUS_REGIONS = 16;

    public enum Kind { OUTSIDE, ROAD_NS, ROAD_EW, INTERSECTION, LOT }
    public enum District { RESIDENTIAL, INDUSTRIAL, CIVIC }
    public enum Front { NORTH, EAST, SOUTH, WEST }

    public record Cell(Kind kind, District district, Front front, long lotSeed) { }
    public record CityCenter(int blockX, int blockZ) { }

    private CityPlan() { }

    public static Cell at(long seed, int chunkX, int chunkZ) {
        int rx = Math.floorDiv(chunkX, REGION);
        int rz = Math.floorDiv(chunkZ, REGION);
        int x = Math.floorMod(chunkX, REGION) - OFFSET;
        int z = Math.floorMod(chunkZ, REGION) - OFFSET;
        if (x < 0 || z < 0 || x >= SIZE || z >= SIZE || !hasCity(seed, rx, rz)) {
            return new Cell(Kind.OUTSIDE, District.RESIDENTIAL, Front.NORTH, 0);
        }
        boolean ns = x == 0 || x == 4;
        boolean ew = z == 0 || z == 4;
        Kind kind = ns && ew ? Kind.INTERSECTION : ns ? Kind.ROAD_NS : ew ? Kind.ROAD_EW : Kind.LOT;
        long cellSeed = mix(seed ^ ((long) chunkX * 0x9E3779B97F4A7C15L) ^ ((long) chunkZ * 0xC2B2AE3D27D4EB4FL));
        District district = x >= 5 && z >= 5 ? District.INDUSTRIAL
                : x >= 5 && z <= 3 ? District.CIVIC : District.RESIDENTIAL;
        int nearestRoadX = x <= 2 ? 0 : 4;
        int nearestRoadZ = z <= 2 ? 0 : 4;
        int distanceX = Math.abs(x - nearestRoadX);
        int distanceZ = Math.abs(z - nearestRoadZ);
        Front front = distanceX <= distanceZ ? (nearestRoadX < x ? Front.WEST : Front.EAST)
                : (nearestRoadZ < z ? Front.NORTH : Front.SOUTH);
        return new Cell(kind, district, front, cellSeed);
    }

    /** One region in six; region selection does not depend on chunk traversal. */
    public static boolean hasCity(long seed, int regionX, int regionZ) {
        return Long.remainderUnsigned(mix(seed ^ ((long) regionX * 0xD6E8FEB86659FD93L)
                ^ ((long) regionZ * 0xA5A3564E27F8862DL)), 6) == 0;
    }

    /** Finds a planned city; terrain may prevent some cells from being placed. */
    public static Optional<CityCenter> nearestCity(long seed, int blockX, int blockZ) {
        int regionX = Math.floorDiv(blockX, REGION_BLOCKS);
        int regionZ = Math.floorDiv(blockZ, REGION_BLOCKS);
        CityCenter nearest = null;
        long bestDistance = Long.MAX_VALUE;
        for (int rx = regionX - SEARCH_RADIUS_REGIONS; rx <= regionX + SEARCH_RADIUS_REGIONS; rx++) {
            for (int rz = regionZ - SEARCH_RADIUS_REGIONS; rz <= regionZ + SEARCH_RADIUS_REGIONS; rz++) {
                if (!hasCity(seed, rx, rz)) continue;
                int x = rx * REGION_BLOCKS + REGION_BLOCKS / 2;
                int z = rz * REGION_BLOCKS + REGION_BLOCKS / 2;
                long dx = (long) x - blockX;
                long dz = (long) z - blockZ;
                long distance = dx * dx + dz * dz;
                if (distance < bestDistance) {
                    bestDistance = distance;
                    nearest = new CityCenter(x, z);
                }
            }
        }
        return Optional.ofNullable(nearest);
    }

    public static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }
}
