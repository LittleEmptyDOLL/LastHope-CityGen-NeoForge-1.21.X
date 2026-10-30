package com.littleemptydoll.lasthopecitygen.worldgen;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/** Shared, order-independent road grade from the original terrain around each anchor. */
final class RoadProfile {
    private static final Cache<RawKey, Integer> HEIGHTS = CacheBuilder.newBuilder()
            .maximumSize(32768).build();
    private record RawKey(ChunkGenerator generator, RandomState randomState,
                          int spacing, int gridX, int gridZ) { }

    private final WorldGenLevel level;
    private final ChunkGenerator generator;
    private final RandomState randomState;
    private final int cityHeight;
    private final int spacing;
    private final int maxRelief;
    private final Map<Long, Integer> samples = new HashMap<>();

    RoadProfile(WorldGenLevel level, ChunkGenerator generator, int chunkX, int chunkZ,
                CityGenerationSettings settings) {
        this.level = level;
        this.generator = generator;
        this.randomState = level.getLevel().getChunkSource().randomState();
        this.spacing = settings.roadSampleSpacing();
        this.maxRelief = settings.roadReliefLimit();
        CityPlan.CityCenter center = CityPlan.centerForRegion(
                Math.floorDiv(chunkX, CityPlan.REGION), Math.floorDiv(chunkZ, CityPlan.REGION));
        this.cityHeight = rawSample(Math.floorDiv(center.blockX(), spacing),
                Math.floorDiv(center.blockZ(), spacing));
    }

    int surfaceY(int x, int z) {
        int gx = Math.floorDiv(x, spacing);
        int gz = Math.floorDiv(z, spacing);
        int dx = Math.floorMod(x, spacing);
        int dz = Math.floorMod(z, spacing);
        long a = (long) sample(gx, gz) * (spacing - dx) * (spacing - dz);
        long b = (long) sample(gx + 1, gz) * dx * (spacing - dz);
        long c = (long) sample(gx, gz + 1) * (spacing - dx) * dz;
        long d = (long) sample(gx + 1, gz + 1) * dx * dz;
        return (int) Math.round((a + b + c + d) / (double) (spacing * spacing));
    }

    private int sample(int gx, int gz) {
        long key = ((long) gx << 32) ^ (gz & 0xffffffffL);
        return samples.computeIfAbsent(key, ignored -> {
            // Nearby original surface blocks soften ridges without depending on
            // the order in which the neighboring road chunks are generated.
            int natural = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                int weight = (dx == 0 ? 2 : 1) * (dz == 0 ? 2 : 1);
                natural += rawSample(gx + dx, gz + dz) * weight;
            }
            natural = (int) Math.round(natural / 16.0);
            int limited = Math.max(cityHeight - maxRelief,
                    Math.min(cityHeight + maxRelief, natural));
            // Keep open water below the deck, rather than cutting a channel through it.
            int aboveWater = Math.max(generator.getSeaLevel() + 1, limited);
            return Math.max(level.getMinBuildHeight() + 8,
                    Math.min(level.getMaxBuildHeight() - 8, aboveWater));
        });
    }

    private int rawSample(int gx, int gz) {
        RawKey key = new RawKey(generator, randomState, spacing, gx, gz);
        try {
            // Neighboring road chunks ask for the same unmodified height grid.
            return HEIGHTS.get(key, () -> generator.getBaseHeight(gx * spacing, gz * spacing,
                    Heightmap.Types.WORLD_SURFACE_WG, level, randomState));
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Unable to sample road height at " + gx + "," + gz,
                    exception.getCause());
        }
    }
}
