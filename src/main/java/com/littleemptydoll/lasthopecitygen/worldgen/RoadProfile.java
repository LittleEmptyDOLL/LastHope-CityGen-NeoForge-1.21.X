package com.littleemptydoll.lasthopecitygen.worldgen;

import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

import java.util.HashMap;
import java.util.Map;

/** Samples unmodified terrain at shared 32-block anchors; results do not depend on chunk order. */
final class RoadProfile {
    private static final int SPACING = 32;
    private final WorldGenLevel level;
    private final ChunkGenerator generator;
    private final RandomState randomState;
    private final Map<Long, Integer> samples = new HashMap<>();

    RoadProfile(WorldGenLevel level, ChunkGenerator generator) {
        this.level = level;
        this.generator = generator;
        this.randomState = level.getLevel().getChunkSource().randomState();
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
        return samples.computeIfAbsent(key, ignored -> generator.getBaseHeight(gx * SPACING, gz * SPACING,
                Heightmap.Types.WORLD_SURFACE_WG, level, randomState));
    }
}
