package com.littleemptydoll.lasthopecitygen.worldgen;

import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Captures the unmodified worldgen terrain once for every planned city region. */
public final class CitySite {
    private static final int CACHE_SIZE = 256;
    private static final Map<Key, Optional<CityLayout>> CACHE = Collections.synchronizedMap(new LinkedHashMap<>(
            CACHE_SIZE, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Optional<CityLayout>> eldest) {
            return size() > CACHE_SIZE;
        }
    });

    private record Key(ChunkGenerator generator, RandomState randomState, long seed,
                       int regionX, int regionZ) { }

    private CitySite() { }

    public static Optional<CityLayout> layout(ChunkGenerator generator, LevelHeightAccessor level,
                                              RandomState randomState, long seed, int regionX, int regionZ) {
        Key key = new Key(generator, randomState, seed, regionX, regionZ);
        Optional<CityLayout> cached = CACHE.get(key);
        if (cached != null) return cached;
        int[][] height = new int[CityPlan.SIZE][CityPlan.SIZE];
        boolean[][] water = new boolean[CityPlan.SIZE][CityPlan.SIZE];
        for (int x = 0; x < CityPlan.SIZE; x++) for (int z = 0; z < CityPlan.SIZE; z++) {
            int blockX = (regionX * CityPlan.REGION + CityPlan.OFFSET + x) * 16 + 8;
            int blockZ = (regionZ * CityPlan.REGION + CityPlan.OFFSET + z) * 16 + 8;
            int surface = generator.getBaseHeight(blockX, blockZ, Heightmap.Types.WORLD_SURFACE_WG,
                    level, randomState);
            int floor = generator.getBaseHeight(blockX, blockZ, Heightmap.Types.OCEAN_FLOOR_WG,
                    level, randomState);
            height[x][z] = surface;
            water[x][z] = surface > floor;
        }
        Optional<CityLayout> planned = CityLayout.plan(seed, regionX, regionZ,
                new CityLayout.Terrain(height, water, generator.getSeaLevel()));
        CACHE.put(key, planned);
        return planned;
    }

    public static boolean isSuitable(ChunkGenerator generator, LevelHeightAccessor level,
                                     RandomState randomState, long seed, int centerX, int centerZ) {
        return layout(generator, level, randomState, seed,
                Math.floorDiv(centerX, CityPlan.REGION_BLOCKS),
                Math.floorDiv(centerZ, CityPlan.REGION_BLOCKS)).isPresent();
    }
}
