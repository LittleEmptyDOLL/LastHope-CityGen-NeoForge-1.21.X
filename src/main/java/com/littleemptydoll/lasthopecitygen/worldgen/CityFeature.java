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
import net.minecraft.world.level.levelgen.Heightmap;
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
    private static final int MAX_EARTHWORK = 10;
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
        int minX = cx * 16, minZ = cz * 16;
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, minX + 8, minZ + 8);
        if (surface <= level.getMinBuildHeight()
                || !level.getFluidState(new BlockPos(minX + 8, surface - 1, minZ + 8)).isEmpty()) return false;
        RoadProfile profile = new RoadProfile(level, context.chunkGenerator());
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
        // Validate the complete paved area before changing any blocks in this chunk.
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            if (!paved(roads, access, dx, dz)) continue;
            int x = minX + dx, z = minZ + dz;
            if (!canPrepare(level, x, z, profile.surfaceY(x, z) - 1, 4)) return false;
        }
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            if (!paved(roads, access, dx, dz)) continue;
            int x = minX + dx, z = minZ + dz;
            BlockState surface = arms(roads, dx, dz, LANE_HALF_WIDTH)
                    ? Blocks.GRAY_CONCRETE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            prepare(level, x, z, profile.surfaceY(x, z) - 1, surface, 4);
        }
        return true;
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
        if (selected.isEmpty()) return false;
        StructureDefinition definition = selected.get();
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
        if (surfaceY <= level.getMinBuildHeight() || surfaceY + template.getSize().getY() + 2 >= level.getMaxBuildHeight())
            return false;

        int buildingMinX = x + bounds.minX(), buildingMaxX = x + bounds.maxX();
        int buildingMinZ = z + bounds.minZ(), buildingMaxZ = z + bounds.maxZ();
        List<BlockPos> approach = approach(minX, minZ, cell.front(),
                buildingMinX, buildingMaxX, buildingMinZ, buildingMaxZ);
        for (int dx = 2; dx <= 13; dx++) for (int dz = 2; dz <= 13; dz++)
            if (!canPrepare(level, minX + dx, minZ + dz, groundY, template.getSize().getY() + 2)) return false;
        for (BlockPos pos : approach)
            if (!canPrepare(level, pos.getX(), pos.getZ(), groundY, 4)) return false;

        for (int dx = 2; dx <= 13; dx++) for (int dz = 2; dz <= 13; dz++)
            prepare(level, minX + dx, minZ + dz, groundY, Blocks.STONE_BRICKS.defaultBlockState(),
                    template.getSize().getY() + 2);
        for (BlockPos pos : approach)
            prepare(level, pos.getX(), pos.getZ(), groundY, Blocks.STONE_BRICKS.defaultBlockState(), 4);

        BlockPos origin = new BlockPos(x, surfaceY, z);
        BoundingBox chunkBounds = new BoundingBox(minX, level.getMinBuildHeight(), minZ,
                minX + 15, level.getMaxBuildHeight() - 1, minZ + 15);
        return SingleStructureBuilder.INSTANCE.build(level, StructurePlacement.at(definition, origin, rotation),
                chunkBounds, RandomSource.create(cell.lotSeed()));
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

    private static boolean canPrepare(WorldGenLevel level, int x, int z, int targetY, int clearance) {
        if (targetY <= level.getMinBuildHeight() || targetY + clearance >= level.getMaxBuildHeight()) return false;
        int naturalY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        return Math.abs(naturalY - targetY) <= MAX_EARTHWORK
                && level.getFluidState(new BlockPos(x, naturalY, z)).isEmpty();
    }

    private static void prepare(WorldGenLevel level, int x, int z, int targetY, BlockState surface, int clearance) {
        int naturalY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        for (int y = naturalY + 1; y < targetY; y++)
            level.setBlock(new BlockPos(x, y, z), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(new BlockPos(x, targetY, z), surface, 2);
        for (int y = targetY + 1; y <= Math.max(targetY, naturalY) + clearance; y++)
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 2);
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
