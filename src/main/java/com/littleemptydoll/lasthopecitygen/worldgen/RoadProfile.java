package com.littleemptydoll.lasthopecitygen.worldgen;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.HashMap;
import java.util.Map;

/** Shared, order-independent road grade. Neighboring chunks sample the same anchors. */
final class RoadProfile {
    private static final int SPACING = 32;
    private static final int MAX_CITY_RELIEF = 12;
    private final WorldGenLevel level;
    private final ChunkGenerator generator;
    private final RandomState randomState;
    private final int cityHeight;
    private final Map<Long, Integer> samples = new HashMap<>();

    RoadProfile(WorldGenLevel level, ChunkGenerator generator, int chunkX, int chunkZ) {
        this.level = level;
        this.generator = generator;
        this.randomState = level.getLevel().getChunkSource().randomState();
        CityPlan.CityCenter center = CityPlan.centerForRegion(
                Math.floorDiv(chunkX, CityPlan.REGION), Math.floorDiv(chunkZ, CityPlan.REGION));
        this.cityHeight = generator.getBaseHeight(center.blockX(), center.blockZ(),
                Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
    }

    int surfaceY(int x, int z) {
        int gx = Math.floorDiv(x, SPACING);
        int gz = Math.floorDiv(z, SPACING);
        int dx = Math.floorMod(x, SPACING);
        int dz = Math.floorMod(z, SPACING);
        long a = (long) sample(gx, gz) * (SPACING - dx) * (SPACING - dz);
        long b = (long) sample(gx + 1, gz) * dx * (SPACING - dz);
        long c = (long) sample(gx, gz + 1) * (SPACING - dx) * dz;
        long d = (long) sample(gx + 1, gz + 1) * dx * dz;
        return (int) Math.round((a + b + c + d) / (double) (SPACING * SPACING));
    }

    private int sample(int gx, int gz) {
        long key = ((long) gx << 32) ^ (gz & 0xffffffffL);
        return samples.computeIfAbsent(key, ignored -> {
            int natural = generator.getBaseHeight(gx * SPACING, gz * SPACING,
                    Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
            int limited = Math.max(cityHeight - MAX_CITY_RELIEF,
                    Math.min(cityHeight + MAX_CITY_RELIEF, natural));
            // Keep open water below the deck, rather than cutting a channel through it.
            int aboveWater = Math.max(generator.getSeaLevel() + 1, limited);
            return Math.max(level.getMinBuildHeight() + 8,
                    Math.min(level.getMaxBuildHeight() - 8, aboveWater));
        });
    }
}
