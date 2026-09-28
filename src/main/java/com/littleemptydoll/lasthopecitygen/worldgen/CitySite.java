package com.littleemptydoll.lasthopecitygen.worldgen;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Decides whether an entire seed-selected city has enough dry land. */
public final class CitySite {
    private static final int SAMPLE_OFFSET = 56;
    private static final int CACHE_SIZE = 256;
    private static final Map<Key, Boolean> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(
            CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Boolean> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    private record Key(ChunkGenerator generator, RandomState randomState, int centerX, int centerZ) { }

    private CitySite() { }

    public static boolean isSuitable(ChunkGenerator generator, LevelHeightAccessor level,
                                     RandomState randomState, int centerX, int centerZ) {
        Key key = new Key(generator, randomState, centerX, centerZ);
        Boolean cached = CACHE.get(key);
        if (cached != null) return cached;
        boolean suitable = sample(generator, level, randomState, centerX, centerZ);
        CACHE.put(key, suitable);
        return suitable;
    }

    private static boolean sample(ChunkGenerator generator, LevelHeightAccessor level,
                                  RandomState randomState, int centerX, int centerZ) {
        // A river can cross one edge of an otherwise dry city, but a city cannot
        // start in water or extend substantially into an ocean, lake or delta.
        if (isWater(generator, level, randomState, centerX, centerZ)) return false;
        int wetSamples = 0;
        for (int dx = -SAMPLE_OFFSET; dx <= SAMPLE_OFFSET; dx += SAMPLE_OFFSET) {
            for (int dz = -SAMPLE_OFFSET; dz <= SAMPLE_OFFSET; dz += SAMPLE_OFFSET) {
                if (dx == 0 && dz == 0) continue;
                if (isWater(generator, level, randomState, centerX + dx, centerZ + dz)
                        && ++wetSamples > 1) return false;
            }
        }
        return true;
    }

    private static boolean isWater(ChunkGenerator generator, LevelHeightAccessor level,
                                   RandomState randomState, int x, int z) {
        int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
        int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
        return surface > floor;
    }
}
