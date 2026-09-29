package com.littleemptydoll.lasthopecitygen.worldgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;

/** One immutable, city-wide plan, built before any of its chunks are modified. */
public final class CityLayout {
    public enum RoadClass { PRIMARY, SECONDARY, WATERFRONT, BRIDGE }
    public enum PlotUse { BUILDING, PARK, EMPTY, BEACH, WATERFRONT, INFRASTRUCTURE }
    public record CityBlock(int id, int cells, boolean touchesBoundary, boolean touchesWater) { }
    /** A pair of adjacent 16-block cells with a shared street frontage. */
    public record LargePlot(int x, int z, int widthCells, int depthCells, CityPlan.Front front,
                            CityPlan.District district, long seed) { }

    public record Terrain(int[][] height, boolean[][] water, int seaLevel) {
        public Terrain {
            if (height.length != CityPlan.SIZE || water.length != CityPlan.SIZE)
                throw new IllegalArgumentException("Expected an 8x8 terrain snapshot");
            for (int x = 0; x < CityPlan.SIZE; x++)
                if (height[x].length != CityPlan.SIZE || water[x].length != CityPlan.SIZE)
                    throw new IllegalArgumentException("Expected an 8x8 terrain snapshot");
        }

        boolean dry(int x, int z) { return inside(x, z) && !water[x][z]; }
    }

    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};
    private static final int[] BIT = {CityPlan.NORTH, CityPlan.EAST, CityPlan.SOUTH, CityPlan.WEST};
    private static final int MAX_ROAD_CELLS = 22;
    private static final CityPlan.Cell OUTSIDE = new CityPlan.Cell(CityPlan.Kind.OUTSIDE,
            CityPlan.District.RESIDENTIAL, CityPlan.Front.NORTH, 0, 0);

    private final CityPlan.Cell[][] cells = new CityPlan.Cell[CityPlan.SIZE][CityPlan.SIZE];
    private final RoadClass[][] roadClasses = new RoadClass[CityPlan.SIZE][CityPlan.SIZE];
    private final PlotUse[][] plotUses = new PlotUse[CityPlan.SIZE][CityPlan.SIZE];
    private final boolean[][] water = new boolean[CityPlan.SIZE][CityPlan.SIZE];
    private final int[][] blockIds = new int[CityPlan.SIZE][CityPlan.SIZE];
    private final LargePlot[][] plotsByCell = new LargePlot[CityPlan.SIZE][CityPlan.SIZE];
    private final List<CityBlock> blocks;
    private final List<LargePlot> largePlots;
    private final int roadCount;

    private CityLayout(Builder builder) {
        int count = 0;
        for (int x = 0; x < CityPlan.SIZE; x++) for (int z = 0; z < CityPlan.SIZE; z++) {
            int mask = builder.links[x][z];
            if (builder.road[x][z]) count++;
            roadClasses[x][z] = builder.classes[x][z];
            water[x][z] = builder.terrain.water[x][z];
            if (builder.road[x][z]) {
                plotUses[x][z] = PlotUse.INFRASTRUCTURE;
                int degree = Integer.bitCount(mask);
                CityPlan.Kind kind = switch (degree) {
                    case 4 -> CityPlan.Kind.CROSS;
                    case 3 -> CityPlan.Kind.T_JUNCTION;
                    case 2 -> mask == (CityPlan.NORTH | CityPlan.SOUTH)
                            || mask == (CityPlan.EAST | CityPlan.WEST)
                            ? CityPlan.Kind.STRAIGHT : CityPlan.Kind.CORNER;
                    default -> CityPlan.Kind.DEAD_END;
                };
                cells[x][z] = builder.cell(x, z, kind, CityPlan.Front.NORTH, mask);
            } else {
                int adjacent = builder.adjacentRoads(x, z);
                PlotUse use = builder.plotUse(x, z, adjacent);
                plotUses[x][z] = use;
                CityPlan.Front front = adjacent == 0 ? CityPlan.Front.NORTH
                        : builder.frontage(adjacent, x, z);
                cells[x][z] = builder.cell(x, z,
                        use == PlotUse.BUILDING ? CityPlan.Kind.LOT : CityPlan.Kind.PARK, front, 0);
            }
        }
        roadCount = count;
        blocks = List.copyOf(extractBlocks(builder));
        largePlots = List.copyOf(selectLargePlots(builder));
    }

    public static Optional<CityLayout> plan(long seed, int regionX, int regionZ, Terrain terrain) {
        int dry = 0, central = 0;
        for (int x = 0; x < CityPlan.SIZE; x++) for (int z = 0; z < CityPlan.SIZE; z++) {
            if (terrain.dry(x, z)) {
                dry++;
                if (x >= 2 && x <= 5 && z >= 2 && z <= 5) central++;
            }
        }
        // A coast or narrow river is useful, a small island in open sea is not.
        if (dry < 34 || central < 9) return Optional.empty();
        Builder builder = new Builder(seed, regionX, regionZ, terrain, dry);
        builder.grow();
        builder.rationalize();
        if (builder.count < 5) return Optional.empty();
        builder.markWaterfront();
        return Optional.of(new CityLayout(builder));
    }

    public CityPlan.Cell cell(int x, int z) { return inside(x, z) ? cells[x][z] : OUTSIDE; }
    public PlotUse plotUse(int x, int z) { return inside(x, z) ? plotUses[x][z] : PlotUse.EMPTY; }
    public RoadClass roadClass(int x, int z) { return inside(x, z) ? roadClasses[x][z] : null; }
    public boolean isWater(int x, int z) { return inside(x, z) && water[x][z]; }
    public int blockId(int x, int z) { return inside(x, z) ? blockIds[x][z] : -1; }
    public List<CityBlock> blocks() { return blocks; }
    public List<LargePlot> largePlots() { return largePlots; }
    public LargePlot largePlotAt(int x, int z) { return inside(x, z) ? plotsByCell[x][z] : null; }
    public int roadCount() { return roadCount; }

    private List<CityBlock> extractBlocks(Builder builder) {
        List<CityBlock> result = new ArrayList<>();
        for (int x = 0; x < CityPlan.SIZE; x++) for (int z = 0; z < CityPlan.SIZE; z++) {
            if (builder.road[x][z] || builder.terrain.water[x][z] || blockIds[x][z] != 0) continue;
            int id = result.size() + 1, size = 0;
            boolean boundary = false, water = false;
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            queue.add(new int[] {x, z});
            blockIds[x][z] = id;
            while (!queue.isEmpty()) {
                int[] point = queue.remove();
                int px = point[0], pz = point[1];
                size++;
                boundary |= px == 0 || pz == 0 || px == CityPlan.SIZE - 1 || pz == CityPlan.SIZE - 1;
                for (int i = 0; i < 4; i++) {
                    int nx = px + DX[i], nz = pz + DZ[i];
                    if (!inside(nx, nz)) continue;
                    if (builder.terrain.water[nx][nz]) { water = true; continue; }
                    if (!builder.road[nx][nz] && blockIds[nx][nz] == 0) {
                        blockIds[nx][nz] = id;
                        queue.add(new int[] {nx, nz});
                    }
                }
            }
            result.add(new CityBlock(id, size, boundary, water));
        }
        return result;
    }

    private record Pair(int x, int z, int dx, int dz, CityPlan.Front front, long priority) { }

    private List<LargePlot> selectLargePlots(Builder builder) {
        List<Pair> candidates = new ArrayList<>();
        for (int x = 0; x < CityPlan.SIZE; x++) for (int z = 0; z < CityPlan.SIZE; z++) {
            for (int axis = 0; axis < 2; axis++) {
                int dx = axis == 0 ? 1 : 0, dz = axis == 0 ? 0 : 1;
                int nx = x + dx, nz = z + dz;
                if (!inside(nx, nz) || plotUses[x][z] != PlotUse.BUILDING
                        || plotUses[nx][nz] != PlotUse.BUILDING
                        || blockIds[x][z] == 0 || blockIds[x][z] != blockIds[nx][nz]
                        || cells[x][z].district() != cells[nx][nz].district()
                        || Math.abs(builder.terrain.height[x][z] - builder.terrain.height[nx][nz]) > 4) continue;
                int[] sides = axis == 0 ? new int[] {0, 2} : new int[] {1, 3};
                for (int side : sides) {
                    int ax = x + DX[side], az = z + DZ[side];
                    int bx = nx + DX[side], bz = nz + DZ[side];
                    if (!inside(ax, az) || !inside(bx, bz)
                            || !cells[ax][az].isRoad() || !cells[bx][bz].isRoad()) continue;
                    long priority = CityPlan.mix(cells[x][z].lotSeed() ^ cells[nx][nz].lotSeed()
                            ^ (long) side * 0x9E3779B97F4A7C15L);
                    candidates.add(new Pair(x, z, dx, dz, CityPlan.Front.values()[side], priority));
                }
            }
        }
        candidates.sort((a, b) -> Long.compareUnsigned(a.priority(), b.priority()));
        List<LargePlot> chosen = new ArrayList<>();
        for (Pair pair : candidates) {
            if (chosen.size() == 2) break;
            int x = pair.x(), z = pair.z(), nx = x + pair.dx(), nz = z + pair.dz();
            if (plotsByCell[x][z] != null || plotsByCell[nx][nz] != null) continue;
            LargePlot plot = new LargePlot(x, z, 1 + pair.dx(), 1 + pair.dz(), pair.front(),
                    cells[x][z].district(), pair.priority());
            plotsByCell[x][z] = plotsByCell[nx][nz] = plot;
            cells[x][z] = new CityPlan.Cell(CityPlan.Kind.LOT, plot.district(), plot.front(), 0,
                    cells[x][z].lotSeed());
            cells[nx][nz] = new CityPlan.Cell(CityPlan.Kind.LOT, plot.district(), plot.front(), 0,
                    cells[nx][nz].lotSeed());
            chosen.add(plot);
        }
        return chosen;
    }

    private static boolean inside(int x, int z) {
        return x >= 0 && z >= 0 && x < CityPlan.SIZE && z < CityPlan.SIZE;
    }

    private record Proposal(int x, int z, int direction, int budget, int depth,
                            RoadClass roadClass, int priority, long tie) { }

    private static final class Builder {
        private final long seed;
        private final int regionX, regionZ;
        private final Terrain terrain;
        private final int[][] links = new int[CityPlan.SIZE][CityPlan.SIZE];
        private final boolean[][] road = new boolean[CityPlan.SIZE][CityPlan.SIZE];
        private final RoadClass[][] classes = new RoadClass[CityPlan.SIZE][CityPlan.SIZE];
        private final PriorityQueue<Proposal> queue = new PriorityQueue<>(Comparator
                .comparingInt(Proposal::priority).reversed().thenComparingLong(Proposal::tie));
        private final int majorAxis;
        private final int roadBudget;
        private int count;

        Builder(long seed, int regionX, int regionZ, Terrain terrain, int dryCells) {
            this.seed = CityPlan.mix(seed ^ ((long) regionX * 0xD6E8FEB86659FD93L)
                    ^ ((long) regionZ * 0xA5A3564E27F8862DL));
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.terrain = terrain;
            // Keep room for blocks even in a city with a river or coastline.
            this.roadBudget = Math.min(MAX_ROAD_CELLS, dryCells * 2 / 5)
                    - (int) (this.seed & 3);
            int wetX = 0, wetZ = 0;
            for (int x = 0; x < CityPlan.SIZE; x++) for (int z = 0; z < CityPlan.SIZE; z++)
                if (terrain.water[x][z]) { wetX += x * 2 - 7; wetZ += z * 2 - 7; }
            // The dominant street tends to follow a coast rather than run into it.
            majorAxis = Math.abs(wetX) > Math.abs(wetZ) ? 0
                    : Math.abs(wetZ) > Math.abs(wetX) ? 1 : (int) (this.seed & 1);
        }

        void grow() {
            int startX = 3, startZ = 3, best = Integer.MAX_VALUE;
            for (int x = 1; x < 7; x++) for (int z = 1; z < 7; z++) {
                if (!terrain.dry(x, z)) continue;
                int distance = Math.abs(x - 3) + Math.abs(z - 3);
                if (distance < best || distance == best && (hash(x, z, 0) & 1) == 0) {
                    startX = x; startZ = z; best = distance;
                }
            }
            addRoad(startX, startZ, RoadClass.PRIMARY);
            int forward = majorAxis == 0 ? 0 : 1;
            enqueue(startX, startZ, forward, 7, 0, RoadClass.PRIMARY);
            enqueue(startX, startZ, (forward + 2) & 3, 7, 0, RoadClass.PRIMARY);
            enqueue(startX, startZ, (forward + 1) & 3, 4, 1, RoadClass.SECONDARY);
            enqueue(startX, startZ, (forward + 3) & 3, 4, 1, RoadClass.SECONDARY);
            int attempts = 0;
            while (!queue.isEmpty() && attempts++ < 180 && count < roadBudget) {
                Proposal proposal = queue.remove();
                if (proposal.budget == 0 || !road[proposal.x][proposal.z]) continue;
                advance(proposal);
            }
        }

        private void advance(Proposal proposal) {
            int[] directions = {proposal.direction, (proposal.direction + 1) & 3,
                    (proposal.direction + 3) & 3};
            int bestDirection = -1;
            double bestScore = -Double.MAX_VALUE;
            for (int direction : directions) {
                int nx = proposal.x + DX[direction], nz = proposal.z + DZ[direction];
                if (!inside(nx, nz) || (links[proposal.x][proposal.z] & BIT[direction]) != 0) continue;
                int waterRun = waterRun(nx, nz, direction);
                if (waterRun < 0) continue;
                if (waterRun > 0 && !clearBridge(nx, nz, direction, waterRun)) continue;
                if (!hasSpace(proposal.x, proposal.z, direction, waterRun)) continue;
                int landingX = nx + DX[direction] * waterRun;
                int landingZ = nz + DZ[direction] * waterRun;
                int elevation = Math.abs(terrain.height[proposal.x][proposal.z]
                        - terrain.height[landingX][landingZ]);
                if (elevation > (proposal.roadClass == RoadClass.PRIMARY ? 9 : 6)) continue;
                if (waterRun == 0 && !road[nx][nz] && crowded(nx, nz, proposal.x, proposal.z)) continue;
                double score = fieldScore(proposal, direction, landingX, landingZ, elevation, waterRun);
                if (score > bestScore) { bestScore = score; bestDirection = direction; }
            }
            if (bestDirection < 0) return;
            int direction = bestDirection, x = proposal.x, z = proposal.z;
            int nx = x + DX[direction], nz = z + DZ[direction];
            int waterRun = waterRun(nx, nz, direction);
            boolean existing = false;
            for (int step = 0; step <= waterRun; step++) {
                nx = x + DX[direction]; nz = z + DZ[direction];
                boolean wasRoad = road[nx][nz];
                if (!wasRoad) addRoad(nx, nz, step < waterRun ? RoadClass.BRIDGE : proposal.roadClass);
                existing |= wasRoad;
                connect(x, z, nx, nz, direction);
                if (!terrain.water[nx][nz]) snapNeighbors(nx, nz);
                x = nx; z = nz;
            }
            // Reaching an existing segment creates an intersection, not another branch.
            if (existing) return;
            if (proposal.budget > 1) enqueue(x, z, direction, proposal.budget - 1,
                    proposal.depth, proposal.roadClass);
            if (proposal.depth < 2 && proposal.budget > 2 && count < roadBudget - 3
                    && (hash(x, z, direction) & 3) <= (proposal.roadClass == RoadClass.PRIMARY ? 2 : 1)) {
                int side = (hash(x, z, 7) & 1) == 0 ? 1 : 3;
                enqueue(x, z, (direction + side) & 3, 2 + (int) (hash(x, z, 4) & 3),
                        proposal.depth + 1, RoadClass.SECONDARY);
            }
        }

        private int waterRun(int x, int z, int direction) {
            if (!inside(x, z)) return -1;
            if (!terrain.water[x][z]) return 0;
            for (int length = 1; length <= 2; length++) {
                int nx = x + DX[direction] * length, nz = z + DZ[direction] * length;
                if (!inside(nx, nz)) return -1;
                if (!terrain.water[nx][nz]) return length;
            }
            return -1;
        }

        private boolean clearBridge(int x, int z, int direction, int length) {
            for (int step = 0; step < length; step++) {
                int bx = x + DX[direction] * step, bz = z + DZ[direction] * step;
                if (road[bx][bz]) return false;
                for (int side = 0; side < 4; side++) {
                    if (side == direction || side == ((direction + 2) & 3)) continue;
                    int nx = bx + DX[side], nz = bz + DZ[side];
                    if (inside(nx, nz) && road[nx][nz]) return false;
                }
            }
            return true;
        }

        private boolean hasSpace(int x, int z, int direction, int waterRun) {
            int added = 0;
            for (int step = 0; step <= waterRun; step++) {
                int nx = x + DX[direction] * (step + 1);
                int nz = z + DZ[direction] * (step + 1);
                if (road[nx][nz]) continue;
                if (++added + count > roadBudget) return false;
                // A paved 2x2 square is the start of the solid 2x4 patches.
                // Check all cells proposed by this bridge, not only the landing.
                for (int ax = nx - 1; ax <= nx; ax++) for (int az = nz - 1; az <= nz; az++) {
                    if (!inside(ax, az) || !inside(ax + 1, az + 1)) continue;
                    boolean full = true;
                    for (int px = ax; px <= ax + 1; px++) for (int pz = az; pz <= az + 1; pz++) {
                        boolean onProposal = false;
                        for (int k = 0; k <= step; k++)
                            if (px == x + DX[direction] * (k + 1)
                                    && pz == z + DZ[direction] * (k + 1)) onProposal = true;
                        full &= road[px][pz] || onProposal;
                    }
                    if (full) return false;
                }
            }
            return true;
        }

        private double fieldScore(Proposal proposal, int direction, int x, int z,
                                  int elevation, int waterRun) {
            double grid = direction == proposal.direction ? 2.0 : 0.3;
            if (proposal.roadClass == RoadClass.PRIMARY && direction % 2 == majorAxis) grid += 1.1;
            int wetX = 0, wetZ = 0;
            for (int i = 0; i < 4; i++) {
                int nx = x + DX[i], nz = z + DZ[i];
                if (inside(nx, nz) && terrain.water[nx][nz]) {
                    wetX += DX[i]; wetZ += DZ[i];
                }
            }
            double coast = wetX == 0 && wetZ == 0 ? 0
                    : direction % 2 == (Math.abs(wetX) > Math.abs(wetZ) ? 0 : 1) ? 1.5 : -0.7;
            double anchor = (Math.abs(x - 3.5) + Math.abs(z - 3.5)) * 0.07;
            double noise = ((hash(x, z, direction) >>> 8) & 255) / 255.0 * 0.7 - 0.35;
            return grid + coast + anchor + noise - elevation * 0.32 - waterRun * 0.8;
        }

        private boolean crowded(int x, int z, int fromX, int fromZ) {
            int nearby = 0;
            for (int i = 0; i < 4; i++) {
                int nx = x + DX[i], nz = z + DZ[i];
                if (inside(nx, nz) && road[nx][nz] && (nx != fromX || nz != fromZ)) nearby++;
            }
            return nearby >= 2;
        }

        private void enqueue(int x, int z, int direction, int budget, int depth, RoadClass roadClass) {
            long tie = hash(x, z, direction);
            queue.add(new Proposal(x, z, direction, budget, depth, roadClass,
                    (roadClass == RoadClass.PRIMARY ? 100 : 30) + budget, tie));
        }

        private void addRoad(int x, int z, RoadClass roadClass) {
            road[x][z] = true;
            classes[x][z] = roadClass;
            count++;
        }

        private void connect(int x, int z, int nx, int nz, int direction) {
            links[x][z] |= BIT[direction];
            links[nx][nz] |= BIT[(direction + 2) & 3];
        }

        private void snapNeighbors(int x, int z) {
            for (int direction = 0; direction < 4; direction++) {
                int nx = x + DX[direction], nz = z + DZ[direction];
                if (inside(nx, nz) && road[nx][nz]) connect(x, z, nx, nz, direction);
            }
        }

        void rationalize() {
            // Prune all terminal segments without dry frontage, including chains
            // exposed by removing another dead end in the previous pass.
            boolean changed;
            do {
                changed = false;
                for (int x = 0; x < CityPlan.SIZE; x++) for (int z = 0; z < CityPlan.SIZE; z++) {
                    if (!road[x][z] || Integer.bitCount(links[x][z]) != 1) continue;
                    int nearbyDry = 0;
                    for (int i = 0; i < 4; i++)
                        if (terrain.dry(x + DX[i], z + DZ[i]) && !road[x + DX[i]][z + DZ[i]]) nearbyDry++;
                    if (nearbyDry != 0) continue;
                    int direction = Integer.numberOfTrailingZeros(links[x][z]);
                    int nx = x + DX[direction], nz = z + DZ[direction];
                    links[nx][nz] &= ~BIT[(direction + 2) & 3];
                    links[x][z] = 0; road[x][z] = false; classes[x][z] = null; count--;
                    changed = true;
                }
            } while (changed);
        }

        void markWaterfront() {
            for (int x = 0; x < CityPlan.SIZE; x++) for (int z = 0; z < CityPlan.SIZE; z++) {
                if (!road[x][z] || classes[x][z] == RoadClass.BRIDGE) continue;
                for (int i = 0; i < 4; i++) {
                    int nx = x + DX[i], nz = z + DZ[i];
                    if (inside(nx, nz) && terrain.water[nx][nz]) {
                        classes[x][z] = RoadClass.WATERFRONT;
                        break;
                    }
                }
            }
        }

        private int adjacentRoads(int x, int z) {
            int mask = 0;
            for (int i = 0; i < 4; i++) {
                int nx = x + DX[i], nz = z + DZ[i];
                if (inside(nx, nz) && road[nx][nz]) mask |= BIT[i];
            }
            return mask;
        }

        private PlotUse plotUse(int x, int z, int adjacent) {
            if (terrain.water[x][z]) return PlotUse.EMPTY;
            boolean shore = false;
            int relief = 0;
            for (int i = 0; i < 4; i++) {
                int nx = x + DX[i], nz = z + DZ[i];
                if (!inside(nx, nz)) continue;
                shore |= terrain.water[nx][nz];
                if (!terrain.water[nx][nz])
                    relief = Math.max(relief, Math.abs(terrain.height[x][z] - terrain.height[nx][nz]));
            }
            if (shore) return terrain.height[x][z] <= terrain.seaLevel + 3
                    ? PlotUse.BEACH : adjacent != 0 && relief <= 5 ? PlotUse.WATERFRONT : PlotUse.PARK;
            if (adjacent == 0) return (hash(x, z, 5) & 3) == 0 ? PlotUse.EMPTY : PlotUse.PARK;
            return relief > 8 ? PlotUse.PARK : PlotUse.BUILDING;
        }

        private CityPlan.Front frontage(int roads, int x, int z) {
            int preferred = (int) (hash(x, z, 9) & 3);
            for (int i = 0; i < 4; i++) {
                int direction = (preferred + i) & 3;
                if ((roads & BIT[direction]) != 0) return CityPlan.Front.values()[direction];
            }
            throw new IllegalStateException("Missing frontage");
        }

        private CityPlan.Cell cell(int x, int z, CityPlan.Kind kind, CityPlan.Front front, int mask) {
            int chunkX = regionX * CityPlan.REGION + (CityPlan.REGION - CityPlan.SIZE) / 2 + x;
            int chunkZ = regionZ * CityPlan.REGION + (CityPlan.REGION - CityPlan.SIZE) / 2 + z;
            long lotSeed = CityPlan.mix(seed ^ ((long) chunkX * 0x9E3779B97F4A7C15L)
                    ^ ((long) chunkZ * 0xC2B2AE3D27D4EB4FL));
            CityPlan.District district = x >= 5 && z >= 5 ? CityPlan.District.INDUSTRIAL
                    : x >= 5 && z <= 3 ? CityPlan.District.CIVIC : CityPlan.District.RESIDENTIAL;
            return new CityPlan.Cell(kind, district, front, mask, lotSeed);
        }

        private long hash(int x, int z, int salt) {
            return CityPlan.mix(seed ^ ((long) x * 0x9E3779B97F4A7C15L)
                    ^ ((long) z * 0xC2B2AE3D27D4EB4FL) ^ ((long) salt * 0xD6E8FEB86659FD93L));
        }
    }
}
