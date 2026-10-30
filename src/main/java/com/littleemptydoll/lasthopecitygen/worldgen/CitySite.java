package com.littleemptydoll.lasthopecitygen.worldgen;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.Optional;
import java.util.concurrent.ExecutionException;

/** Captures the unmodified worldgen terrain once for every planned city region. */
public final class CitySite {
    private static final int CACHE_SIZE = 256;
    // Cache.get performs one calculation per key even when adjacent chunks
    // arrive on different worldgen threads at the same time.
    private static final Cache<Key, Optional<CityLayout>> CACHE = CacheBuilder.newBuilder()
            .maximumSize(CACHE_SIZE).build();

    private record Key(ChunkGenerator generator, RandomState randomState, CityGenerationSettings settings, long seed,
                       int regionX, int regionZ) { }

    private CitySite() { }

    public static Optional<CityLayout> layout(ChunkGenerator generator, LevelHeightAccessor level,
                                              RandomState randomState, long seed, int regionX, int regionZ) {
        return layout(generator, level, randomState, seed, regionX, regionZ,
                CityGenerationSettings.current());
    }

    public static Optional<CityLayout> layout(ChunkGenerator generator, LevelHeightAccessor level,
                                              RandomState randomState, long seed, int regionX, int regionZ,
                                              CityGenerationSettings settings) {
        Key key = new Key(generator, randomState, settings, seed, regionX, regionZ);
        try {
            return CACHE.get(key, () -> plan(generator, level, randomState, seed, regionX, regionZ, settings));
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Unable to plan city region " + regionX + "," + regionZ,
                    exception.getCause());
        }
    }

    private static Optional<CityLayout> plan(ChunkGenerator generator, LevelHeightAccessor level,
                                              RandomState randomState, long seed, int regionX, int regionZ,
                                              CityGenerationSettings settings) {
        int size = CityPlan.sizeFor(seed, regionX, regionZ, settings);
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
        return CityLayout.plan(seed, regionX, regionZ,
                new CityLayout.Terrain(height, water), settings);
    }

    public static boolean isSuitable(ChunkGenerator generator, LevelHeightAccessor level,
                                     RandomState randomState, long seed, int centerX, int centerZ) {
        return isSuitable(generator, level, randomState, seed, centerX, centerZ,
                CityGenerationSettings.current());
    }

    public static boolean isSuitable(ChunkGenerator generator, LevelHeightAccessor level,
                                     RandomState randomState, long seed, int centerX, int centerZ,
                                     CityGenerationSettings settings) {
        return layout(generator, level, randomState, seed,
                Math.floorDiv(centerX, CityPlan.REGION_BLOCKS),
                Math.floorDiv(centerZ, CityPlan.REGION_BLOCKS), settings).isPresent();
    }
}
