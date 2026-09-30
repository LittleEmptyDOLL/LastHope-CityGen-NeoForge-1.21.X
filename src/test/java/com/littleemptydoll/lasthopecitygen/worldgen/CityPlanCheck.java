package com.littleemptydoll.lasthopecitygen.worldgen;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

/** Runs without Minecraft: java -ea ...CityPlanCheck. */
public final class CityPlanCheck {
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};
    private static final int[] OPPOSITE = {CityPlan.SOUTH, CityPlan.WEST, CityPlan.NORTH, CityPlan.EAST};

    public static void main(String[] args) {
        long seed = 123456789;
        CityPlan.CityCenter first = CityPlan.nearestCity(seed, 0, 0).orElseThrow();
        CityPlan.CityCenter next = CityPlan.nearestCity(seed, 0, 0,
                candidate -> !candidate.equals(first)).orElseThrow();
        assert !first.equals(next);
        assert CityPlan.nearestCity(seed, 0, 0, candidate -> false).isEmpty();

        CityLayout.Terrain flat = terrain(64);
        Set<String> shapes = new HashSet<>();
        int largePlots = 0;
        for (int rx = -3; rx <= 3; rx++) for (int rz = -3; rz <= 3; rz++) {
            if (!CityPlan.hasCity(seed, rx, rz)) continue;
            CityLayout layout = CityLayout.plan(seed, rx, rz, flat).orElseThrow();
            verify(layout, flat);
            String signature = signature(layout);
            assert signature.equals(signature(CityLayout.plan(seed, rx, rz, flat).orElseThrow()));
            assert layout.largePlots().equals(CityLayout.plan(seed, rx, rz, flat).orElseThrow().largePlots());
            largePlots += layout.largePlots().size();
            shapes.add(signature);
        }
        assert shapes.size() >= 2 : "Every city has the same road pattern";
        assert largePlots > 0 : "No two-chunk plots on level terrain";
        int corners = 0, junctions = 0, crosses = 0;
        Set<Integer> roadCounts = new HashSet<>();
        for (long otherSeed = 1; otherSeed <= 64; otherSeed++) {
            CityLayout layout = CityLayout.plan(otherSeed, 1, -1, flat).orElseThrow();
            verify(layout, flat);
            roadCounts.add(layout.roadCount());
            assert layout.blocks().stream().anyMatch(block -> block.cells() >= 4)
                    : "No usable block on level ground";
            for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
                switch (layout.cell(x, z).kind()) {
                    case CORNER -> corners++;
                    case T_JUNCTION -> junctions++;
                    case CROSS -> crosses++;
                    default -> { }
                }
            }
        }
        assert corners > 0 && junctions > 0 && crosses > 0 : "Lost street variety";
        assert roadCounts.size() >= 3 : "Every city has the same street density";

        CityLayout.Terrain coast = terrain(64);
        for (int x = 6; x < 8; x++) for (int z = 0; z < 8; z++) coast.water()[x][z] = true;
        CityLayout coastal = CityLayout.plan(seed, 1, 0, coast).orElseThrow();
        verify(coastal, coast);
        assert shorePlots(coastal) > 0;
        assert dryRoads(coastal, coast) == coastal.roadCount() : "Road into the ocean";

        CityLayout.Terrain river = terrain(64);
        for (int z = 0; z < 8; z++) river.water()[4][z] = true;
        CityLayout riverside = CityLayout.plan(seed, 2, 0, river).orElseThrow();
        verify(riverside, river);
        assert dryRoads(riverside, river) > 0;
        for (long otherSeed = 1; otherSeed <= 32; otherSeed++) {
            CityLayout.plan(otherSeed, 2, 0, river).ifPresent(layout -> verify(layout, river));
            CityLayout.plan(otherSeed, 1, 0, coast).ifPresent(layout -> verify(layout, coast));
        }
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            if (!riverside.cell(x, z).isRoad()) continue;
            int mask = ShoreGeometry.openWaterMask(riverside, x, z);
            for (int i = 0; i < 4; i++) if ((mask & (1 << i)) != 0)
                assert !riverside.cell(x + DX[i], z + DZ[i]).isRoad() : "Rail across bridge entrance";
        }

        CityLayout.Terrain highCoast = terrain(72);
        for (int z = 0; z < 8; z++) {
            highCoast.water()[6][z] = highCoast.water()[7][z] = true;
            highCoast.height()[6][z] = highCoast.height()[7][z] = 64;
        }
        CityLayout high = CityLayout.plan(seed, 1, 0, highCoast).orElseThrow();
        verify(high, highCoast);
        int waterfront = 0;
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++)
            if (high.plotUse(x, z) == CityLayout.PlotUse.WATERFRONT) {
                waterfront++;
                CityPlan.Front front = high.cell(x, z).front();
                assert high.cell(x + DX[front.ordinal()], z + DZ[front.ordinal()]).isRoad();
            }
        assert waterfront > 0;
        checkShoreShapes();
        checkVariableCitiesAndShores();

        CityLayout.Terrain ocean = terrain(63);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) ocean.water()[x][z] = true;
        assert CityLayout.plan(seed, 0, 0, ocean).isEmpty();

        CityLayout.Terrain cliffs = terrain(64);
        for (int x = 5; x < 8; x++) for (int z = 0; z < 8; z++) cliffs.height()[x][z] = 110;
        CityLayout hilly = CityLayout.plan(seed, 0, 1, cliffs).orElseThrow();
        verify(hilly, cliffs);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            CityPlan.Cell cell = hilly.cell(x, z);
            if (!cell.isRoad()) continue;
            for (int i = 0; i < 4; i++) if ((cell.connections() & (1 << i)) != 0)
                assert Math.abs(cliffs.height()[x][z] - cliffs.height()[x + DX[i]][z + DZ[i]]) <= 9;
        }
        System.out.println("CityPlanCheck passed");
    }

    private static void checkVariableCitiesAndShores() {
        Set<Integer> sizes = new HashSet<>();
        int smallRoads = 0, largeRoads = 0, naturalBanks = 0, bankRoads = 0, bridges = 0;
        for (int rx = -8; rx <= 8; rx++) for (int rz = -8; rz <= 8; rz++) {
            int size = CityPlan.sizeFor(123456789L, rx, rz);
            sizes.add(size);
            assert size >= CityPlan.SIZE && size <= CityPlan.MAX_SIZE && size % 2 == 0;
            int offset = CityPlan.offsetFor(size);
            assert offset >= 0 && offset + size <= CityPlan.REGION;
            CityLayout.Terrain flat = terrain(64, size);
            CityLayout layout = CityLayout.plan(123456789L, rx, rz, flat).orElseThrow();
            verify(layout, flat);
            assert layout.size() == size;
            if (size == 8) smallRoads += layout.roadCount();
            if (size == 16) largeRoads += layout.roadCount();

            CityLayout.Terrain coast = terrain(64, size);
            for (int x = size - 2; x < size; x++) for (int z = 0; z < size; z++)
                coast.water()[x][z] = true;
            CityLayout coastal = CityLayout.plan(123456789L, rx, rz, coast).orElseThrow();
            verify(coastal, coast);
            for (int z = 0; z < size; z++)
                if (coastal.plotUse(size - 3, z) == CityLayout.PlotUse.PARK) naturalBanks++;

            CityLayout.Terrain river = terrain(64, size);
            for (int z = 0; z < size; z++) river.water()[size / 2][z] = true;
            CityLayout riverside = CityLayout.plan(123456789L, rx, rz, river).orElseThrow();
            verify(riverside, river);
            for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
                if (riverside.roadClass(x, z) == CityLayout.RoadClass.BRIDGE) bridges++;
                if ((x == size / 2 - 1 || x == size / 2 + 1)
                        && riverside.cell(x, z).isRoad()) bankRoads++;
            }
        }
        assert sizes.equals(Set.of(8, 10, 12, 14, 16)) : "No city-size variation";
        assert largeRoads > smallRoads * 2 : "Large cities did not grow";
        assert naturalBanks > 30 : "Natural shoreline was consumed by construction";
        assert bridges > 0 : "River crossings disappeared";
        assert bankRoads > bridges * 2 : "River crossings dominate bank streets";
    }

    private static CityLayout.Terrain terrain(int y, int size) {
        int[][] height = new int[size][size];
        boolean[][] water = new boolean[size][size];
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) height[x][z] = y;
        return new CityLayout.Terrain(height, water);
    }

    private static CityLayout.Terrain terrain(int y) {
        int[][] height = new int[8][8];
        boolean[][] water = new boolean[8][8];
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) height[x][z] = y;
        return new CityLayout.Terrain(height, water);
    }

    private static void checkShoreShapes() {
        int[] shore = {CityPlan.NORTH, CityPlan.EAST, CityPlan.SOUTH, CityPlan.WEST};
        int[] opposite = {CityPlan.SOUTH, CityPlan.WEST, CityPlan.NORTH, CityPlan.EAST};
        int[] edgeX = {8, 15, 8, 0}, edgeZ = {0, 8, 15, 8};
        for (int i = 0; i < 4; i++) {
            assert ShoreGeometry.shoreBand(shore[i], edgeX[i], edgeZ[i]);
            assert ShoreGeometry.rail(shore[i], edgeX[i], edgeZ[i]);
            assert !ShoreGeometry.rail(shore[i], edgeX[(i + 2) & 3], edgeZ[(i + 2) & 3]);
            if (i % 2 == 0) {
                assert ShoreGeometry.rail(shore[i], 0, edgeZ[i]);
                assert ShoreGeometry.rail(shore[i], 15, edgeZ[i]);
            } else {
                assert ShoreGeometry.rail(shore[i], edgeX[i], 0);
                assert ShoreGeometry.rail(shore[i], edgeX[i], 15);
            }
            boolean[][] visited = new boolean[16][16];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[] {edgeX[(i + 2) & 3], edgeZ[(i + 2) & 3]});
            while (!queue.isEmpty()) {
                int[] p = queue.remove();
                int x = p[0], z = p[1];
                if (x < 0 || z < 0 || x >= 16 || z >= 16 || visited[x][z]
                        || !ShoreGeometry.quay(shore[i], opposite[i], x, z)) continue;
                visited[x][z] = true;
                for (int d = 0; d < 4; d++) queue.add(new int[] {x + DX[d], z + DZ[d]});
            }
            assert visited[edgeX[i]][edgeZ[i]] : "Promenade does not reach shore";
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
                assert !ShoreGeometry.quay(shore[i], opposite[i], x, z) || visited[x][z]
                        : "Disconnected quay paving";
        }
    }

    private static int shorePlots(CityLayout layout) {
        int result = 0;
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++)
            if (layout.plotUse(x, z) == CityLayout.PlotUse.WATERFRONT) result++;
        return result;
    }

    private static int dryRoads(CityLayout layout, CityLayout.Terrain terrain) {
        int result = 0;
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++)
            if (layout.cell(x, z).isRoad() && !terrain.water()[x][z]) result++;
        return result;
    }

    private static String signature(CityLayout layout) {
        StringBuilder result = new StringBuilder();
        for (int z = 0; z < 8; z++) for (int x = 0; x < 8; x++)
            result.append((char) ('A' + layout.cell(x, z).connections()));
        return result.toString();
    }

    private static void verify(CityLayout layout, CityLayout.Terrain terrain) {
        boolean[][] visited = new boolean[layout.size()][layout.size()];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        int roads = 0, lots = 0, blockCells = 0, dryCells = 0;
        for (int x = 0; x < layout.size(); x++) for (int z = 0; z < layout.size(); z++) {
            CityPlan.Cell cell = layout.cell(x, z);
            if (!terrain.water()[x][z]) dryCells++;
            if (cell.isRoad()) {
                roads++;
                if (queue.isEmpty()) queue.add(new int[] {x, z});
                if (terrain.water()[x][z]) assert layout.roadClass(x, z) == CityLayout.RoadClass.BRIDGE;
                for (int i = 0; i < 4; i++) if ((cell.connections() & (1 << i)) != 0) {
                    int nx = x + DX[i], nz = z + DZ[i];
                    assert nx >= 0 && nz >= 0 && nx < layout.size() && nz < layout.size();
                    assert layout.cell(nx, nz).isRoad();
                    assert (layout.cell(nx, nz).connections() & OPPOSITE[i]) != 0;
                }
                assert layout.blockId(x, z) == 0;
            } else {
                if (!terrain.water()[x][z]) {
                    blockCells++;
                    assert layout.blockId(x, z) > 0;
                }
                if (cell.kind() == CityPlan.Kind.LOT) {
                    lots++;
                    int i = cell.front().ordinal();
                    assert layout.cell(x + DX[i], z + DZ[i]).isRoad();
                }
            }
        }
        int reached = 0;
        while (!queue.isEmpty()) {
            int[] point = queue.remove();
            int x = point[0], z = point[1];
            if (visited[x][z]) continue;
            visited[x][z] = true;
            reached++;
            int mask = layout.cell(x, z).connections();
            for (int i = 0; i < 4; i++) if ((mask & (1 << i)) != 0)
                queue.add(new int[] {x + DX[i], z + DZ[i]});
        }
        assert reached == roads : "Disconnected streets";
        assert roads == layout.roadCount() && roads >= 5;
        assert roads <= Math.min(22 * layout.size() * layout.size() / 64, dryCells * 2 / 5) : "Roads consumed too much of the site";
        for (int x = 0; x < layout.size() - 1; x++) for (int z = 0; z < layout.size() - 1; z++)
            assert !(layout.cell(x, z).isRoad() && layout.cell(x + 1, z).isRoad()
                    && layout.cell(x, z + 1).isRoad() && layout.cell(x + 1, z + 1).isRoad())
                    : "Solid 2x2 road patch";
        assert layout.blocks().stream().mapToInt(CityLayout.CityBlock::cells).sum() == blockCells;
        int reserved = 0;
        for (CityLayout.LargePlot plot : layout.largePlots()) {
            assert plot.widthCells() + plot.depthCells() == 3;
            int id = layout.blockId(plot.x(), plot.z());
            for (int x = plot.x(); x < plot.x() + plot.widthCells(); x++)
                for (int z = plot.z(); z < plot.z() + plot.depthCells(); z++) {
                    assert layout.largePlotAt(x, z) == plot;
                    assert layout.blockId(x, z) == id && id > 0;
                    assert layout.plotUse(x, z) == CityLayout.PlotUse.BUILDING;
                    assert layout.cell(x, z).district() == plot.district();
                    assert layout.cell(x, z).front() == plot.front();
                    int side = plot.front().ordinal();
                    assert layout.cell(x + DX[side], z + DZ[side]).isRoad();
                    assert Math.abs(terrain.height()[x][z] - terrain.height()[plot.x()][plot.z()]) <= 4;
                    reserved++;
                }
        }
        int seen = 0;
        for (int x = 0; x < layout.size(); x++) for (int z = 0; z < layout.size(); z++)
            if (layout.largePlotAt(x, z) != null) seen++;
        assert seen == reserved;
        assert layout.cell(-1, 0).kind() == CityPlan.Kind.OUTSIDE;
        assert lots > 0;
    }
}
