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
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
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
        int rx = Math.floorDiv(cx, CityPlan.REGION), rz = Math.floorDiv(cz, CityPlan.REGION);
        int size = CityPlan.sizeFor(level.getSeed(), rx, rz);
        int offset = CityPlan.offsetFor(size);
        int x = Math.floorMod(cx, CityPlan.REGION) - offset;
        int z = Math.floorMod(cz, CityPlan.REGION) - offset;
        if (x < 0 || z < 0 || x >= size || z >= size
                || !CityPlan.hasCity(level.getSeed(), rx, rz)) return false;
        Optional<CityLayout> planned = CitySite.layout(context.chunkGenerator(), level,
                level.getLevel().getChunkSource().randomState(), level.getSeed(), rx, rz);
        if (planned.isEmpty()) return false;
        CityLayout layout = planned.get();
        CityPlan.Cell cell = layout.cell(x, z);
        int minX = cx * 16, minZ = cz * 16;
        if (cell.kind() == CityPlan.Kind.PARK
                && layout.plotUse(x, z) != CityLayout.PlotUse.WATERFRONT) return false;
        RoadProfile profile = new RoadProfile(level, context.chunkGenerator(), cx, cz);
        if (layout.plotUse(x, z) == CityLayout.PlotUse.WATERFRONT)
            return placeQuay(level, profile, minX, minZ,
                    ShoreGeometry.openWaterMask(layout, x, z), 1 << cell.front().ordinal());
        if (cell.kind() == CityPlan.Kind.LOT) {
            CityLayout.LargePlot plot = layout.largePlotAt(x, z);
            if (plot != null) {
                ResourceLocation category = category(plot.district());
                Optional<StructureDefinition> larger = StructureCatalog.INSTANCE.chooseLarge(category,
                        plot.widthCells() * 16 - 4, plot.depthCells() * 16 - 4,
                        plot.front() == CityPlan.Front.NORTH || plot.front() == CityPlan.Front.SOUTH, plot.seed());
                if (larger.isPresent()) {
                    Optional<LargePlacement> placement = prepareLarge(level, context.chunkGenerator(), profile,
                            rx, rz, plot, larger.get());
                    if (placement.isPresent())
                        return placeLargePiece(level, minX, minZ, plot, placement.get());
                }
            }
            return placeLot(level, profile, minX, minZ, cell);
        }
        return placeRoad(level, profile, minX, minZ, layout, x, z, cell);
    }

    private static boolean placeRoad(WorldGenLevel level, RoadProfile profile, int minX, int minZ,
                                     CityLayout layout, int cx, int cz, CityPlan.Cell cell) {
        int access = 0;
        CityPlan.Cell north = layout.cell(cx, cz - 1);
        CityPlan.Cell east = layout.cell(cx + 1, cz);
        CityPlan.Cell south = layout.cell(cx, cz + 1);
        CityPlan.Cell west = layout.cell(cx - 1, cz);
        if (frontsRoad(north, layout.plotUse(cx, cz - 1), CityPlan.Front.SOUTH)) access |= CityPlan.NORTH;
        if (frontsRoad(east, layout.plotUse(cx + 1, cz), CityPlan.Front.WEST)) access |= CityPlan.EAST;
        if (frontsRoad(south, layout.plotUse(cx, cz + 1), CityPlan.Front.NORTH)) access |= CityPlan.SOUTH;
        if (frontsRoad(west, layout.plotUse(cx - 1, cz), CityPlan.Front.EAST)) access |= CityPlan.WEST;
        int roads = cell.connections();
        int shore = layout.roadClass(cx, cz) == CityLayout.RoadClass.WATERFRONT
                ? ShoreGeometry.openWaterMask(layout, cx, cz) : 0;
        // First soften the edge of the roadway within this chunk. Its paved arms
        // connect to the same grade in the adjacent road chunk.
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            if (paved(roads, access, shore, dx, dz)) continue;
            int distance = distanceToPavement(roads, access, shore, dx, dz);
            if (distance > 3) continue;
            int x = minX + dx, z = minZ + dz;
            TerrainWorks.shoulder(level, x, z, profile.surfaceY(x, z) - 1, distance, 4);
        }
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            if (!paved(roads, access, shore, dx, dz)) continue;
            int x = minX + dx, z = minZ + dz;
            BlockState surface = arms(roads, dx, dz, LANE_HALF_WIDTH)
                    ? Blocks.GRAY_CONCRETE.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState();
            TerrainWorks.grade(level, x, z, profile.surfaceY(x, z) - 1,
                    surface, 4, Blocks.STONE.defaultBlockState());
        }
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            if (!ShoreGeometry.rail(shore, dx, dz)) continue;
            int x = minX + dx, z = minZ + dz;
            int groundY = profile.surfaceY(x, z) - 1;
            if (TerrainWorks.fitsHeight(level, groundY, 2))
                level.setBlock(new BlockPos(x, groundY + 1, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        }
        return true;
    }

    private static boolean frontsRoad(CityPlan.Cell cell, CityLayout.PlotUse use, CityPlan.Front front) {
        return (use == CityLayout.PlotUse.BUILDING || use == CityLayout.PlotUse.WATERFRONT)
                && cell.front() == front;
    }

    private static int distanceToPavement(int roads, int access, int shore, int x, int z) {
        for (int distance = 1; distance <= 3; distance++) {
            for (int dx = -distance; dx <= distance; dx++) for (int dz = -distance; dz <= distance; dz++) {
                if (Math.abs(dx) + Math.abs(dz) != distance) continue;
                int nx = x + dx, nz = z + dz;
                if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16 && paved(roads, access, shore, nx, nz))
                    return distance;
            }
        }
        return 4;
    }

    private static boolean paved(int roads, int access, int shore, int dx, int dz) {
        return arms(roads, dx, dz, SIDEWALK_HALF_WIDTH)
                || arms(access, dx, dz, PATH_HALF_WIDTH)
                || ShoreGeometry.shoreBand(shore, dx, dz);
    }

    private static boolean placeQuay(WorldGenLevel level, RoadProfile profile, int minX, int minZ,
                                     int shore, int frontage) {
        if (shore == 0 || !TerrainWorks.fitsHeight(level, profile.surfaceY(minX + 8, minZ + 8) - 1, 4))
            return false;
        // Validate the complete path first: an unusable cliff or flooded strip
        // stays natural instead of leaving an isolated fragment of promenade.
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            if (!ShoreGeometry.quay(shore, frontage, dx, dz)) continue;
            int x = minX + dx, z = minZ + dz;
            int groundY = profile.surfaceY(x, z) - 1;
            if (!TerrainWorks.fitsHeight(level, groundY, 4)
                    || Math.abs(TerrainWorks.naturalY(level, x, z) - groundY) > 5
                    || !TerrainWorks.canBuildLot(level, x, z, groundY)) return false;
        }
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            if (!ShoreGeometry.quay(shore, frontage, dx, dz)) continue;
            int x = minX + dx, z = minZ + dz;
            int groundY = profile.surfaceY(x, z) - 1;
            TerrainWorks.grade(level, x, z, groundY, Blocks.STONE_BRICKS.defaultBlockState(), 4,
                    Blocks.STONE.defaultBlockState());
            if (ShoreGeometry.rail(shore, dx, dz))
                level.setBlock(new BlockPos(x, groundY + 1, z), Blocks.STONE_BRICKS.defaultBlockState(), 2);
        }
        return true;
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
        Optional<StructureDefinition> selected = StructureCatalog.INSTANCE.choose(
                category(cell.district()), 12, 12, cell.lotSeed());
        return selected.isPresent() && placeBuilding(level, profile, minX, minZ, cell, selected.get());
    }

    private static ResourceLocation category(CityPlan.District district) {
        return ResourceLocation.fromNamespaceAndPath(LastHopeCityGen.MOD_ID,
                district.name().toLowerCase(Locale.ROOT));
    }

    private record LargePlacement(StructureDefinition definition, Rotation rotation,
                                  int originX, int originZ, int groundY, int plotMinX, int plotMinZ,
                                  int width, int depth) { }

    private static Optional<LargePlacement> prepareLarge(WorldGenLevel level, ChunkGenerator generator,
                                                          RoadProfile profile, int regionX, int regionZ,
                                                          CityLayout.LargePlot plot, StructureDefinition definition) {
        Optional<StructureTemplate> found = level.getLevel().getStructureManager().get(definition.singleSource().template());
        if (found.isEmpty()) return Optional.empty();
        StructureTemplate template = found.get();
        if (template.getSize().getX() != definition.dimensions().width()
                || template.getSize().getY() != definition.dimensions().height()
                || template.getSize().getZ() != definition.dimensions().depth()) return Optional.empty();
        int width = plot.widthCells() * 16, depth = plot.depthCells() * 16;
        int offset = CityPlan.offsetFor(CityPlan.sizeFor(level.getSeed(), regionX, regionZ));
        int plotMinX = (regionX * CityPlan.REGION + offset + plot.x()) * 16;
        int plotMinZ = (regionZ * CityPlan.REGION + offset + plot.z()) * 16;
        Rotation rotation = rotation(definition.front(), plot.front());
        BoundingBox bounds = template.getBoundingBox(new StructurePlaceSettings().setRotation(rotation), BlockPos.ZERO);
        int rotatedWidth = bounds.maxX() - bounds.minX() + 1;
        int rotatedDepth = bounds.maxZ() - bounds.minZ() + 1;
        if (rotatedWidth > width - 4 || rotatedDepth > depth - 4) return Optional.empty();
        int edgeX = plotMinX + (plot.front() == CityPlan.Front.WEST ? 0
                : plot.front() == CityPlan.Front.EAST ? width - 1 : width / 2);
        int edgeZ = plotMinZ + (plot.front() == CityPlan.Front.NORTH ? 0
                : plot.front() == CityPlan.Front.SOUTH ? depth - 1 : depth / 2);
        int groundY = profile.surfaceY(edgeX, edgeZ) - 1;
        if (!TerrainWorks.fitsHeight(level, groundY, template.getSize().getY() + 2)) return Optional.empty();
        RandomState randomState = level.getLevel().getChunkSource().randomState();
        // This decision uses only the unmodified generator for the whole plot.
        // Both chunk invocations therefore either place their piece or fall back.
        for (int x = plotMinX; x < plotMinX + width; x++) for (int z = plotMinZ; z < plotMinZ + depth; z++) {
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
            int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
            if (surface > floor || Math.abs(surface - 1 - groundY) > TerrainWorks.MAX_LOT_EARTHWORK)
                return Optional.empty();
        }
        int originX = plotMinX + (width - rotatedWidth) / 2 - bounds.minX();
        int originZ = plotMinZ + (depth - rotatedDepth) / 2 - bounds.minZ();
        return Optional.of(new LargePlacement(definition, rotation, originX, originZ,
                groundY, plotMinX, plotMinZ, width, depth));
    }

    private static boolean placeLargePiece(WorldGenLevel level, int minX, int minZ,
                                           CityLayout.LargePlot plot, LargePlacement placement) {
        int groundY = placement.groundY();
        int minPlotX = placement.plotMinX(), minPlotZ = placement.plotMinZ();
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = minX + dx, z = minZ + dz;
            int px = x - minPlotX, pz = z - minPlotZ;
            int distance = Math.max(Math.max(2 - px, px - (placement.width() - 3)),
                    Math.max(2 - pz, pz - (placement.depth() - 3)));
            if (distance > 0 && distance <= 2)
                TerrainWorks.shoulder(level, x, z, groundY, distance, 3);
        }
        for (int dx = 0; dx < 16; dx++) for (int dz = 0; dz < 16; dz++) {
            int x = minX + dx, z = minZ + dz;
            int px = x - minPlotX, pz = z - minPlotZ;
            boolean pad = px >= 2 && px < placement.width() - 2
                    && pz >= 2 && pz < placement.depth() - 2;
            boolean frontage = switch (plot.front()) {
                case NORTH -> pz < 2 && px >= 2 && px < placement.width() - 2;
                case SOUTH -> pz >= placement.depth() - 2 && px >= 2 && px < placement.width() - 2;
                case WEST -> px < 2 && pz >= 2 && pz < placement.depth() - 2;
                case EAST -> px >= placement.width() - 2 && pz >= 2 && pz < placement.depth() - 2;
            };
            if (pad || frontage)
                TerrainWorks.grade(level, x, z, groundY, Blocks.STONE_BRICKS.defaultBlockState(),
                        placement.definition().dimensions().height() + 2, Blocks.STONE.defaultBlockState());
        }
        BlockPos origin = new BlockPos(placement.originX(), groundY + 1, placement.originZ());
        BoundingBox chunkBounds = new BoundingBox(minX, level.getMinBuildHeight(), minZ,
                minX + 15, level.getMaxBuildHeight() - 1, minZ + 15);
        return SingleStructureBuilder.INSTANCE.build(level, StructurePlacement.at(placement.definition(),
                origin, placement.rotation()), chunkBounds, RandomSource.create(plot.seed()));
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
