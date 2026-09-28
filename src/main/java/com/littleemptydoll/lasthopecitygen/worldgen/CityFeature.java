package com.littleemptydoll.lasthopecitygen.worldgen;

import com.littleemptydoll.lasthopecitygen.LastHopeCityGen;
import com.littleemptydoll.lasthopecitygen.structure.builder.SingleStructureBuilder;
import com.littleemptydoll.lasthopecitygen.structure.catalog.StructureCatalog;
import com.littleemptydoll.lasthopecitygen.structure.definition.StructureDefinition;
import com.littleemptydoll.lasthopecitygen.structure.placement.StructurePlacement;
import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Each feature invocation writes only to its own chunk. */
public final class CityFeature extends Feature<NoneFeatureConfiguration> {
    private static final int LANE_HALF_WIDTH = 3;
    private static final int SIDEWALK_HALF_WIDTH = 5;
    private static final int PATH_HALF_WIDTH = 1;

    public CityFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();
        int cx = Math.floorDiv(context.origin().getX(), 16);
        int cz = Math.floorDiv(context.origin().getZ(), 16);
        CityPlan.Cell cell = CityPlan.at(level.getSeed(), cx, cz);
        if (cell.kind() == CityPlan.Kind.OUTSIDE || cell.kind() == CityPlan.Kind.PARK) return false;
        CityPlan.CityCenter center = CityPlan.centerForRegion(
                Math.floorDiv(cx, CityPlan.REGION), Math.floorDiv(cz, CityPlan.REGION));
        if (!CitySite.isSuitable(context.chunkGenerator(), level,
                level.getLevel().getChunkSource().randomState(), center.blockX(), center.blockZ())) return false;
        int minX = cx * 16, minZ = cz * 16;
        RoadProfile profile = new RoadProfile(level, context.chunkGenerator(), cx, cz);
        if (cell.kind() == CityPlan.Kind.LOT) return placeLot(level, profile, minX, minZ, cell);
        return placeRoad(level, profile, minX, minZ, cx, cz, cell);
    }

    private static boolean placeRoad(WorldGenLevel level, RoadProfile profile, int minX, int minZ,
                                     int cx, int cz, CityPlan.Cell cell) {
        int access = 0;
        CityPlan.Cell north = CityPlan.at(level.getSeed(), cx, cz - 1);
        CityPlan.Cell east = CityPlan.at(level.getSeed(), cx + 1, cz);
        CityPlan.Cell south = CityPlan.at(level.getSeed(), cx, cz + 1);
        CityPlan.Cell west = CityPlan.at(level.getSeed(), cx - 1, cz);
        if (north.kind() == CityPlan.Kind.LOT && north.front() == CityPlan.Front.SOUTH) access |= CityPlan.NORTH;
        if (east.kind() == CityPlan.Kind.LOT && east.front() == CityPlan.Front.WEST) access |= CityPlan.EAST;
        if (south.kind() == CityPlan.Kind.LOT && south.front() == CityPlan.Front.NORTH) access |= CityPlan.SOUTH;
        if (west.kind() == CityPlan.Kind.LOT && west.front() == CityPlan.Front.EAST) access |= CityPlan.WEST;
        int roads = cell.connections();
        // First soften the edge of the roadway within this chunk. Its paved arms
        // connect to the same grade in the adjacent road chunk.
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            if (paved(roads, access, dx, dz)) continue;
            int distance = distanceToPavement(roads, access, dx, dz);
            if (distance > 3) continue;
            int x = minX + dx, z = minZ + dz;
            TerrainWorks.shoulder(level, x, z, profile.surfaceY(x, z) - 1, distance, 4);
        }
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            if (!paved(roads, access, dx, dz)) continue;
            int x = minX + dx, z = minZ + dz;
            BlockState surface = arms(roads, dx, dz, LANE_HALF_WIDTH)
                    ? Blocks.GRAY_CONCRETE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            TerrainWorks.grade(level, x, z, profile.surfaceY(x, z) - 1,
                    surface, 4, Blocks.STONE.defaultBlockState());
        }
        return true;
    }

    private static int distanceToPavement(int roads, int access, int x, int z) {
        for (int distance = 1; distance <= 3; distance++) {
            for (int dx = -distance; dx <= distance; dx++) for (int dz = -distance; dz <= distance; dz++) {
                if (Math.abs(dx) + Math.abs(dz) != distance) continue;
                int nx = x + dx, nz = z + dz;
                if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16 && paved(roads, access, nx, nz))
                    return distance;
            }
        }
        return 4;
    }

    private static boolean paved(int roads, int access, int dx, int dz) {
        return arms(roads, dx, dz, SIDEWALK_HALF_WIDTH)
                || arms(access, dx, dz, PATH_HALF_WIDTH);
    }

    private static boolean arms(int mask, int dx, int dz, int halfWidth) {
        int x = dx - 8, z = dz - 8;
        return (Math.abs(x) <= halfWidth && Math.abs(z) <= halfWidth)
                || (mask & CityPlan.NORTH) != 0 && Math.abs(x) <= halfWidth && z <= 0
                || (mask & CityPlan.EAST) != 0 && Math.abs(z) <= halfWidth && x >= 0
                || (mask & CityPlan.SOUTH) != 0 && Math.abs(x) <= halfWidth && z >= 0
                || (mask & CityPlan.WEST) != 0 && Math.abs(z) <= halfWidth && x <= 0;
    }

    private static boolean placeLot(WorldGenLevel level, RoadProfile profile, int minX, int minZ,
                                    CityPlan.Cell cell) {
        ResourceLocation category = ResourceLocation.fromNamespaceAndPath(LastHopeCityGen.MOD_ID,
                cell.district().name().toLowerCase(Locale.ROOT));
        Optional<StructureDefinition> selected = StructureCatalog.INSTANCE.choose(category, 12, 12, cell.lotSeed());
        if (selected.isPresent() && placeBuilding(level, profile, minX, minZ, cell, selected.get()))
            return true;
        return placeVacantLot(level, profile, minX, minZ, cell.front());
    }

    private static boolean placeBuilding(WorldGenLevel level, RoadProfile profile, int minX, int minZ,
                                         CityPlan.Cell cell, StructureDefinition definition) {
        Optional<StructureTemplate> found = level.getLevel().getStructureManager().get(definition.singleSource().template());
        if (found.isEmpty()) return false;
        StructureTemplate template = found.get();
        if (template.getSize().getX() != definition.dimensions().width()
                || template.getSize().getY() != definition.dimensions().height()
                || template.getSize().getZ() != definition.dimensions().depth()) return false;

        Rotation rotation = rotation(definition.front(), cell.front());
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotation);
        BoundingBox bounds = template.getBoundingBox(settings, BlockPos.ZERO);
        int width = bounds.maxX() - bounds.minX() + 1;
        int depth = bounds.maxZ() - bounds.minZ() + 1;
        if (width > 12 || depth > 12) return false;
        int x = minX + (16 - width) / 2 - bounds.minX();
        int z = minZ + (16 - depth) / 2 - bounds.minZ();
        int edgeX = cell.front() == CityPlan.Front.WEST ? minX
                : cell.front() == CityPlan.Front.EAST ? minX + 15 : minX + 8;
        int edgeZ = cell.front() == CityPlan.Front.NORTH ? minZ
                : cell.front() == CityPlan.Front.SOUTH ? minZ + 15 : minZ + 8;
        int surfaceY = profile.surfaceY(edgeX, edgeZ);
        int groundY = surfaceY - 1;
        if (!TerrainWorks.fitsHeight(level, groundY, template.getSize().getY() + 2))
            return false;

        int buildingMinX = x + bounds.minX(), buildingMaxX = x + bounds.maxX();
        int buildingMinZ = z + bounds.minZ(), buildingMaxZ = z + bounds.maxZ();
        List<BlockPos> approach = approach(minX, minZ, cell.front(),
                buildingMinX, buildingMaxX, buildingMinZ, buildingMaxZ);
        for (int dx = 2; dx <= 13; dx++) for (int dz = 2; dz <= 13; dz++)
            if (!TerrainWorks.canBuildLot(level, minX + dx, minZ + dz, groundY)) return false;
        for (BlockPos pos : approach)
            if (!TerrainWorks.canBuildLot(level, pos.getX(), pos.getZ(), groundY)) return false;

        // The outer two blocks slope back to the untouched terrain. The pad and
        // approach are written afterward to keep the entrance completely clear.
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int distance = Math.max(Math.max(2 - dx, dx - 13), Math.max(2 - dz, dz - 13));
            if (distance <= 0 || approach.contains(new BlockPos(minX + dx, 0, minZ + dz))) continue;
            TerrainWorks.shoulder(level, minX + dx, minZ + dz, groundY, distance, 3);
        }

        for (int dx = 2; dx <= 13; dx++) for (int dz = 2; dz <= 13; dz++)
            TerrainWorks.grade(level, minX + dx, minZ + dz, groundY,
                    Blocks.STONE_BRICKS.defaultBlockState(), template.getSize().getY() + 2,
                    Blocks.STONE.defaultBlockState());
        for (BlockPos pos : approach)
            TerrainWorks.grade(level, pos.getX(), pos.getZ(), groundY,
                    Blocks.STONE_BRICKS.defaultBlockState(), 4, Blocks.STONE.defaultBlockState());

        BlockPos origin = new BlockPos(x, surfaceY, z);
        BoundingBox chunkBounds = new BoundingBox(minX, level.getMinBuildHeight(), minZ,
                minX + 15, level.getMaxBuildHeight() - 1, minZ + 15);
        return SingleStructureBuilder.INSTANCE.build(level, StructurePlacement.at(definition, origin, rotation),
                chunkBounds, RandomSource.create(cell.lotSeed()));
    }

    /** Every planned frontage ends at a small terrace even if a building cannot fit. */
    private static boolean placeVacantLot(WorldGenLevel level, RoadProfile profile, int minX, int minZ,
                                          CityPlan.Front front) {
        int edgeX = front == CityPlan.Front.WEST ? minX : front == CityPlan.Front.EAST ? minX + 15 : minX + 8;
        int edgeZ = front == CityPlan.Front.NORTH ? minZ : front == CityPlan.Front.SOUTH ? minZ + 15 : minZ + 8;
        int groundY = profile.surfaceY(edgeX, edgeZ) - 1;
        if (!TerrainWorks.fitsHeight(level, groundY, 4)) return false;
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int cross = terraceCross(front, dx, dz);
            int inward = terraceInward(front, dx, dz);
            int distance = Math.max(6 - cross, Math.max(cross - 9, 0)) + Math.max(inward - 5, 0);
            if (distance > 0 && distance <= 2)
                TerrainWorks.shoulder(level, minX + dx, minZ + dz, groundY, distance, 3);
        }
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int cross = terraceCross(front, dx, dz);
            int inward = terraceInward(front, dx, dz);
            if (cross < 6 || cross > 9 || inward > 5) continue;
            TerrainWorks.grade(level, minX + dx, minZ + dz, groundY,
                    Blocks.STONE_BRICKS.defaultBlockState(), 4, Blocks.STONE.defaultBlockState());
            if (inward == 5 || inward >= 2 && (cross == 6 || cross == 9))
                level.setBlock(new BlockPos(minX + dx, groundY + 1, minZ + dz),
                        Blocks.STONE_BRICK_WALL.defaultBlockState(), 2);
        }
        return true;
    }

    private static int terraceCross(CityPlan.Front front, int dx, int dz) {
        return front == CityPlan.Front.NORTH || front == CityPlan.Front.SOUTH ? dx : dz;
    }

    private static int terraceInward(CityPlan.Front front, int dx, int dz) {
        return switch (front) {
            case NORTH -> dz;
            case EAST -> 15 - dx;
            case SOUTH -> 15 - dz;
            case WEST -> dx;
        };
    }

    private static List<BlockPos> approach(int minX, int minZ, CityPlan.Front front,
                                           int left, int right, int top, int bottom) {
        List<BlockPos> path = new ArrayList<>();
        int centerX = (left + right) / 2, centerZ = (top + bottom) / 2;
        switch (front) {
            case NORTH -> {
                for (int z = minZ; z < top; z++) for (int x = centerX - 1; x <= centerX + 1; x++)
                    path.add(new BlockPos(x, 0, z));
            }
            case EAST -> {
                for (int x = right + 1; x <= minX + 15; x++) for (int z = centerZ - 1; z <= centerZ + 1; z++)
                    path.add(new BlockPos(x, 0, z));
            }
            case SOUTH -> {
                for (int z = bottom + 1; z <= minZ + 15; z++) for (int x = centerX - 1; x <= centerX + 1; x++)
                    path.add(new BlockPos(x, 0, z));
            }
            case WEST -> {
                for (int x = minX; x < left; x++) for (int z = centerZ - 1; z <= centerZ + 1; z++)
                    path.add(new BlockPos(x, 0, z));
            }
        }
        return path;
    }

    private static Rotation rotation(Direction from, CityPlan.Front to) {
        int fromIndex = switch (from) {
            case NORTH -> 0;
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> throw new IllegalArgumentException("A structure front must be horizontal");
        };
        int turns = Math.floorMod(to.ordinal() - fromIndex, 4);
        return switch (turns) {
            case 1 -> Rotation.CLOCKWISE_90;
            case 2 -> Rotation.CLOCKWISE_180;
            case 3 -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
