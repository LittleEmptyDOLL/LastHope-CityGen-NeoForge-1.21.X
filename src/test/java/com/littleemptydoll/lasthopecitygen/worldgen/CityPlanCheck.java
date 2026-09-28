package com.littleemptydoll.lasthopecitygen.worldgen;

import java.util.ArrayDeque;

/** Runs without Minecraft: java -ea ...CityPlanCheck. */
public final class CityPlanCheck {
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};
    private static final int[] OPPOSITE = {CityPlan.SOUTH, CityPlan.WEST, CityPlan.NORTH, CityPlan.EAST};

    public static void main(String[] args) {
        long seed = 123456789;
        for (int rx = -3; rx <= 3; rx++) for (int rz = -3; rz <= 3; rz++) {
            if (CityPlan.hasCity(seed, rx, rz)) checkCity(seed, rx, rz);
        }
        CityPlan.CityCenter first = CityPlan.nearestCity(seed, 0, 0).orElseThrow();
        CityPlan.CityCenter next = CityPlan.nearestCity(seed, 0, 0,
                candidate -> !candidate.equals(first)).orElseThrow();
        assert !first.equals(next);
        assert CityPlan.nearestCity(seed, 0, 0, candidate -> false).isEmpty();
        System.out.println("CityPlanCheck passed");
    }

    private static void checkCity(long seed, int rx, int rz) {
        int baseX = rx * CityPlan.REGION + 12;
        int baseZ = rz * CityPlan.REGION + 12;
        boolean[][] visited = new boolean[CityPlan.SIZE][CityPlan.SIZE];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        int roads = 0, corners = 0, junctions = 0, crosses = 0, lots = 0;
        for (int z = 0; z < CityPlan.SIZE; z++) for (int x = 0; x < CityPlan.SIZE; x++) {
            CityPlan.Cell cell = CityPlan.at(seed, baseX + x, baseZ + z);
            assert cell.equals(CityPlan.at(seed, baseX + x, baseZ + z));
            if (cell.isRoad()) {
                roads++;
                if (cell.kind() == CityPlan.Kind.CORNER) corners++;
                if (cell.kind() == CityPlan.Kind.T_JUNCTION) junctions++;
                if (cell.kind() == CityPlan.Kind.CROSS) crosses++;
                if (queue.isEmpty()) queue.add(new int[] {x, z});
                for (int i = 0; i < 4; i++) {
                    int nx = x + DX[i], nz = z + DZ[i];
                    boolean connected = (cell.connections() & (1 << i)) != 0;
                    if (connected) {
                        assert nx >= 0 && nz >= 0 && nx < CityPlan.SIZE && nz < CityPlan.SIZE;
                        CityPlan.Cell neighbor = CityPlan.at(seed, baseX + nx, baseZ + nz);
                        assert neighbor.isRoad() && (neighbor.connections() & OPPOSITE[i]) != 0;
                    }
                }
            } else if (cell.kind() == CityPlan.Kind.LOT) {
                lots++;
                int i = cell.front().ordinal();
                assert CityPlan.at(seed, baseX + x + DX[i], baseZ + z + DZ[i]).isRoad();
            }
        }
        int reached = 0;
        while (!queue.isEmpty()) {
            int[] point = queue.remove();
            int x = point[0], z = point[1];
            if (visited[x][z]) continue;
            visited[x][z] = true;
            reached++;
            int mask = CityPlan.at(seed, baseX + x, baseZ + z).connections();
            for (int i = 0; i < 4; i++) if ((mask & (1 << i)) != 0)
                queue.add(new int[] {x + DX[i], z + DZ[i]});
        }
        assert roads == reached : "Disconnected streets";
        assert corners >= 4 && junctions >= 2 && crosses >= 1 && lots > 0;
        assert CityPlan.at(seed, baseX - 1, baseZ).kind() == CityPlan.Kind.OUTSIDE;
        assert CityPlan.at(seed, baseX + 8, baseZ).kind() == CityPlan.Kind.OUTSIDE;
        CityPlan.CityCenter nearest = CityPlan.nearestCity(seed, baseX * 16 + 64, baseZ * 16 + 64).orElseThrow();
        assert nearest.blockX() == baseX * 16 + 64 && nearest.blockZ() == baseZ * 16 + 64;
    }
}
