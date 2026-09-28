package com.littleemptydoll.lasthopecitygen.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.Optional;

/** Called once per chunk via a placed feature without placement modifiers. */
public final class CityFeature extends Feature<NoneFeatureConfiguration> {
    private static final int ROAD_HALF_WIDTH = 3;

    public CityFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int cx = Math.floorDiv(context.origin().getX(), 16);
        int cz = Math.floorDiv(context.origin().getZ(), 16);
        CityPlan.Cell cell = CityPlan.at(level.getSeed(), cx, cz);
        if (cell.kind() == CityPlan.Kind.OUTSIDE) return false;
        int minX = cx * 16, minZ = cz * 16;
        int centerY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, minX + 8, minZ + 8);
        if (centerY <= level.getMinBuildHeight() || !level.getFluidState(new BlockPos(minX + 8, centerY - 1, minZ + 8)).isEmpty())
            return false;
        if (cell.kind() == CityPlan.Kind.LOT) return placeLot(level, minX, minZ, cell);
        placeRoad(level, minX, minZ, cell.kind());
        return true;
    }

    private static void placeRoad(WorldGenLevel level, int minX, int minZ, CityPlan.Kind kind) {
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            boolean ns = kind != CityPlan.Kind.ROAD_EW && Math.abs(dx - 8) <= ROAD_HALF_WIDTH;
            boolean ew = kind != CityPlan.Kind.ROAD_NS && Math.abs(dz - 8) <= ROAD_HALF_WIDTH;
            boolean road = ns || ew;
            // Pavements cover the remainder of a road chunk, keeping the street clear.
            int x = minX + dx, z = minZ + dz;
            int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
            if (y <= level.getMinBuildHeight() || y >= level.getMaxBuildHeight() - 5) continue;
            BlockPos ground = new BlockPos(x, y, z);
            level.setBlock(ground, road ? Blocks.GRAY_CONCRETE.defaultBlockState()
                    : Blocks.STONE_BRICKS.defaultBlockState(), 2);
            for (int height = 1; height <= 3; height++)
                level.setBlock(ground.above(height), Blocks.AIR.defaultBlockState(), 2);
            // Shallow support stops a road built over water from floating.
            for (int depth = 1; depth <= 3; depth++) {
                BlockPos below = ground.below(depth);
                if (!level.getBlockState(below).isAir() && level.getFluidState(below).isEmpty()) break;
                level.setBlock(below, Blocks.STONE.defaultBlockState(), 2);
            }
        }
    }

    private static boolean placeLot(WorldGenLevel level, int minX, int minZ, CityPlan.Cell cell) {
        TemplateCatalog.Entry entry = TemplateCatalog.choose(cell.district(), cell.lotSeed());
        if (entry == null) return false;
        Optional<StructureTemplate> found = level.getLevel().getStructureManager().get(entry.template());
        if (found.isEmpty()) return false;
        StructureTemplate template = found.get();
        if (template.getSize().getX() != entry.width() || template.getSize().getZ() != entry.depth()) return false;

        Rotation rotation = rotation(entry.front(), cell.front());
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setIgnoreEntities(true)
                .setBoundingBox(new BoundingBox(minX, level.getMinBuildHeight(), minZ,
                        minX + 15, level.getMaxBuildHeight() - 1, minZ + 15));
        BoundingBox bounds = template.getBoundingBox(settings, BlockPos.ZERO);
        int width = bounds.maxX() - bounds.minX() + 1;
        int depth = bounds.maxZ() - bounds.minZ() + 1;
        if (width > 12 || depth > 12) return false;
        int x = minX + (16 - width) / 2 - bounds.minX();
        int z = minZ + (16 - depth) / 2 - bounds.minZ();
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, minX + 8, minZ + 8);
        if (y <= level.getMinBuildHeight() || y + template.getSize().getY() >= level.getMaxBuildHeight()) return false;
        // Buildings on steep or submerged plots would be torn apart. Leave the lot empty.
        for (int dx = 2; dx <= 13; dx += 11) for (int dz = 2; dz <= 13; dz += 11) {
            int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, minX + dx, minZ + dz);
            if (Math.abs(surface - y) > 2 || !level.getFluidState(new BlockPos(minX + dx, surface - 1, minZ + dz)).isEmpty())
                return false;
        }
        BlockPos origin = new BlockPos(x, y, z);
        RandomSource random = RandomSource.create(cell.lotSeed());
        return template.placeInWorld(level, origin, origin, settings, random, 2);
    }

    private static Rotation rotation(CityPlan.Front from, CityPlan.Front to) {
        int turns = Math.floorMod(to.ordinal() - from.ordinal(), 4);
        return switch (turns) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
