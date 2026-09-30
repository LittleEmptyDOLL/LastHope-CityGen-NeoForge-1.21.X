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
        int size = CityPlan.sizeFor(seed, regionX, regionZ);
        int offset = CityPlan.offsetFor(size);
        int[][] height = new int[size][size];
        boolean[][] water = new boolean[size][size];
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            int blockX = (regionX * CityPlan.REGION + offset + x) * 16 + 8;
            int blockZ = (regionZ * CityPlan.REGION + offset + z) * 16 + 8;
            int surface = generator.getBaseHeight(blockX, blockZ, Heightmap.Types.WORLD_SURFACE_WG,
                    level, randomState);
            int floor = generator.getBaseHeight(blockX, blockZ, Heightmap.Types.OCEAN_FLOOR_WG,
                    level, randomState);
            height[x][z] = surface;
            water[x][z] = surface > floor;
        }
        Optional<CityLayout> planned = CityLayout.plan(seed, regionX, regionZ,
                new CityLayout.Terrain(height, water));
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
