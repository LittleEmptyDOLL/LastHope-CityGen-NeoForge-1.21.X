package com.littleemptydoll.lasthopecitygen.worldgen;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/** Grading, shallow embankments, bridge decks and short tunnel cuts within one chunk. */
final class TerrainWorks {
    static final int MAX_LOT_EARTHWORK = 12;
    private static final int OPEN_CUT_DEPTH = 12;
    private static final int SOLID_FILL_DEPTH = 6;
    private static final int SUBGRADE_DEPTH = 8;

    private TerrainWorks() { }

    static boolean fitsHeight(WorldGenLevel level, int targetY, int clearance) {
        return targetY > level.getMinBuildHeight() + 1
                && targetY + clearance + 2 < level.getMaxBuildHeight();
    }

    static boolean canBuildLot(WorldGenLevel level, int x, int z, int targetY) {
        int naturalY = naturalY(level, x, z);
        return Math.abs(naturalY - targetY) <= MAX_LOT_EARTHWORK
                && level.getFluidState(new BlockPos(x, naturalY, z)).isEmpty();
    }

    static int naturalY(WorldGenLevel level, int x, int z) {
        return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
    }

    /** Transition to the original ground over a few blocks; extreme cliffs keep their rock face. */
    static void shoulder(WorldGenLevel level, int x, int z, int targetY, int distance, int radius) {
        int naturalY = naturalY(level, x, z);
        boolean wet = !level.getFluidState(new BlockPos(x, naturalY, z)).isEmpty();
        if (Math.abs(naturalY - targetY) > MAX_LOT_EARTHWORK || wet) {
            if (distance == 1 && fitsHeight(level, targetY, 4)) {
                if (naturalY > targetY + MAX_LOT_EARTHWORK) {
                    // The cliff itself becomes the retaining wall of a short tunnel.
                    for (int y = targetY; y <= targetY + 4; y++)
                        level.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                } else if (naturalY <= targetY) {
                    // Low ground or water gets a simple edge rail beside the deck.
                    level.setBlock(new BlockPos(x, targetY, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x, targetY + 1, z), Blocks.STONE_BRICK_WALL.defaultBlockState(), 2);
                }
            }
            return;
        }
        int blendedY = (int) Math.round(targetY + (naturalY - targetY) * distance / (double) radius);
        if (!fitsHeight(level, blendedY, 4)) return;
        grade(level, x, z, blendedY, Blocks.COARSE_DIRT.defaultBlockState(), 4,
                Blocks.DIRT.defaultBlockState());
    }

    static void grade(WorldGenLevel level, int x, int z, int targetY,
                      BlockState surface, int clearance, BlockState fill) {
        int naturalY = naturalY(level, x, z);
        boolean wet = !level.getFluidState(new BlockPos(x, naturalY, z)).isEmpty();
        boolean earthwork = !wet && targetY - naturalY <= SOLID_FILL_DEPTH;
        if (targetY > naturalY || wet && targetY == naturalY) {
            if (!earthwork) {
                // A thin deck avoids turning rivers and deep valleys into solid dams.
                level.setBlock(new BlockPos(x, targetY - 1, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                if (Math.floorMod(x, 4) == 0 && Math.floorMod(z, 4) == 0) {
                    int floorY = wet ? level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1 : naturalY;
                    for (int y = floorY + 1; y < targetY - 1; y++)
                        level.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
                }
            }
        }
        if (earthwork) {
            // Compact the shallow subgrade as well as the raised strip. Caves and
            // uneven ground could otherwise leave unsupported single-block roads.
            for (int y = targetY - 1; y >= Math.max(level.getMinBuildHeight(), targetY - SUBGRADE_DEPTH); y--) {
                BlockPos below = new BlockPos(x, y, z);
                if (level.getBlockState(below).isAir() && level.getFluidState(below).isEmpty())
                    level.setBlock(below, fill, 2);
            }
        }
        level.setBlock(new BlockPos(x, targetY, z), surface, 2);
        int top = naturalY - targetY <= OPEN_CUT_DEPTH
                ? Math.max(naturalY, targetY) + clearance : targetY + clearance;
        top = Math.min(top, level.getMaxBuildHeight() - 1);
        for (int y = targetY + 1; y <= top; y++)
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
        if (naturalY - targetY > OPEN_CUT_DEPTH)
            level.setBlock(new BlockPos(x, targetY + clearance + 1, z),
                    Blocks.STONE_BRICKS.defaultBlockState(), 2);
    }
}
