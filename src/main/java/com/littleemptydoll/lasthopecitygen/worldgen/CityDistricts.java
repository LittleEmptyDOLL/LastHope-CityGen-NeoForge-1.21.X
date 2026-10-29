package com.littleemptydoll.lasthopecitygen.worldgen;

/** A small, deterministic district field shared by every chunk of the city. */
final class CityDistricts {
    private final int civicX, civicZ, industrialX, industrialZ;
    private final int civicRadius, industrialRadius;
    private final long seed;

    CityDistricts(long seed, CityLayout.Terrain terrain, CityGenerationSettings.CityPlanning rules) {
        this.seed = seed;
        int size = terrain.size();
        int middle = (size - 1) / 2;
        int bestCivic = Integer.MAX_VALUE;
        int cx = middle, cz = middle;
        for (int x = 1; x < size - 1; x++) for (int z = 1; z < size - 1; z++) {
            if (!terrain.dry(x, z)) continue;
            int score = Math.abs(x - middle) + Math.abs(z - middle)
                    + adjacentWater(terrain, x, z) * 3;
            if (score < bestCivic) { bestCivic = score; cx = x; cz = z; }
        }
        civicX = cx;
        civicZ = cz;
        int side = (int) Long.remainderUnsigned(CityPlan.mix(seed ^ 0xD7E28FCB3051901DL), 4);
        int targetX = side == 1 ? size * 4 / 5 : side == 3 ? size / 5 : middle;
        int targetZ = side == 2 ? size * 4 / 5 : side == 0 ? size / 5 : middle;
        int bestIndustry = Integer.MAX_VALUE;
        int ix = targetX, iz = targetZ;
        for (int x = 1; x < size - 1; x++) for (int z = 1; z < size - 1; z++) {
            if (!terrain.dry(x, z)) continue;
            int score = Math.abs(x - targetX) + Math.abs(z - targetZ)
                    + adjacentWater(terrain, x, z) * 3;
            if (Math.abs(x - cx) + Math.abs(z - cz) < size / 3) score += size;
            if (score < bestIndustry) { bestIndustry = score; ix = x; iz = z; }
        }
        industrialX = ix;
        industrialZ = iz;
        civicRadius = Math.max(2, size * rules.civicRadiusPercent() / 100);
        industrialRadius = Math.max(2, size * rules.industrialRadiusPercent() / 100);
    }

    CityPlan.District at(int x, int z) {
        // A small deterministic perturbation softens the district borders.
        long noise = CityPlan.mix(seed ^ ((long) (x / 2) * 0x9E3779B97F4A7C15L)
                ^ ((long) (z / 2) * 0xC2B2AE3D27D4EB4FL));
        int edge = (int) Long.remainderUnsigned(noise, 3) - 1;
        int civicDistance = Math.abs(x - civicX) + Math.abs(z - civicZ);
        int industrialDistance = Math.abs(x - industrialX) + Math.abs(z - industrialZ);
        if (civicDistance <= civicRadius + edge) return CityPlan.District.CIVIC;
        if (industrialDistance <= industrialRadius + edge) return CityPlan.District.INDUSTRIAL;
        return CityPlan.District.RESIDENTIAL;
    }

    private static int adjacentWater(CityLayout.Terrain terrain, int x, int z) {
        int count = 0;
        if (!terrain.dry(x - 1, z)) count++;
        if (!terrain.dry(x + 1, z)) count++;
        if (!terrain.dry(x, z - 1)) count++;
        if (!terrain.dry(x, z + 1)) count++;
        return count;
    }
}
