package com.littleemptydoll.lasthopecitygen.structure.builder;

import com.littleemptydoll.lasthopecitygen.structure.placement.StructurePlacement;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.util.RandomSource;

public interface StructureBuilder {
    boolean build(WorldGenLevel level, StructurePlacement placement, BoundingBox bounds, RandomSource random);
}
