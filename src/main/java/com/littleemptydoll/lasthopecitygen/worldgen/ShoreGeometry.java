package com.littleemptydoll.lasthopecitygen.worldgen;

/** Shore-facing shapes in one 16x16 chunk; no neighboring chunk is written. */
final class ShoreGeometry {
    private ShoreGeometry() { }

    static int waterMask(CityLayout layout, int x, int z) {
        int mask = 0;
        if (layout.isWater(x, z - 1)) mask |= CityPlan.NORTH;
        if (layout.isWater(x + 1, z)) mask |= CityPlan.EAST;
        if (layout.isWater(x, z + 1)) mask |= CityPlan.SOUTH;
        if (layout.isWater(x - 1, z)) mask |= CityPlan.WEST;
        return mask;
    }

    static int openWaterMask(CityLayout layout, int x, int z) {
        int mask = waterMask(layout, x, z);
        if (layout.cell(x, z - 1).isRoad()) mask &= ~CityPlan.NORTH;
        if (layout.cell(x + 1, z).isRoad()) mask &= ~CityPlan.EAST;
        if (layout.cell(x, z + 1).isRoad()) mask &= ~CityPlan.SOUTH;
        if (layout.cell(x - 1, z).isRoad()) mask &= ~CityPlan.WEST;
        return mask;
    }

    static boolean beach(int mask, int x, int z) {
        return (mask & CityPlan.NORTH) != 0 && z <= 5
                || (mask & CityPlan.EAST) != 0 && x >= 10
                || (mask & CityPlan.SOUTH) != 0 && z >= 10
                || (mask & CityPlan.WEST) != 0 && x <= 5;
    }

    static boolean quay(int mask, int front, int x, int z) {
        if ((mask & CityPlan.NORTH) != 0 && z <= 5
                || (mask & CityPlan.EAST) != 0 && x >= 10
                || (mask & CityPlan.SOUTH) != 0 && z >= 10
                || (mask & CityPlan.WEST) != 0 && x <= 5) return true;
        if (x >= 7 && x <= 9 && z >= 7 && z <= 9) return true;
        return (mask & CityPlan.NORTH) != 0 && x >= 7 && x <= 9 && z <= 8
                || (mask & CityPlan.EAST) != 0 && z >= 7 && z <= 9 && x >= 8
                || (mask & CityPlan.SOUTH) != 0 && x >= 7 && x <= 9 && z >= 8
                || (mask & CityPlan.WEST) != 0 && z >= 7 && z <= 9 && x <= 8
                || (front & CityPlan.NORTH) != 0 && x >= 7 && x <= 9 && z <= 8
                || (front & CityPlan.EAST) != 0 && z >= 7 && z <= 9 && x >= 8
                || (front & CityPlan.SOUTH) != 0 && x >= 7 && x <= 9 && z >= 8
                || (front & CityPlan.WEST) != 0 && z >= 7 && z <= 9 && x <= 8;
    }

    static boolean rail(int mask, int x, int z) {
        return (mask & CityPlan.NORTH) != 0 && z == 0
                || (mask & CityPlan.EAST) != 0 && x == 15
                || (mask & CityPlan.SOUTH) != 0 && z == 15
                || (mask & CityPlan.WEST) != 0 && x == 0;
    }
}
