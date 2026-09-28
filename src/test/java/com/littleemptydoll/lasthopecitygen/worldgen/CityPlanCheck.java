package com.littleemptydoll.lasthopecitygen.worldgen;

/** Runnable without Minecraft: javac CityPlan.java CityPlanCheck.java; java -ea ...CityPlanCheck. */
public final class CityPlanCheck {
    public static void main(String[] args) {
        long seed = 123456789;
        int regionX = -3, regionZ = 2;
        while (!CityPlan.hasCity(seed, regionX, regionZ)) regionZ++;
        int baseX = regionX * CityPlan.REGION + 12;
        int baseZ = regionZ * CityPlan.REGION + 12;
        int lots = 0;
        for (int z = 0; z < CityPlan.SIZE; z++) {
            for (int x = 0; x < CityPlan.SIZE; x++) {
                CityPlan.Cell cell = CityPlan.at(seed, baseX + x, baseZ + z);
                assert cell.equals(CityPlan.at(seed, baseX + x, baseZ + z));
                if (x == 0 || x == 4) assert cell.kind() == (z == 0 || z == 4
                        ? CityPlan.Kind.INTERSECTION : CityPlan.Kind.ROAD_NS);
                else if (z == 0 || z == 4) assert cell.kind() == CityPlan.Kind.ROAD_EW;
                else { assert cell.kind() == CityPlan.Kind.LOT; lots++; }
            }
        }
        assert lots == 36 : lots;
        assert CityPlan.at(seed, baseX - 1, baseZ).kind() == CityPlan.Kind.OUTSIDE;
        assert CityPlan.at(seed, baseX + 8, baseZ).kind() == CityPlan.Kind.OUTSIDE;
        System.out.println("CityPlanCheck passed");
    }
}
